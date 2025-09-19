
package io.github.tera630.sdsearchtest1.data

import androidx.appsearch.annotation.Document

@Document
data class NoteDoc(
    @Document.Namespace
    val namespace: String = "notes",

    @Document.Id
    val id: String, // 安定ID（pathのハッシュなど）

    @Document.StringProperty
    val path: String, // SAF の documentUri 文字列

    @Document.StringProperty
    val title: String,

    // 検索対象の本文（全文）
    @Document.StringProperty
    val content: String,

    // 将来 N-gram/NFKC を入れる拡張用（現状は空でOK）
    @Document.StringProperty
    val contentNGram: String? = null,

    @Document.LongProperty
    val updatedAt: Long
)
