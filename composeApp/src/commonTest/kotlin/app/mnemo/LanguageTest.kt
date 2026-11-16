package app.mnemo

import app.mnemo.domain.language.Gender
import app.mnemo.domain.language.LanguageProfile
import app.mnemo.domain.language.Tokenizer
import app.mnemo.domain.language.conjugateGerman
import app.mnemo.domain.language.conjugateSpanish
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LanguageTest {
    @Test
    fun germanRegularVerbs() {
        assertEquals(listOf("mache", "machst", "macht", "machen", "macht", "machen"), conjugateGerman("machen"))
        assertEquals(listOf("arbeite", "arbeitest", "arbeitet", "arbeiten", "arbeitet", "arbeiten"), conjugateGerman("arbeiten"))
        assertEquals(listOf("reise", "reist", "reist", "reisen", "reist", "reisen"), conjugateGerman("reisen"))
        assertEquals(listOf("öffne", "öffnest", "öffnet", "öffnen", "öffnet", "öffnen"), conjugateGerman("öffnen"))
        assertEquals("wandere", conjugateGerman("wandern")!![0])
        assertNull(conjugateGerman("Zug"))
    }

    @Test
    fun spanishRegularVerbs() {
        assertEquals(listOf("hablo", "hablas", "habla", "hablamos", "habláis", "hablan"), conjugateSpanish("hablar"))
        assertEquals(listOf("como", "comes", "come", "comemos", "coméis", "comen"), conjugateSpanish("comer"))
        assertEquals(listOf("vivo", "vives", "vive", "vivimos", "vivís", "viven"), conjugateSpanish("vivir"))
    }

    @Test
    fun articlesAndGender() {
        val de = LanguageProfile.german
        assertEquals(Gender.Masculine to "Zug", de.splitArticle("der Zug"))
        assertEquals(null to "Zug", de.splitArticle("Zug"))
        assertEquals(Gender.Feminine, Gender.parse("die"))
        assertEquals(Gender.Neuter, Gender.parse("n"))
        assertEquals("de", LanguageProfile.forCode("de-DE").code)
        assertEquals("Other", LanguageProfile.forCode("xx").name)
    }

    @Test
    fun tokenizerKeepsSentencesAndAccents() {
        val tokens = Tokenizer.tokenize("Wegen des Streiks fallen Züge aus. Pendler müssen warten!")
        assertEquals(listOf("Wegen", "des", "Streiks", "fallen", "Züge", "aus", "Pendler", "müssen", "warten"), tokens.map { it.text })
        assertEquals("Wegen des Streiks fallen Züge aus.", tokens[0].sentence)
        assertEquals("Pendler müssen warten!", tokens.last().sentence)
        assertEquals("Züge", "Wegen des Streiks fallen Züge aus. Pendler müssen warten!".substring(tokens[4].start, tokens[4].end))
    }
}
