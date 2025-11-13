package io.github.tera630.sdsearchtest1.data.local

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.github.tera630.sdsearchtest1.data.local.FileRepository
import java.text.Normalizer
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

    override fun stableId(path: String): String =
        UUID.nameUUIDFromBytes(path.toByteArray()).toString()

    override fun buildTitleIdMap(files: List<DocumentFile>): Map<String, String> =
        linkedMapOf<String, String>().also { map ->
            files.forEach { f ->
                val t = fileTitle(f)
                val key = Normalizer.normalize(t, Normalizer.Form.NFKC).trim().lowercase()
                map.putIfAbsent(key, stableId(f.uri.toString()))
            }
        }
}