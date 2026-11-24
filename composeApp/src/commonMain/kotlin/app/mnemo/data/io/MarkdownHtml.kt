package app.mnemo.data.io

/**
 * Mnemo's light Markdown → the HTML Anki shows: bold, italics, code, images, links, headings,
 * quotes, lists and line breaks. `{{…}}` template and cloze markers and `[sound:…]` pass through.
 */
object MarkdownHtml {
    private val protectedToken = Regex("""\{\{.*?\}\}|\[sound:[^\]]+]""")
    private val fence = Regex("""```[^\n]*\n(.*?)```""", RegexOption.DOT_MATCHES_ALL)
    private val image = Regex("""!\[[^\]]*]\(([^)\s]+)\)""")
    private val link = Regex("""\[([^\]]+)]\(([^)\s]+)\)""")
    private val bold = Regex("""\*\*(.+?)\*\*|__(.+?)__""")
    private val italic = Regex("""(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])|(?<![\w_])_(?!\s)(.+?)(?<!\s)_(?![\w_])""")
    private val code = Regex("""`([^`]+)`""")

    fun toHtml(markdown: String): String {
        if (markdown.isBlank()) return ""
        val kept = mutableListOf<String>()

        fun keep(s: String): String {
            kept += s
            return "\u0000${kept.size - 1}\u0000"
        }
        var t = markdown.replace("\r\n", "\n")
        t = protectedToken.replace(t) { keep(it.value) }
        t = fence.replace(t) { keep("<pre><code>${escape(it.groupValues[1].trimEnd())}</code></pre>") }
        t = code.replace(t) { keep("<code>${escape(it.groupValues[1])}</code>") }
        t = escape(t)
        t = image.replace(t) { keep("<img src=\"${it.groupValues[1]}\">") }
        t = link.replace(t) { keep("<a href=\"${it.groupValues[2]}\">${it.groupValues[1]}</a>") }
        t = bold.replace(t) { "<b>${it.groupValues[1].ifEmpty { it.groupValues[2] }}</b>" }
        t = italic.replace(t) { "<i>${it.groupValues[1].ifEmpty { it.groupValues[2] }}</i>" }
        t =
            t.lines().joinToString("<br>") { line ->
                val l = line.trimEnd()
                when {
                    l.matches(Regex("^#{1,6} .*")) -> "<b>${l.trimStart('#').trim()}</b>"
                    l.startsWith("&gt; ") || l == "&gt;" -> "<i>${l.removePrefix("&gt;").trim()}</i>"
                    l.matches(Regex("^\\s*[-*+] .*")) -> "• " + l.trimStart().drop(2)
                    else -> l
                }
            }
        // Put back what was kept (kept pieces may hold others, so repeat).
        repeat(3) { t = Regex("\u0000(\\d+)\u0000").replace(t) { Regex.escapeReplacement(kept[it.groupValues[1].toInt()]) } }
        return t
    }

    /** Text without any HTML (Anki's sort field). */
    fun plain(html: String): String =
        html
            .replace(Regex("<br\\s*/?>"), " ")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .trim()

    private fun escape(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
