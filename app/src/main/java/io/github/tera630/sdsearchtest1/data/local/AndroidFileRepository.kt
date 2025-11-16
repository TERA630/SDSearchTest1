package io.github.tera630.sdsearchtest1.data.local

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import io.github.tera630.sdsearchtest1.domain.repo.FileRepository
import io.github.tera630.sdsearchtest1.domain.service.nfkc
import java.util.UUID

class AndroidFileRepository(private val context: Context) : FileRepository {
    override suspend fun collectMarkdownFiles(treeUri: Uri): List<DocumentFile> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val out = mutableListOf<DocumentFile>()
        fun walk(d: DocumentFile) {
            d.listFiles().forEach { f ->
                if (f.isDirectory) walk(f)
                else if (f.isFile && f.name?.endsWith(".md", true) == true) out += f
            }
        }
        walk(root)
        return out
    }
    override suspend fun readText(file: DocumentFile): String =
        context.contentResolver.openInputStream(file.uri)?.use { it.reader(Charsets.UTF_8).readText() }.orEmpty()

    override suspend fun lastModified(file: DocumentFile): Long =
        (file.lastModified()).takeIf { it > 0 } ?: System.currentTimeMillis()

    override fun fileTitle(file: DocumentFile): String =
        (file.name ?: "untitled").removeSuffix(".md")

    override fun stableId(uriString: String): String =
        UUID.nameUUIDFromBytes(uriString.toByteArray()).toString()

    override fun buildTitleIdMap(files: List<DocumentFile>): Map<String, String> {

        val titleMapStartTime = System.currentTimeMillis()
        val titleToId = LinkedHashMap<String, String>()
        val duplicates = mutableListOf<String>() // タイトルが重複した場合はこちらのリストに入る。
        for (f in files) {
            val rawTitle = fileTitle(f)
            val nfkcTitle = nfkc(rawTitle)                 // 既存の正規化関数を利用（NFKC）:contentReference[Title:1]{index=1}
            val id = stableId(f.uri.toString())
            val key = nfkcTitle
            if (titleToId.containsKey(key)) {
                duplicates += nfkcTitle                     // 重複の検出だけログに回す
            } else {
                Log.d("indexingPhase","$nfkcTitle was indexed as $id")
                titleToId[key] = id
            }
        }
        if (duplicates.isNotEmpty()) {
            Log.w("indexingPhase", "duplicated titles: $duplicates") // ポリシー：先勝ち
        }
        val titleMapTime = System.currentTimeMillis() - titleMapStartTime
        Log.d("indexingPhase", "title map making took $titleMapTime")
        return titleToId
    }
}