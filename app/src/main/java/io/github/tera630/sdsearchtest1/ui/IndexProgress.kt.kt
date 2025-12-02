package io.github.tera630.sdsearchtest1.ui

data class IndexProgress(
    val phase:IndexPhase,
    val total: Int,
    val processed: Int
) {
    val fraction: Float get() = if (total == 0) 0f else processed.toFloat() / total
}
enum class IndexPhase {
    FILE_SCANNING,    // ファイル収集中（ツリー走査）
    INDEX_BUILDING,   // インデックス作成中（Markdown→NoteDoc）
    DB_WRITING        // データベース作成中（AppSearchへ書き込み）
}