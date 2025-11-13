package io.github.tera630.sdsearchtest1.data.appsearch


import android.content.Context
import android.util.Log
import androidx.appsearch.app.*
import androidx.appsearch.localstorage.LocalStorage
import io.github.tera630.sdsearchtest1.domain.model.NoteDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class AppSearchNoteRepository(private val context: Context) : NoteIndexRepository {
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
            resolvedSession.setSchemaAsync(req).get() // SchemaにNoteDoc形式をセット、with Suspend
            session = resolvedSession
            resolvedSession
        } // 　NoteDocを登録した検索セッションを初回は作成し、class propertyに保存。2回目以降は保存したセッションを返す。


    override suspend fun putAll(notes: List<NoteDoc>, onProgress: (Int, Int) -> Unit): Int {
        val s = ensureSession()
        // 100件ずつバッチ
        var processed = 0
        notes.chunked(100).forEach { chunk ->
            val req = PutDocumentsRequest.Builder()
                .addDocuments(chunk)
                .build()
            Log.d("AppSearch", "put chunk.size=${chunk.size}")
            s.putAsync(req).get()

            processed += chunk.size
            onProgress(processed, notes.size)
        }
        return notes.size
    }

    override suspend fun clearAll() {
        val s = ensureSession()
        val spec = SearchSpec.Builder().addFilterNamespaces("notes").build()
        s.removeAsync("", spec).get()
    }

    override suspend fun search(query: String, limit: Int): List<SearchHit> {
        val s = ensureSession()
        val spec = SearchSpec.Builder()
            .addFilterNamespaces("notes")
            .setTermMatch(SearchSpec.TERM_MATCH_PREFIX)
            .setResultCountPerPage(limit)
            .setSnippetCount(1)
            .setSnippetCountPerProperty(1)
            .setMaxSnippetSize(120)
            .build()
        val page = s.search(query, spec).nextPageAsync.get()
        return page.map { r ->
            val g = r.genericDocument
            val content = g.getPropertyString("content").orEmpty()
            val snippet = content.take(120) // 簡易。必要ならトークン一致で抜粋
            SearchHit(
                id = g.id,
                path = g.getPropertyString("path").orEmpty(),
                title = g.getPropertyString("title").orEmpty(),
                snippet = snippet
            )
        }
    }

    override suspend fun findById(id: String): NoteDoc? {
        val s = ensureSession()
        val req = GetByDocumentIdRequest.Builder("notes").addIds(id).build()
        val res = s.getByDocumentIdAsync(req).get()
        val g = res.successes[id] ?: return null
        return NoteDoc(
            id = g.id,
            title = g.getPropertyString("title").orEmpty(),
            path = g.getPropertyString("path").orEmpty(),
            content = g.getPropertyString("content").orEmpty(),
            tags = g.getPropertyStringArray("tags")?.toList().orEmpty(),
            updatedAt = g.getPropertyLong("updatedAt")
        )
    }
}
