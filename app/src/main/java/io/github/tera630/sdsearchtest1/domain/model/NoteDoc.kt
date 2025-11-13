package io.github.tera630.sdsearchtest1.domain.model

import androidx.appsearch.annotation.Document
import androidx.appsearch.app.AppSearchSchema

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
        indexingType = AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES,
        tokenizerType = AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN
    )
    val title: String,
// --------------------------------------
 @Document.StringProperty(
        indexingType = AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES,
        tokenizerType = AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN
    )
    val content: String,
    @Document.StringProperty(
        indexingType = AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES,
        tokenizerType = AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_VERBATIM
    )
    val tags: List<String> = emptyList(),
    @Document.LongProperty
    val updatedAt: Long
)