package io.github.tera630.sdsearchtest1.domain.usecase

import io.github.tera630.sdsearchtest1.data.appsearch.NoteIndexRepository
import io.github.tera630.sdsearchtest1.domain.model.NoteDoc

class FindNoteByIdUseCase(private val indexRepo: NoteIndexRepository) {
    suspend operator fun invoke(id: String): NoteDoc? = indexRepo.findById(id)
}
