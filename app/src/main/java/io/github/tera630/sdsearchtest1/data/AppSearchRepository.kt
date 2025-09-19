package io.github.tera630.sdsearchtest1.data

import android.content.Context
import android.net.Uri
import androidx.appsearch.app.AppSearchSession
import androidx.appsearch.app.PutDocumentsRequest
import androidx.appsearch.app.SetSchemaRequest
import androidx.appsearch.localstorage.LocalStorage
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

class AppSearchRepository(private val context: Context) {

    private var session: AppSearchSession? = null

    private suspend fun ensureSession(): AppSearchSession =
        session ?: withContext(Dispatchers.IO) {

            val searchContext = LocalStorage.SearchContext.Builder(context,"notes-db")
                .build()

            val resolvedSession = LocalStorage.createSearchSessionAsync(searchContext).get() // with Suspend

            val req = SetSchemaRequest.Builder()
                .addDocumentClasses(NoteDoc::class.java)
                .build()
            resolvedSession.setSchemaAsync(req).get() // with Suspend

            session = resolvedSession // 返り値をclass propertyにも格納
            resolvedSession
        }

    suspend fun indexAllFromTree(treeUri: Uri): Int = withContext(Dispatchers.IO) {
        val s = ensureSession() // notes-db の SearchSession（Schema済）

        val notes = mutableListOf<NoteDoc>()   // ← NoteDoc を貯める

        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext 0

        fun walk(dir: DocumentFile) {
            dir.listFiles().forEach { f ->
                if (f.isDirectory) {
                    walk(f)
                } else if (f.isFile && f.name?.endsWith(".md", ignoreCase = true) == true) {
                    context.contentResolver.openInputStream(f.uri)?.use { ins ->
                        val text = ins.bufferedReader(Charsets.UTF_8).readText()
                        val title = f.name?.removeSuffix(".md") ?: "untitled"
                        val id = stableId(f.uri.toString())
                        val updatedAt = (f.lastModified()).takeIf { it > 0 } ?: System.currentTimeMillis()

                        notes += NoteDoc(
                            id = id,
                            path = f.uri.toString(),
                            title = title,
                            content = text,
                            updatedAt = updatedAt
                        )
                    }
                }
            }
        }
        walk(root)

        // バッチ投入（100件ずつ）
        notes.chunked(100).forEach { chunk ->
            val req = PutDocumentsRequest.Builder()
                .addDocuments(chunk)       // ← 型付きドキュメント
                .build()
            s.putAsync(req).get()
        }
        notes.size
    }

    suspend fun getMarkdownByPath(path: String): String = withContext(Dispatchers.IO) {
        val uri = Uri.parse(path)
        context.contentResolver.openInputStream(uri)?.use { ins ->
            BufferedReader(InputStreamReader(ins, Charsets.UTF_8)).readText()
        } ?: ""
    }

    private fun stableId(path: String): String =
        UUID.nameUUIDFromBytes(path.toByteArray()).toString()
}

data class SearchHit(
    val id: String,
    val path: String,
    val title: String,
    val snippet: String
)
