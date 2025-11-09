
package io.github.tera630.sdsearchtest1.data
import androidx.appsearch.annotation.Document

import androidx.appsearch.app.AppSearchSchema

import androidx.appsearch.app.AppSearchSchema.StringPropertyConfig
@Document
data class NoteDoc(
    @Document.Namespace
    val namespace: String = "notes",

    @Document.Id
    val id: String,
// -----------------------------------------------------------------------------------
    @Document.StringProperty // path はデフォルトのインデックス設定（通常は EXACT_TERM）
    val path: String,
// -----------------------------------------------------------------------------------
    @Document.StringProperty(
        indexingType = StringPropertyConfig.INDEXING_TYPE_PREFIXES,
        tokenizerType = StringPropertyConfig.TOKENIZER_TYPE_PLAIN
    )
    val title: String,
// --------------------------------------
 @Document.StringProperty(
        indexingType = StringPropertyConfig.INDEXING_TYPE_PREFIXES,
        tokenizerType = StringPropertyConfig.TOKENIZER_TYPE_PLAIN
    )
    val content: String,
    @Document.StringProperty(
        indexingType = StringPropertyConfig.INDEXING_TYPE_PREFIXES,
        tokenizerType = StringPropertyConfig.TOKENIZER_TYPE_VERBATIM
    )
    val tags: List<String> = emptyList(),
    @Document.LongProperty
    val updatedAt: Long
)