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
class AppSearchRepository(private val context: Context) : NoteIndex {
    private val linkTargetInsideParens = Regex("""(?<=]\()(.+?)(?=\))""")
    // ］と（の連続があった直後の部位でかつ、)が直後にある　文字列に最短マッチ＝　[リンクテキスト（注釈1）](リンク先(注釈2))
    private val wikilink = Regex("""\[([^\[]+)]]""")

    private var session: AppSearchSession? = null
    suspend fun ensureSession(): AppSearchSession =
        session ?: withContext(Dispatchers.IO) {
            // 検索処理セッションを作成する。

            val searchContext = LocalStorage.SearchContext.Builder(context, "notes-db")
                .build()

            val resolvedSession =
                LocalStorage.createSearchSessionAsync(searchContext).get() // with Suspend
            // 検索対象のデータ型定義(NoteDocで定義したクラス)をApp　searchの検索処理セッションに登録する。

            val req = SetSchemaRequest.Builder()
                .addDocumentClasses(NoteDoc::class.java)
                .setForceOverride(true)
                .build()
            resolvedSession.setSchemaAsync(req).get() // with Suspend

            session = resolvedSession
            resolvedSession
        } // 　NoteDocを登録した検索セッションを初回は作成し、class propertyに保存。2回目以降は保存したセッションを返す。

    suspend fun indexAllFromTree(
        treeUri: Uri,
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }
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

        val mdFiles = mutableListOf<DocumentFile>().also { collect(root, it) }
        val total = mdFiles.size
        Log.d("list files", "root.listFiles().size=${total}")
        val indexBeginTime = System.currentTimeMillis()
        Log.i("indexAllFromTree", "indexing has began at $indexBeginTime")
        onProgress(0, total)

        // 進捗表示を可能にするため、1回めは全体数走査（Total）、2回めで呼び出し元のprocessを増やしながらインデックス作成する
        var processed = 0
        val notes = mutableListOf<NoteDoc>()   // ← NoteDoc を貯めるリスト

        for (f in mdFiles) {
            context.contentResolver.openInputStream(f.uri)?.use { ins ->
                // URI上のファイルからNoteDocの形式でDocumentFile構造を作成
                val rawText = ins.bufferedReader(Charsets.UTF_8).readText()
                val title = f.name?.removeSuffix(".md") ?: "untitled"
                val id = stableId(f.uri.toString()) // 固有のIDを作る
                val updatedAt = (f.lastModified()).takeIf { it > 0 } ?: System.currentTimeMillis()
                val tags = parseTagsFromText(rawText)
                val parsedText = parseInternalLinks(rawText) // [](title) →　[](docid:docid)
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
            onProgress(processed, total)
        }
        if (notes.isEmpty()) {
            Log.w("IndexFromUri", "No documents were indexed.")
            return@withContext 0
        } // 空ならエラーを出して早期リターン

        Log.d("AppSearch", "notes.size=${notes.size}")

        // バッチ投入（100件ずつ）
        notes.chunked(100).forEach { chunk ->
            val req = PutDocumentsRequest.Builder()
                .addDocuments(chunk)       // ← 型付きドキュメント
                .build()

            Log.d("AppSearch", "put chunk.size=${chunk.size}")
            s.putAsync(req).get()
        }
        val indexTime = System.currentTimeMillis() - indexBeginTime
        Log.i("indexAllFromTree", "indexing takes $indexTime ms ")
        notes.size
    } // URIで与えられたフォルダ以下のファイルをNoteDocクラス形式でインデックス化
    suspend fun clearAll(): Void? = withContext(Dispatchers.IO) {
        val session = ensureSession()
        val searchSpec = SearchSpec.Builder()
            .addFilterNamespaces("notes") // "notes" 名前空間を指定
            .build()
        // 空のクエリと名前空間フィルタで、該当するすべてのドキュメントを削除
        session.removeAsync("", searchSpec).get()
    } // 作成したインデックスを削除
    suspend fun getMarkdownByPath(path: String): String = withContext(Dispatchers.IO) {
        val uri = path.toUri()
        context.contentResolver.openInputStream(uri)?.use { ins ->
            BufferedReader(InputStreamReader(ins, Charsets.UTF_8)).readText()
        } ?: ""
    }
    @SuppressLint("RequiresFeature")
    suspend fun search(query: String, limit: Int = 100): List<SearchHit> =
        withContext(Dispatchers.IO) {
            val s = ensureSession()
            val tokens = nfkc(query)
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }

            //　検索セッションの条件
            val specBuilder = SearchSpec.Builder()
                .addFilterNamespaces("notes")
                .setTermMatch(SearchSpec.TERM_MATCH_PREFIX) // 前方一致
                .setSnippetCount(1) // 各ドキュメント1スニペット
                .setMaxSnippetSize(120) // スニペットは120文字
                .setSnippetCountPerProperty(1)
                .setRankingStrategy(SearchSpec.RANKING_STRATEGY_RELEVANCE_SCORE)
                .setResultCountPerPage(limit)

            // property weight のサポートがあればtagsを強く。
            val features: Features = s.features
            val isWeightSupported =
                features.isFeatureSupported(Features.SEARCH_SPEC_PROPERTY_WEIGHTS)
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
    override suspend fun findNoteById(id: String): NoteDoc? = withContext(Dispatchers.IO) {
        val s = ensureSession()
        val req = GetByDocumentIdRequest.Builder("notes")
            .addIds(id)
            .build()
        val res = s.getByDocumentIdAsync(req).get()
        // GenericDocument → NoteDoc に変換
        val gd = res.successes[id]
        if (gd == null) {
            Log.w("findNoteById", "Document not found for $id.")
        }
        gd?.toDocumentClass(NoteDoc::class.java)
    }

    override suspend fun findNoteByTitle(title: String): NoteDoc? = withContext(Dispatchers.IO) {
        val s = ensureSession()
        val propertyPaths = mutableListOf("title")

        val spec = SearchSpec.Builder()
            .addFilterNamespaces("notes")
            .addFilterProperties(NoteDoc::class.java,propertyPaths)
            .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
            .setResultCountPerPage(1)
            .build()

        val query = nfkc(title)
        val results = s.search(query, spec)
        val page = results.nextPageAsync.get()
        if (page.isNullOrEmpty()) {
            Log.w("findNoteByTitle", "no document was found by $query")
            return@withContext null
        }
        val doc = page.firstOrNull()?.genericDocument ?: return@withContext null
        NoteDoc(
            id = doc.id,
            path = doc.getPropertyString("path") ?: "",
            title = doc.getPropertyString("title") ?: "",
            content = doc.getPropertyString("content") ?: "",
            tags = doc.getPropertyStringArray("tags")?.toList().orEmpty(),
            updatedAt = doc.getPropertyLong("updatedAt")
        )
    }
    override suspend fun resolveTitleToId(title: String): String? {
        val note = findNoteByTitle(title)
        if (note == null) {
            Log.w("resolveTitleTold", "no document was found by $title")
            return null
        } else {
            val resolvedId = note.id
            Log.i("resolveTitleTold", "found document id $resolvedId by $title")
            resolvedId
        }
        return null
    }

    suspend fun parseInternalLinks(raw: String): String {
            // [[title]] ウィキリンクを[title](title)に寄せる
            val stage1 = wikilink.replace(raw) { m ->
                "[${m.groupValues[1]}](${m.groupValues[1]})"
            }
            val result = StringBuilder()

            var lastIndex = 0
            // 正規表現にマッチするすべての箇所をループ処理
            linkTargetInsideParens.findAll(stage1).forEach { m ->
                val target = m.value.trim()
                // 直前のマッチの終わりから、今回のマッチの始まりまでを追加
                result.append(stage1.substring(lastIndex, m.range.first))

                val passThrough = target.contains("://", true) ||
                        target.startsWith("#") ||
                        target.startsWith("doc:", ignoreCase = true) ||
                        target.startsWith("docid:", ignoreCase = true)

                val replacement = if (passThrough) {
                    target
                } else {
                    // suspend 関数を安全に呼び出す
                    resolveTitleToId(target)?.let { id ->
                        Log.d("parseInternalLink", "target=$target was parsed as docid=$id")
                        "docid:$id"
                    } ?: run {
                        Log.w("parseInternalLink", "target=$target was not found")
                        "doc:" + Uri.encode(target)
                    }
                }
                result.append(replacement)
                lastIndex = m.range.last + 1
            }
            // 最後のマッチ以降の残りの文字列を追加
            if (lastIndex < stage1.length) {
                result.append(stage1.substring(lastIndex))
            }
            return result.toString()
        }
}
interface NoteIndex {
    suspend fun findNoteById(id: String): NoteDoc?
    suspend fun findNoteByTitle(title: String): NoteDoc?
    suspend fun resolveTitleToId(title: String): String? // タイトルからDocid
}
data class SearchHit(
    val id: String,
    val path: String,
    val title: String,
    val snippet: String
) //　虫垂炎