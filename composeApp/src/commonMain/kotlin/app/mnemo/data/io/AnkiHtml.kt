package app.mnemo.data.io

/**
 * Turns the HTML Anki stores in fields into the light Markdown Mnemo renders:
 * bold/italic, line breaks, images (`![](file)`), and `[sound:file]` kept as is.
 */
object AnkiHtml {
    private val entities =
        mapOf(
            "&nbsp;" to " ",
            "&amp;" to "&",
            "&lt;" to "<",
            "&gt;" to ">",
            "&quot;" to "\"",
            "&#39;" to "'",
            "&apos;" to "'",
        )

    fun toMarkdown(html: String): String {
        var s = html
        s = s.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        s = s.replace(Regex("""</(div|p|li|tr|h[1-6])>""", RegexOption.IGNORE_CASE), "\n")
        s = s.replace(Regex("""<(div|p|tr|h[1-6])(\s[^>]*)?>""", RegexOption.IGNORE_CASE), "\n")
        s = s.replace(Regex("""<li[^>]*>""", RegexOption.IGNORE_CASE), "- ")
        s =
            s.replace(Regex("""<(b|strong)>(.*?)</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) {
                "**${it.groupValues[2].trim()}**"
            }
        s =
            s.replace(
                Regex("""<(i|em)>(.*?)</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            ) { "*${it.groupValues[2].trim()}*" }
        s =
            s.replace(
                Regex("""<code>(.*?)</code>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            ) { "`${it.groupValues[1]}`" }
        s = s.replace(Regex("""<img[^>]*src\s*=\s*["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)) { "![](${it.groupValues[1]})" }
        s = s.replace(Regex("""<[^>]+>"""), "")
        entities.forEach { (k, v) -> s = s.replace(k, v) }
        s =
            s.replace(Regex("""&#(\d+);""")) {
                it.groupValues[1]
                    .toIntOrNull()
                    ?.toChar()
                    ?.toString() ?: ""
            }
        s = s.replace(Regex("""[ \t]+\n"""), "\n").replace(Regex("""\n{3,}"""), "\n\n")
        return s.trim()
    }

    /** `[sound:x.mp3]` references in a field. */
    fun sounds(text: String): List<String> = Regex("""\[sound:([^\]]+)\]""").findAll(text).map { it.groupValues[1] }.toList()

    /** `![](file)` references in a field. */
    fun images(text: String): List<String> = Regex("""!\[[^\]]*\]\(([^)]+)\)""").findAll(text).map { it.groupValues[1] }.toList()
}
