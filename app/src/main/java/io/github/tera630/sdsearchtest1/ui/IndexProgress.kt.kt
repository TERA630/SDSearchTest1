package io.github.tera630.sdsearchtest1.ui

data class IndexProgress(
    val total: Int,
    val processed: Int
) {
    val fraction: Float get() = if (total == 0) 0f else processed.toFloat() / total
}