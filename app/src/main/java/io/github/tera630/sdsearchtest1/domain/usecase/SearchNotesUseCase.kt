package io.github.tera630.sdsearchtest1.domain.usecase

import io.github.tera630.sdsearchtest1.data.appsearch.NoteIndexRepository
import io.github.tera630.sdsearchtest1.data.appsearch.SearchHit

class SearchNotesUseCase(private val indexRepo: NoteIndexRepository) {
    suspend operator fun invoke(query: String, limit: Int = 100): List<SearchHit> =
        indexRepo.search(query, limit)
}