
package io.github.tera630.sdsearchtest1.data
import androidx.appsearch.annotation.Document

import androidx.appsearch.app.AppSearchSchema
@Document
data class NoteDoc(
    @Document.Namespace
    val namespace: String = "notes",

    @Document.Id
    val id: String,

    @Document.StringProperty // path はデフォルトのインデックス設定（通常は EXACT_TERM）
    val path: String,
// --------------------------------------
    @Document.StringProperty(
        indexingType = AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES, // カンマを追加し、定数のパスを修正
        tokenizerType = AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN  // TOKENIZER_TYPE_WORD を PLAIN に変更、カンマを追加
    )
    val title: String,
// --------------------------------------
 @Document.StringProperty(
        indexingType = AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES, // パラメータ名を小文字にし、定数のパスを修正、カンマを追加
        tokenizerType = AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN  // パラメータ名を小文字にし、TOKENIZER_TYPE_WORD を PLAIN に変更
    )
    val content: String,

    @Document.LongProperty
    val updatedAt: Long
)