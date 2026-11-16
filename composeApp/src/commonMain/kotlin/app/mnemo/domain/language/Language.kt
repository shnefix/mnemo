package app.mnemo.domain.language

/** Grammatical gender, as the drills and card colouring use it. */
enum class Gender(
    val code: String,
) {
    Masculine("m"),
    Feminine("f"),
    Neuter("n"),
    ;

    companion object {
        fun parse(raw: String?): Gender? {
            val s = raw?.trim()?.lowercase() ?: return null
            return when (s) {
                "m", "masc", "masculine", "der", "el", "le", "il", "o" -> Masculine
                "f", "fem", "feminine", "die", "la", "a" -> Feminine
                "n", "neut", "neuter", "das" -> Neuter
                else -> null
            }
        }
    }
}

/** Per-language facts: articles, the words a drill shows, verb endings. */
class LanguageProfile(
    val code: String,
    val name: String,
    /** Definite articles by gender; null when the language has no article drill. */
    val articles: Map<Gender, String>?,
    /** Pronouns shown in the conjugation drill, in order. */
    val pronouns: List<String>,
    val conjugate: ((String) -> List<String>?)?,
) {
    val hasGender: Boolean get() = articles != null
    val hasConjugation: Boolean get() = conjugate != null

    /** Strips a leading article ("der Zug" → "Zug") and returns the gender it implied. */
    fun splitArticle(word: String): Pair<Gender?, String> {
        val arts = articles ?: return null to word.trim()
        val trimmed = word.trim()
        val first = trimmed.substringBefore(' ').lowercase()
        val g = arts.entries.firstOrNull { it.value == first }?.key
        return if (g != null && trimmed.contains(' ')) g to trimmed.substringAfter(' ').trim() else null to trimmed
    }

    companion object {
        val german =
            LanguageProfile(
                code = "de",
                name = "German",
                articles = mapOf(Gender.Masculine to "der", Gender.Feminine to "die", Gender.Neuter to "das"),
                pronouns = listOf("ich", "du", "er/sie/es", "wir", "ihr", "sie/Sie"),
                conjugate = ::conjugateGerman,
            )
        val spanish =
            LanguageProfile(
                code = "es",
                name = "Spanish",
                articles = mapOf(Gender.Masculine to "el", Gender.Feminine to "la"),
                pronouns = listOf("yo", "tú", "él/ella", "nosotros", "vosotros", "ellos/ellas"),
                conjugate = ::conjugateSpanish,
            )
        val french =
            LanguageProfile(
                code = "fr",
                name = "French",
                articles = mapOf(Gender.Masculine to "le", Gender.Feminine to "la"),
                pronouns = listOf("je", "tu", "il/elle", "nous", "vous", "ils/elles"),
                conjugate = ::conjugateFrench,
            )
        val italian =
            LanguageProfile(
                code = "it",
                name = "Italian",
                articles = mapOf(Gender.Masculine to "il", Gender.Feminine to "la"),
                pronouns = listOf("io", "tu", "lui/lei", "noi", "voi", "loro"),
                conjugate = null,
            )
        val portuguese =
            LanguageProfile(
                code = "pt",
                name = "Portuguese",
                articles = mapOf(Gender.Masculine to "o", Gender.Feminine to "a"),
                pronouns = listOf("eu", "tu", "ele/ela", "nós", "vós", "eles/elas"),
                conjugate = null,
            )
        val generic = LanguageProfile("", "Other", null, emptyList(), null)

        val all = listOf(german, spanish, french, italian, portuguese)

        fun forCode(code: String?): LanguageProfile = all.firstOrNull { it.code == code?.lowercase()?.take(2) } ?: generic
    }
}

/**
 * Regular German present tense: stem + e/st/t/en/t/en, with an -e- inserted
 * after d/t/m/n stems (arbeiten → arbeitest) and -s stems merging (reisen → reist).
 * Returns null for a word that doesn't look like an infinitive.
 */
fun conjugateGerman(infinitive: String): List<String>? {
    val v = infinitive.trim().lowercase()
    val stem =
        when {
            v.endsWith("ern") || v.endsWith("eln") -> v.dropLast(1)
            v.endsWith("en") -> v.dropLast(2)
            else -> return null
        }
    if (stem.isEmpty()) return null
    val needsE =
        stem.endsWith("d") ||
            stem.endsWith("t") ||
            (stem.endsWith("n") && !stem.endsWith("nn") && stem.length > 1 && stem[stem.length - 2] !in "aeiouräöü") ||
            (stem.endsWith("m") && stem.length > 1 && stem[stem.length - 2] !in "aeiouräöülr")
    val sibilant = stem.endsWith("s") || stem.endsWith("ß") || stem.endsWith("z") || stem.endsWith("x")
    val er = if (needsE) "${stem}et" else "${stem}t"
    val du =
        when {
            needsE -> "${stem}est"
            sibilant -> "${stem}t"
            else -> "${stem}st"
        }
    return listOf("${stem}e", du, er, v, er, v)
}

/** Regular Spanish present tense for -ar / -er / -ir verbs. */
fun conjugateSpanish(infinitive: String): List<String>? {
    val v = infinitive.trim().lowercase()
    val stem = v.dropLast(2)
    if (stem.isEmpty()) return null
    return when {
        v.endsWith("ar") -> listOf("o", "as", "a", "amos", "áis", "an").map { stem + it }
        v.endsWith("er") -> listOf("o", "es", "e", "emos", "éis", "en").map { stem + it }
        v.endsWith("ir") -> listOf("o", "es", "e", "imos", "ís", "en").map { stem + it }
        else -> null
    }
}

/** Regular French present tense for -er / -ir / -re verbs. */
fun conjugateFrench(infinitive: String): List<String>? {
    val v = infinitive.trim().lowercase()
    val stem = v.dropLast(2)
    if (stem.isEmpty()) return null
    return when {
        v.endsWith("er") -> listOf("e", "es", "e", "ons", "ez", "ent").map { stem + it }
        v.endsWith("ir") -> listOf("is", "is", "it", "issons", "issez", "issent").map { stem + it }
        v.endsWith("re") -> listOf("s", "s", "", "ons", "ez", "ent").map { stem + it }
        else -> null
    }
}

/** Splits running text into word tokens with their positions, keeping letters (incl. accents) and inner apostrophes/hyphens. */
object Tokenizer {
    data class Token(
        val text: String,
        val start: Int,
        val end: Int,
        val sentence: String,
    )

    private val word = Regex("""[\p{L}\p{M}]+(?:['’\-][\p{L}\p{M}]+)*""")
    private val sentenceEnd = Regex("""(?<=[.!?…])\s+""")

    fun tokenize(text: String): List<Token> {
        val out = mutableListOf<Token>()
        var offset = 0
        for (sentence in text.split(sentenceEnd)) {
            val start = text.indexOf(sentence, offset).takeIf { it >= 0 } ?: offset
            word.findAll(sentence).forEach { m ->
                out += Token(m.value, start + m.range.first, start + m.range.last + 1, sentence.trim())
            }
            offset = start + sentence.length
        }
        return out
    }

    /** Lower-case, accents kept, for matching against a vocabulary. */
    fun key(s: String): String = s.lowercase().trim()
}
