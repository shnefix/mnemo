package app.mnemo

import app.mnemo.data.db.DriverFactory
import app.mnemo.data.seed.EnglishDeck
import app.mnemo.data.seed.EnglishPhotoContent
import app.mnemo.data.seed.EnglishPhotoCredits
import app.mnemo.di.AppGraph
import app.mnemo.platform.MediaFiles
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnglishPhotosTest {
    private val all =
        listOf(
            EnglishPhotoContent.KITCHEN,
            EnglishPhotoContent.BATHROOM,
            EnglishPhotoContent.HOUSE,
            EnglishPhotoContent.TOOLS,
            EnglishPhotoContent.STREET,
            EnglishPhotoContent.SHOP,
            EnglishPhotoContent.CLOTHES,
        ).flatMap { EnglishDeck.rows(it) }

    private val resources = File("src/commonMain/composeResources/files/seed/en")

    @Test
    fun everyRowIsWellFormedAndEveryPhotoIsCredited() {
        all.forEach { r ->
            assertEquals(5, r.size, "bad row $r")
            assertTrue(r.all { it.isNotBlank() }, "empty field in $r")
        }
        assertEquals(all.size, all.map { it[0] }.toSet().size, "duplicate photo key")
        assertEquals(all.size, all.map { it[1].lowercase() }.toSet().size, "duplicate word")
        // Not a word the older picture cards already teach.
        val pictures = (EnglishDeck.thingCards() + EnglishDeck.actionCards()).map { it.fields.getValue("Back").substringBefore("\n") }
        all.forEach { r -> assertTrue("**${r[1]}**" !in pictures, "already a picture card: ${r[1]}") }
        // Every credited photo ships, and every shipped photo is credited.
        val shipped =
            resources
                .listFiles()
                .orEmpty()
                .filter { it.extension == "webp" }
                .map { it.nameWithoutExtension }
                .toSet()
        assertEquals(EnglishPhotoCredits.byKey.keys, shipped)
        assertEquals(all.map { it[0] }.toSet(), EnglishPhotoCredits.byKey.keys, "every row has its photo")
        EnglishPhotoCredits.byKey.values.forEach { assertTrue(Regex("CC0|Public domain|PD|CC BY").containsMatchIn(it), "licence? $it") }
    }

    @Test
    fun photosArriveOnUpdateWithTheirPictures() =
        runBlocking {
            val g = AppGraph.create(DriverFactory(), dbPath = null, fuzz = null)
            g.seed()
            // An install from before the photos (version 7) gets them when it updates.
            EnglishDeck.install(g)
            val section = g.decks.all().first { it.name == EnglishDeck.photos.name }
            val ids = g.decks.subtreeIds(section.id)
            ids.forEach { id -> g.notes.byDeck(id).forEach { g.notes.delete(it.id) } }
            assertEquals(EnglishPhotoCredits.byKey.size, EnglishDeck.install(g, fresh = false, from = 7))
            val notes = g.decks.subtreeIds(section.id).flatMap { g.notes.byDeck(it) }
            assertEquals(EnglishPhotoCredits.byKey.size, notes.size)
            notes.forEach { n ->
                val image = Regex("""!\[]\(([^)]+)\)""").find(n.fields.getValue("Front"))!!.groupValues[1]
                assertTrue(MediaFiles.exists(image), "missing $image")
                assertTrue(n.fields.getValue("Back").contains("*Photo: "), "no credit on ${n.fields["Front"]}")
            }
            val categories =
                g.decks
                    .all()
                    .filter { it.parentId == section.id }
                    .map { it.name }
            assertEquals(7, categories.size, "$categories")
            // Updating again adds nothing.
            assertEquals(0, EnglishDeck.install(g, fresh = false, from = 8))
        }
}
