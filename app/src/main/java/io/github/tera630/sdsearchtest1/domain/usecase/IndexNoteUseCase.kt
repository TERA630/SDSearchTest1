package io.github.tera630.sdsearchtest1.domain.usecase

import android.net.Uri
import io.github.tera630.sdsearchtest1.domain.model.NoteDoc
import io.github.tera630.sdsearchtest1.domain.repo.NoteIndexRepository
import io.github.tera630.sdsearchtest1.domain.service.NoteParser
import io.github.tera630.sdsearchtest1.domain.service.TagNormalizer
import io.github.tera630.sdsearchtest1.domain.repo.FileRepository

//　ファイルから､インデックス構築手順(UseCase)のロジック｡
class IndexNotesUseCase(
    private val fileRepo: FileRepository,
    private val noteParser: NoteParser,
    private val indexRepo: NoteIndexRepository
) {
    suspend operator fun invoke(treeUri: Uri, onProgress: (Int, Int) -> Unit = { _, _ -> }): Int {
        val files = fileRepo.collectMarkdownFiles(treeUri)
        val titleToId = fileRepo.buildTitleIdMap(files)
        val normalize = TagNormalizer::nfkc

        val notes = files.map { f ->
            val raw = fileRepo.readText(f)
            val title = normalize(fileRepo.fileTitle(f))
            val id = titleToId[title.lowercase()]!!
            val content = noteParser.parseContent(raw, titleToId)
            val tags = noteParser.parseTagsFromText(raw, normalize)
            val updatedAt = fileRepo.lastModified(f)
            NoteDoc(id = id, title = title, path = f.uri.toString(), content = content, tags = tags, updatedAt = updatedAt)
        }
        return indexRepo.putAll(notes, onProgress)
    }
}
