package io.github.tera630.sdsearchtest1.data
import java.text.Normalizer

// テキストファイルのテキストから、タグ行を抜き出す。
private val tagLineRegex = Regex("""^\s*(tags?|タグ)\s*:\s*(.+)$""", RegexOption.IGNORE_CASE)
/**
 * 例:
 * Tag: 虫垂炎 診断 治療
 * Tag: 便秘, 大腸内視鏡 / 鎮静
 */

data class ParsedTags(
    val tags: List<String>,
    val contentWithoutTagLines: String
)

fun parseTagsAndStrip(text: String): ParsedTags {
    val tags = mutableListOf<String>()
    val keptLines = mutableListOf<String>()

    text.lineSequence().forEach { line ->
        val match = tagLineRegex.find(line)
        if (match != null) {
            // この行は本文に入れず、タグ文字列を抽出
            val raw = match.groupValues[1]
            val extracted = raw.split(Regex("""[,\uFF0C\u3001\u3002\u30FB/|\s]+"""))
                .map { nfkc(it) }
                .filter { it.isNotEmpty() }
            tags += extracted
        } else {
            keptLines += line
        }
    }

    val distinctTags = tags.distinct()
    val body = keptLines.joinToString("\n")
    return ParsedTags(tags = distinctTags, contentWithoutTagLines = body)
}
fun nfkc(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFKC).trim()