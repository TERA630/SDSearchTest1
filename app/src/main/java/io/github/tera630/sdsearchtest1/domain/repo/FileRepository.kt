package io.github.tera630.sdsearchtest1.domain.repo

import android.net.Uri
import androidx.documentfile.provider.DocumentFile

interface FileRepository {
    suspend fun collectMarkdownFiles(treeUri: Uri): List<DocumentFile>
    suspend fun readText(file: DocumentFile): String
    suspend fun lastModified(file: DocumentFile): Long
    fun fileTitle(file: DocumentFile): String // ".md" なし
    fun stableId(path: String): String
    fun buildTitleIdMap(files: List<DocumentFile>): Map<String, String> // normalizedTitle.lowercase -> id
}