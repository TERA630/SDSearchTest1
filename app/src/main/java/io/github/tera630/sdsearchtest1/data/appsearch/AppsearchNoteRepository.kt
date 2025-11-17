package io.github.tera630.sdsearchtest1.data.appsearch


import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.appsearch.app.*
import androidx.appsearch.localstorage.LocalStorage
import io.github.tera630.sdsearchtest1.domain.model.NoteDoc
import io.github.tera630.sdsearchtest1.domain.repo.NoteIndexRepository
import io.github.tera630.sdsearchtest1.domain.repo.SearchHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// データ(インフラ層)　AppSearchのセッションを保持。APIを叩く。
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

    override suspend fun putAll(notes: List<NoteDoc>, onProgress: (Int, Int) -> Unit): Int
    = withContext(Dispatchers.IO){
        val s = ensureSession()
        // 100件ずつバッチ
        var processed = 0

        notes.chunked(100).forEach { chunk ->
            val req = PutDocumentsRequest.Builder()
                .addDocuments(chunk)
                .build()
            s.putAsync(req).get()

            processed += chunk.size
            onProgress(processed, notes.size)
        }
        notes.size
    }

    override suspend fun clearAll() {
        val s = ensureSession()
        val spec = SearchSpec.Builder().addFilterNamespaces("notes").build()
        s.removeAsync("", spec).get()
        Log.d("clearAll","index was removed.")
    }

    @SuppressLint("RequiresFeature")
    override suspend fun search(query: String, limit: Int): List<SearchHit> {
        val s = ensureSession()
        val specBuilder = SearchSpec.Builder()
            .addFilterSchemas(NoteDoc::class.java.simpleName)
            .addFilterNamespaces("notes")
            .setTermMatch(SearchSpec.TERM_MATCH_PREFIX)
            .setResultCountPerPage(limit)
            .setSnippetCount(1)
            .setSnippetCountPerProperty(1)
            .setMaxSnippetSize(120)
            .setRankingStrategy(SearchSpec.RANKING_STRATEGY_RELEVANCE_SCORE)

        val features: Features = s.features
        val isWeightSupported = features.isFeatureSupported(Features.SEARCH_SPEC_PROPERTY_WEIGHTS)
        if(isWeightSupported){
            specBuilder.setPropertyWeightPaths(
                "noteDoc",
                mapOf(
                    PropertyPath("tags") to 5.0,
                    PropertyPath("title") to 6.0,
                    PropertyPath("content") to 1.0
                    )
            )
        }
        Log.d("AppSearchNoteRepository", "weightSupported={$isWeightSupported} on this device.")

        val spec = specBuilder.build()
        val searchStartTime = System.currentTimeMillis()
        val page = s.search(query, spec).nextPageAsync.get()
        val searchTime = System.currentTimeMillis() - searchStartTime
        Log.i("AppSearchNoteRepository", "search took $searchTime ms")
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
        val findStartTime = System.currentTimeMillis()
        val s = ensureSession()
        val req = GetByDocumentIdRequest.Builder("notes").addIds(id).build()
        val res = s.getByDocumentIdAsync(req).get()
        val g = if(res.isSuccess) {
            res.successes[id] ?: return null
        } else  {
            Log.w("findById","$id was not found.")
            return null
        }
        val findTime = System.currentTimeMillis() - findStartTime
        Log.d("AppSearchNoteRepository", "findById took $findTime ms")

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
