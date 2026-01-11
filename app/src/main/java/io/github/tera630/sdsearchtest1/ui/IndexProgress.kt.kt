package io.github.tera630.sdsearchtest1.ui

data class IndexProgress(
    val phase: IndexPhase,
    val processed: Int,
    val total: Int
) {
    val fraction: Float get() = if (total == 0) 0f else processed.toFloat() / total
}

enum class IndexPhase {
    FILE_LOADING,          // ファイル読み込み
    LINK_TABLE_CREATING,   // リンクテーブル作成
    INDEX_CREATING,        // インデックス作成
    DATA_REGISTERING       // インデックスのデーター登録
}