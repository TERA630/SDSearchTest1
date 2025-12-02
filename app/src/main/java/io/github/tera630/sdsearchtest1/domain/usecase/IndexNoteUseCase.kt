package io.github.tera630.sdsearchtest1.domain.usecase

import android.net.Uri
import android.util.Log
import io.github.tera630.sdsearchtest1.domain.model.NoteDoc
import io.github.tera630.sdsearchtest1.domain.repo.NoteIndexRepository
import io.github.tera630.sdsearchtest1.domain.service.NoteParser
import io.github.tera630.sdsearchtest1.domain.service.TagNormalizer
import io.github.tera630.sdsearchtest1.domain.repo.FileRepository
import io.github.tera630.sdsearchtest1.ui.IndexPhase
import java.util.UUID

//　ファイルから､インデックス構築手順(UseCase)のロジック｡
class IndexNotesUseCase(
    private val fileRepo: FileRepository,
    private val noteParser: NoteParser,
    private val indexRepo: NoteIndexRepository
) {
    suspend operator fun invoke(
        treeUri: Uri,
        onProgress: (phase: IndexPhase,processed:Int, total:Int) -> Unit = { _, _,_ -> }
    ): Int {

        val normalize = TagNormalizer::nfkc

        // インデックス作成前に前のインデックスを消去。
        indexRepo.clearAll()

        val notes = mutableListOf<NoteDoc>()
        val unresolvedSummery = StringBuilder()
        val fileCollectStart = System.currentTimeMillis()
        val files = fileRepo.collectMarkdownFiles(treeUri)
        val total = files.size
        val fileCollectTime = System.currentTimeMillis() - fileCollectStart
        Log.d("Indexing phase", "$total files collection took $fileCollectTime ms")

        val titleToId = fileRepo.buildTitleIdMap(files)
        val indexingMakingStartTime = System.currentTimeMillis()

        for (f in files) {
            val raw = fileRepo.readText(f)
            val nfkcTitle = normalize(fileRepo.fileTitle(f))
            val id = titleToId[nfkcTitle] !!
            val parsed = noteParser.parseContent(raw, titleToId)
            val tags = noteParser.parseTagsFromText(raw, normalize)
            val headings = noteParser.extractHeadings(raw)
            val updatedAt = fileRepo.lastModified(f)

            notes += NoteDoc(
                id = id,
                title = nfkcTitle,
                path = f.uri.toString(),
                content = parsed.content,
                tags = tags,
                heading = headings,
                updatedAt = updatedAt
            )
            if(parsed.unresolvedLinks.isNotEmpty()){
                unresolvedSummery.appendLine("---${nfkcTitle}.md---")
                unresolvedSummery.appendLine("未解決リンク")
                parsed.unresolvedLinks.forEach { link ->
                    unresolvedSummery.appendLine("・$link")
                }
                unresolvedSummery.appendLine()
            }
        }
        //  全てのファイル処理終了後
        if(unresolvedSummery.isNotEmpty()){
            val unresolvedCollection = unresolvedSummery.toString().trimEnd()
            val unresolvedID = UUID.nameUUIDFromBytes("unresolved".toByteArray(Charsets.UTF_8)).toString()

            notes += NoteDoc(
                id = unresolvedID,
                title = "未解決リンク",
                path = "",
                content = unresolvedCollection,
                tags = emptyList(),
                updatedAt = System.currentTimeMillis()
            )
        }
        val indexingMakingTime = System.currentTimeMillis() - indexingMakingStartTime
        Log.d("indexNotes", "indexing making took $indexingMakingTime ms")

        return indexRepo.putAll(notes){ processed, totalNotes ->
            onProgress(IndexPhase.DB_WRITING, processed, totalNotes)
        }
    }
}
