package io.github.tera630.sdsearchtest1.data
import java.text.Normalizer

private val tagLineRegex = Regex("""^\s*(tag|tags|Tag|Tags|TAG|TAGs|タグ)\s*:\s*(.+)$""")

/**
 * 例:
 * Tag: 虫垂炎 診断 治療 痙攣
 * Tag: 便秘, 大腸内視鏡 / 鎮静
 */
fun parseTagsFromText(text: String): List<String> {
    val line = text.lineSequence().firstOrNull { tagLineRegex.containsMatchIn(it) } ?: return emptyList()
    val raw = tagLineRegex.find(line)?.groupValues?.getOrNull(2)?.trim().orEmpty()
    if (raw.isEmpty()) return emptyList()

    // 区切り文字：スペース・カンマ（半/全）・スラッシュ・中点・読点を許容
    return raw.split(Regex("""[,\u3001\u3002\u30FB/|\s]+"""))
        .map { nfkc(it)}
        .filter { it.isNotEmpty() }
        .distinct()
}
fun nfkc(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFKC).trim()