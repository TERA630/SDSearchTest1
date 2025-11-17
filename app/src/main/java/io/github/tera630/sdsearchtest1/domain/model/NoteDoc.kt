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
// -------------------------------------Title ：　文書タイトル＝ファイル名(拡張子除く)
    @Document.StringProperty(
        indexingType = AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES,
        tokenizerType = AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN
    )
    val title: String,
// --------------------------------------Content :　本文
 @Document.StringProperty(
        indexingType = AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES,
        tokenizerType = AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN
    )
    val content: String,
// ------------------------------------- tag : タグ　文書に設定したタグ
    @Document.StringProperty(
        indexingType = AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES,
        tokenizerType = AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_VERBATIM
    )
    val tags: List<String> = emptyList(),
// ------------------------------------ Heading : 見出し　文書内の段落
    @Document.StringProperty(
        indexingType = AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES,
        tokenizerType = AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN
    )
    val heading: String ="",

//------------------------------------  updatedAt
    @Document.LongProperty
    val updatedAt: Long
)