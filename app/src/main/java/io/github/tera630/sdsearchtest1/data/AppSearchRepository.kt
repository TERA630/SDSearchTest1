package io.github.tera630.sdsearchtest1.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.appsearch.app.AppSearchSession
import androidx.appsearch.app.PutDocumentsRequest
import androidx.appsearch.app.SearchSpec
import androidx.appsearch.app.SetSchemaRequest
import androidx.appsearch.localstorage.LocalStorage
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.Normalizer
import java.util.UUID
import io.github.tera630.sdsearchtest1.data.NoteDoc
import androidx.core.net.toUri

class AppSearchRepository(private val context: Context) {

    private var session: AppSearchSession? = null

    private suspend fun ensureSession(): AppSearchSession =
        session ?: withContext(Dispatchers.IO) {

            val searchContext = LocalStorage.SearchContext.Builder(context,"notes-db")
                .build()

            val resolvedSession = LocalStorage.createSearchSessionAsync(searchContext).get() // with Suspend

            val req = SetSchemaRequest.Builder()
                .addDocumentClasses(NoteDoc::class.java)
                .setForceOverride(true)
                .build()
            resolvedSession.setSchemaAsync(req).get() // with Suspend

            session = resolvedSession // 返り値をclass propertyにも格納
            resolvedSession
        }

    suspend fun indexAllFromTree(treeUri: Uri,
             onProgress:(Processed: Int, Total: Int) -> Unit ={_,_->}
    ): Int = withContext(Dispatchers.IO) {

        val s = ensureSession() // notes-db の SearchSession（Schema済）
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext 0
    //  まず 下位ディレクトリも含め.md を全部集めて総数を出す（1パス）

        fun collect(dir: DocumentFile, out: MutableList<DocumentFile>) {
            dir.listFiles().forEach { f ->
                if (f.isDirectory) collect(f, out)
                else if (f.isFile && f.name?.endsWith(".md", ignoreCase = true) == true) out += f
            }
        }

        val mdFiles = mutableListOf<DocumentFile>().also{ collect(root, it) }
        val total = mdFiles.size
        Log.d("list files","root.listFiles().size=${total}")
        onProgress(0,total)

        // 2Pass Making Index
        val progressed = 0;
        val notes = mutableListOf<NoteDoc>()   // ← NoteDoc を貯める

        for (f in mdFiles) {
            context.contentResolver.openInputStream(f.uri)?.use { ins ->
                val text = ins.bufferedReader(Charsets.UTF_8).readText()
                val title = f.name?.removeSuffix(".md") ?: "untitled"
                val id = stableId(f.uri.toString())
                val updatedAt = (f.lastModified()).takeIf { it > 0 } ?: System.currentTimeMillis()

                notes += NoteDoc(
                    id = id,
                    path = f.uri.toString(),
                    title = title,
                    content = text,
                    updatedAt = updatedAt
                )
            }
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

    suspend fun getMarkdownByPath(path: String): String = withContext(Dispatchers.IO) {
        val uri = path.toUri()
        context.contentResolver.openInputStream(uri)?.use { ins ->
            BufferedReader(InputStreamReader(ins, Charsets.UTF_8)).readText()
        } ?: ""
    }
    suspend fun search(query: String, limit: Int = 100): List<SearchHit> = withContext(Dispatchers.IO) {
        val s = ensureSession()

        // 前処理（NFKC 正規化 + 前後空白除去）
        val q = Normalizer.normalize(query.trim(), Normalizer.Form.NFKC)

        val spec = SearchSpec.Builder()
            .addFilterNamespaces("notes")
            .setTermMatch(SearchSpec.TERM_MATCH_PREFIX) // 前方一致
            .setSnippetCount(1) // 各ドキュメント1スニペット
            .setMaxSnippetSize(120)
            .setSnippetCountPerProperty(1)
            .setRankingStrategy(SearchSpec.RANKING_STRATEGY_RELEVANCE_SCORE)
            .setResultCountPerPage(limit)
            .build()

        val results = s.search(q, spec)
        val page = results.nextPageAsync.get()

        page.map { r ->
            val doc = r.genericDocument
            val path = doc.getPropertyString("path")!!
            val title = doc.getPropertyString("title")!!
            val snippetObj = doc.getProperty("snippet")// 1.1.0系で取得方法が変わる場合あり
            // content のスニペット抽出（安全策：自前でも一行スニペットを作る）
            val content = doc.getPropertyString("content") ?: ""
            val hitLine =
                content.lineSequence().firstOrNull { it.contains(query) } ?: content.take(120)

            SearchHit(
                id = doc.id,
                path = path,
                title = title,
                snippet = hitLine
            )
        }
    }
    private fun stableId(path: String): String =
        UUID.nameUUIDFromBytes(path.toByteArray()).toString()
}

data class SearchHit(
    val id: String,
    val path: String,
    val title: String,
    val snippet: String
)
