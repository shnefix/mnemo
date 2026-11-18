package app.mnemo

import app.mnemo.data.db.DriverFactory
import app.mnemo.di.AppGraph
import app.mnemo.domain.model.DeckSettings
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.model.Rating
import app.mnemo.domain.review.ReviewSession
import app.mnemo.domain.sync.CloudSync
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** A sync is skipped while the fingerprint is unchanged, so every kind of edit must change it. */
class SyncFingerprintTest {
    @Test
    fun everyEditChangesTheFingerprint() =
        runBlocking {
            val g = AppGraph.create(DriverFactory(), dbPath = null, fuzz = null).also { it.seed() }
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            val deck = g.decks.createByPath("A")
            val ids = (0 until 3).map { g.notes.create(deck, basic, mapOf("Front" to "q$it", "Back" to "a"), emptyList()) }
            var last = CloudSync.fingerprint(g)
            assertEquals(last, CloudSync.fingerprint(g), "stable while nothing changes")

            suspend fun changed(what: String) {
                val now = CloudSync.fingerprint(g)
                assertNotEquals(last, now, what)
                last = now
            }
            ReviewSession(g, deck).apply { load() }.answer(Rating.Good, responseMs = 1000)
            changed("a review")
            val card = g.cards.byNote(ids[1]).first()
            g.cards.setSuspended(card.id, true)
            changed("suspending")
            g.cards.bury(card.id, 123L)
            changed("burying")
            g.notes.update(ids[2], deck, mapOf("Front" to "edited", "Back" to "a"), emptyList())
            changed("an edit")
            g.notes.setTags(ids[2], listOf("tag"))
            changed("tags")
            g.decks.rename(deck, "B")
            changed("a rename")
            g.decks.updateSettings(deck, DeckSettings(newPerDay = 3))
            changed("deck settings")
            g.notes.delete(ids[0])
            changed("a deletion")
        }
}
