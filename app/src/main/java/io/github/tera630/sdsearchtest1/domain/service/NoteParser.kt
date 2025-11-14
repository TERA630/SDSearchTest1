package io.github.tera630.sdsearchtest1.domain.service

import android.util.Log

class NoteParser() {
    private val wikilink = Regex("""\[([^\[]+)]]""")
    private val linkTargetInsideParens = Regex("""(?<=]\()(.+?)(?=\))""")

    fun parseContent(raw: String, titleToId:Map<String,String>): String {
        // [[title]] → [title](title)
        val stage1 = wikilink.replace(raw) { m -> "[${m.groupValues[1]}](${m.groupValues[1]})" }
        val sb = StringBuilder()
        var last = 0
        linkTargetInsideParens.findAll(stage1).forEach { m ->
            val target = m.value.trim()
            sb.append(stage1.substring(last, m.range.first))
            val passThrough = target.contains("://", true) ||
                    target.startsWith("#") ||
                    target.startsWith("doc:", true) ||
                    target.startsWith("docid:", true)
            val rep = if (passThrough) {
                target
            } else {
                val key = nfkc(target).lowercase()
                titleToId[key]?.let{ id->
                    val rep = "docid:$id"
                    Log.d("parseContent","$key was parsed int $rep")
                    rep
                } ?: run{
                    // リンク解決不能
                    Log.w("parseContent","no id was associated by $target")
                    target
                }
            }
            sb.append(rep)
            last = m.range.last + 1
        }
        if (last < stage1.length) sb.append(stage1.substring(last))
        return sb.toString()
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
