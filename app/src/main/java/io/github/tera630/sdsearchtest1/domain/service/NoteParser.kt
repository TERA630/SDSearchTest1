package io.github.tera630.sdsearchtest1.domain.service

import android.util.Log

//　インフラに依存しないロジック

class NoteParser() {
    private val wikilink = Regex("""\[\[([^\[]+)]]""")
    private val linkTargetInsideParens = Regex("""(?<=]\()(.+?)(?=\))""")

    data class ContentsParsed (
        val content:String,
        val unresolvedLinks:List<String>
    )

    // @param rawText 元テキスト
    // @param titleToId 正規化したタイトルとdocidの対応表
    fun parseContent(raw: String, titleToId:Map<String,String>): ContentsParsed {
        // [[title]] → [title](title)
        val stage1 = wikilink.replace(raw) { m ->
            "[${m.groupValues[1]}](${m.groupValues[1]})"
        }
        val sb = StringBuilder()
        val unresolved = mutableListOf<String>()
        var last = 0
        linkTargetInsideParens.findAll(stage1).forEach { m ->
            val target = m.value.trim()
            sb.append(stage1.substring(last, m.range.first))
            val passThrough = target.contains("://", true) ||
                    target.startsWith("#") ||
                    target.startsWith("docid:", true)
            val linkAddress = if (passThrough) {
                target
            } else {
                val key = nfkc(target) .trim()
                titleToId[key]?.let{ id->
                    val replace = "docid:$id"
                    replace
                } ?: run{
                    // 解決不能なリンク(タイトル間違いやタイトルを持つファイルがない)
                    Log.w("parseContent","no id was associated by $target")
                    unresolved += target
                    target
                }
            }
            sb.append(linkAddress)
            last = m.range.last + 1
        }

        if (last < stage1.length) sb.append(stage1.substring(last))

        return ContentsParsed(
            content = sb.toString(),
            unresolvedLinks = unresolved
        )
    }

    fun parseTagsFromText(text: String, normalize: (String) -> String): List<String> {
        val tagLineRegex = Regex("""^\s*(tag|tags|Tag|Tags|TAG|TAGs|タグ)\s*:\s*(.+)$""", RegexOption.MULTILINE)
        val line = tagLineRegex.find(text)?.groupValues?.getOrNull(2)?.trim().orEmpty()
        if (line.isEmpty()) return emptyList()
        return line.split(Regex("""[,\u3001\u3002\u30FB/|\s]+"""))
            .map(normalize)
            .filter { it.isNotEmpty() }
            .distinct()
    }
}
