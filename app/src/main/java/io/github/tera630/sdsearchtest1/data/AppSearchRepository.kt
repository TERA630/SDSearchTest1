package io.github.tera630.sdsearchtest1.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.appsearch.app.AppSearchSession
import androidx.appsearch.app.Features
import androidx.appsearch.app.PropertyPath
import androidx.appsearch.app.PutDocumentsRequest
import androidx.appsearch.app.SearchSpec
import androidx.appsearch.app.SetSchemaRequest
import androidx.appsearch.localstorage.LocalStorage
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import androidx.core.net.toUri
import androidx.appsearch.app.GetByDocumentIdRequest
import kotlin.text.split


class AppSearchRepository(private val context: Context) {

    private val linkTargetInsideParens = Regex("""(?<=]\()(.+?)(?=\))""")
    // ］と（の連続があった直後の部位でかつ、)が直後にある　文字列に最短マッチ＝　[リンクテキスト（注釈1）](リンク先(注釈2))
    private var session: AppSearchSession? = null

    suspend fun ensureSession(): AppSearchSession =
        session ?: withContext(Dispatchers.IO) {
            // 検索処理セッションを作成する。

            val searchContext = LocalStorage.SearchContext.Builder(context,"notes-db")
                .build()

            val resolvedSession = LocalStorage.createSearchSessionAsync(searchContext).get() // with Suspend
            // 検索対象のデータ型定義(NoteDocで定義したクラス)をApp　searchの検索処理セッションに登録する。

            val req = SetSchemaRequest.Builder()
                .addDocumentClasses(NoteDoc::class.java)
                .setForceOverride(true)
                .build()
            resolvedSession.setSchemaAsync(req).get() // with Suspend

            session = resolvedSession
            resolvedSession
        } // 　NoteDocを登録した検索処理セッションを初回は作成し、class propertyと、呼び出し元に返す。

    suspend fun indexAllFromTree(treeUri: Uri,
             onProgress:(processed: Int, total: Int) -> Unit ={ _, _->}
    ): Int = withContext(Dispatchers.IO) {
        val s = ensureSession() // notes-db の SearchSession（Schema済）
        // 呼び出し元(ここではSearchScreenでユーザーが選択)で得たTreeUriからフォルダのルートを得る(なければ早期リターン)
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext 0
    //  まず 下位ディレクトリも含め.md を全部集めて総数を出す（1パス）

        fun collect(dir: DocumentFile, out: MutableList<DocumentFile>) {
            dir.listFiles().forEach { f ->
                if (f.isDirectory) collect(f, out)
                else if (f.isFile && f.name?.endsWith(".md", ignoreCase = true) == true) out += f
            }
        } // 下位フォルダを探すための関数
        //
        val mdFiles = mutableListOf<DocumentFile>().also{ collect(root, it) }
        val total = mdFiles.size
        Log.d("list files","root.listFiles().size=${total}")
        onProgress(0,total)

        // 2Pass Making Index
        var processed = 0
        val notes = mutableListOf<NoteDoc>()   // ← NoteDoc を貯めるリスト

        for (f in mdFiles) {
            context.contentResolver.openInputStream(f.uri)?.use { ins ->
                // URI上のファイルからNoteDocの形式でDocumentFile構造を作成
                val rawText = ins.bufferedReader(Charsets.UTF_8).readText()
                val title = f.name?.removeSuffix(".md") ?: "untitled"
                val id = stableId(f.uri.toString())
                val updatedAt = (f.lastModified()).takeIf { it > 0 } ?: System.currentTimeMillis()
                val tags = parseTagsFromText(rawText)
                val parsedText = parseInternalLinks(rawText)

                notes += NoteDoc(
                    id = id,
                    path = f.uri.toString(),
                    title = nfkc(title),
                    content = parsedText,
                    tags = tags,
                    updatedAt = updatedAt
                )
            }
            processed++
            onProgress(processed,total)
        }

        // 空なら早期リターン（走査ミスの検知に役立つ）
        if (notes.isEmpty()) return@withContext 0
        Log.d("AppSearch","notes.size=${notes.size}")

        // バッチ投入（100件ずつ）
        notes.chunked(100).forEach { chunk ->
            val req = PutDocumentsRequest.Builder()
                .addDocuments(chunk)       // ← 型付きドキュメント
                .build()

            Log.d("AppSearch","put chunk.size=${chunk.size}")
            s.putAsync(req).get()

        }
        notes.size
    }
    suspend fun clearAll(): Void? = withContext(Dispatchers.IO) {
        val session = ensureSession()
        val searchSpec = SearchSpec.Builder()
            .addFilterNamespaces("notes") // "notes" 名前空間を指定
            .build()
        // 空のクエリと名前空間フィルタで、該当するすべてのドキュメントを削除
        session.removeAsync("", searchSpec).get()
    }
    suspend fun getMarkdownByPath(path: String): String = withContext(Dispatchers.IO) {
        val uri = path.toUri()
        context.contentResolver.openInputStream(uri)?.use { ins ->
            BufferedReader(InputStreamReader(ins, Charsets.UTF_8)).readText()
        } ?: ""
    }
    @SuppressLint("RequiresFeature")
    suspend fun search(query: String, limit: Int = 100): List<SearchHit> = withContext(Dispatchers.IO) {
        val s = ensureSession()
        val tokens = nfkc(query)
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        //　検索セッションの条件
        val specBuilder = SearchSpec.Builder()
            .addFilterNamespaces("notes")
            .setTermMatch(SearchSpec.TERM_MATCH_PREFIX) // 前方一致
            .setSnippetCount(1) // 各ドキュメント1スニペット
            .setMaxSnippetSize(120)
            .setSnippetCountPerProperty(1)
            .setRankingStrategy(SearchSpec.RANKING_STRATEGY_RELEVANCE_SCORE)
            .setResultCountPerPage(limit)

        // property weight のサポートがあればtagsを強く。
        val features: Features = s.features
        val isWeightSupported = features.isFeatureSupported(Features.SEARCH_SPEC_PROPERTY_WEIGHTS)
        Log.d("AppSearch", "weightSupported={$isWeightSupported}")

        if (isWeightSupported) {
            specBuilder.setPropertyWeightPaths(
                "NoteDoc",
                mapOf(
                    PropertyPath("tags") to 5.0,
                    PropertyPath("title") to 2.0,
                    PropertyPath("content") to 1.0
                )
            )
        }

        val spec = specBuilder.build()
        val results = s.search(tokens.joinToString(" "), spec)
        val page = results.nextPageAsync.get()

        // 検索結果とtagScoreを保持する中間データクラス
        data class Row(val hit: SearchHit, val tagScore: Int)

        // AppSearch の結果を SearchHit と tagScore を含む Row に変換
        val rows = page.map { r ->
            val doc = r.genericDocument
            val path = doc.getPropertyString("path")!!
            val title = doc.getPropertyString("title") ?: "untitled"
            val content = doc.getPropertyString("content") ?: ""
            val tags = doc.getPropertyStringArray("tags")?.toList().orEmpty()
            val snippet = content.lineSequence().firstOrNull { line ->
                tokens.all { token -> line.contains(token, ignoreCase = true) }
            } ?: content.take(120)

            // tagScoreをtokenの中にtagと一致する数から計算
            val tagScore = if (tokens.isEmpty()) 0 else {
                tokens.count { t -> tags.any { tag -> tag.startsWith(t, ignoreCase = true) } }
            }
            Row(
                hit = SearchHit(id = doc.id, path = path, title = title, snippet = snippet),
                tagScore = tagScore
            )
        }

        // isWeightSupportedがfalseの場合のみ、tagScoreでソート
        val sortedRows = if (!isWeightSupported) {
            rows.sortedWith(
                compareByDescending<Row> { it.tagScore }
                    .thenBy { it.hit.title } // スコアが同じ場合はタイトルで安定ソート
            )
        } else {
            rows // AppSearchの順序を維持
        }

        // 最終的に List<SearchHit> を返す
        sortedRows.map { it.hit }
    }
    private fun stableId(path: String): String =
        UUID.nameUUIDFromBytes(path.toByteArray()).toString()
    
    suspend fun getNoteById(id: String): NoteDoc? = withContext(Dispatchers.IO) {
        val s = ensureSession()
        val req = GetByDocumentIdRequest.Builder("notes")
            .addIds(id)
            .build()
        val res = s.getByDocumentIdAsync(req).get() // Suspend
        
    // GenericDocument → NoteDoc に変換
        val gd = res.successes[id]
        gd?.toDocumentClass(NoteDoc::class.java)
    }

    fun parseInternalLinks(raw: String): String {
        return linkTargetInsideParens.replace(raw){ m ->
            val target = m.value.trim()
            val keepAsis = target.contains("://") ||
                    target.startsWith("mailto:",ignoreCase = true) ||
                    target.startsWith("#") ||
                    target.startsWith("doc:",ignoreCase = true) ||
                    target.startsWith("docid:", ignoreCase = true)
            if (keepAsis) { target }
            else {
                Log.i("AppSearchRepository","target=$target was parsed as internal links")
                "doc:" + Uri.encode(target)}
        }

    }
}
data class SearchHit(
    val id: String,
    val path: String,
    val title: String,
    val snippet: String
)
