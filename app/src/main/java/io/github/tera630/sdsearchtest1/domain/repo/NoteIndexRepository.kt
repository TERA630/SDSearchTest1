package io.github.tera630.sdsearchtest1.domain.repo

import io.github.tera630.sdsearchtest1.domain.model.NoteDoc
import io.github.tera630.sdsearchtest1.ui.IndexPhase

interface NoteIndexRepository {
    suspend fun putAll(notes: List<NoteDoc>, onProgress: (indexPhase: IndexPhase, processed: Int, total: Int) -> Unit = { _, _, _ -> }): Int
    suspend fun clearAll()
    suspend fun search(query: String, limit: Int = 100): List<SearchHit>
    suspend fun findById(id: String): NoteDoc?
}


data class SearchHit(val id: String, val path: String, val title: String, val snippet: String)
