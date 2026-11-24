package app.mnemo.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mnemo.di.AppGraph
import app.mnemo.domain.cards.CardGenerator
import app.mnemo.domain.cards.RenderedCard
import app.mnemo.domain.model.Deck
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.model.NoteType
import app.mnemo.platform.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorUiState(
    val loading: Boolean = true,
    val editingNoteId: Long? = null,
    val decks: List<Deck> = emptyList(),
    val noteTypes: List<NoteType> = emptyList(),
    val deck: Deck? = null,
    val noteType: NoteType? = null,
    val fields: Map<String, String> = emptyMap(),
    val tags: List<String> = emptyList(),
    val savedCount: Int = 0,
    val lastSavedMessage: String? = null,
    val error: String? = null,
    /** Fields pinned to keep their text for the next note (Anki's sticky fields). */
    val sticky: Set<String> = emptySet(),
) {
    val canSave: Boolean get() = deck != null && noteType != null && fields.values.any { it.isNotBlank() } && previews.isNotEmpty()

    /** Cards the current fields would generate. */
    val previews: List<RenderedCard>
        get() {
            val t = noteType ?: return emptyList()
            return CardGenerator.templateIndices(t, fields).map { CardGenerator.render(t, fields, it) }
        }
}

class NoteEditorViewModel(
    private val graph: AppGraph,
    private val noteId: Long?,
    private val initialDeckId: Long?,
) : ViewModel() {
    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val decks = graph.decks.all()
        val types = graph.notes.noteTypes()
        if (noteId != null) {
            val note = graph.notes.byId(noteId)
            if (note != null) {
                _state.value =
                    EditorUiState(
                        loading = false,
                        editingNoteId = noteId,
                        decks = decks,
                        noteTypes = types,
                        deck = decks.firstOrNull { it.id == note.deckId },
                        noteType = types.firstOrNull { it.id == note.noteTypeId },
                        fields = note.fields,
                        tags = note.tags,
                    )
                return
            }
        }
        // The note type used last time, as in Anki.
        val lastType = Prefs.get(Prefs.EDITOR_LAST_TYPE)?.toLongOrNull()
        val type = types.firstOrNull { it.id == lastType } ?: types.firstOrNull { it.kind == NoteKind.Basic } ?: types.firstOrNull()
        _state.value =
            EditorUiState(
                loading = false,
                decks = decks,
                noteTypes = types,
                deck = decks.firstOrNull { it.id == initialDeckId } ?: decks.firstOrNull(),
                noteType = type,
                fields = type?.fields?.associateWith { "" } ?: emptyMap(),
                sticky = type?.let { stickyOf(it) } ?: emptySet(),
            )
    }

    fun selectDeck(deck: Deck) = _state.update { it.copy(deck = deck) }

    fun selectNoteType(type: NoteType) =
        _state.update { s ->
            val fields = type.fields.associateWith { f -> s.fields[f] ?: s.fields.values.elementAtOrNull(type.fields.indexOf(f)) ?: "" }
            if (s.editingNoteId == null) Prefs.set(Prefs.EDITOR_LAST_TYPE, type.id.toString())
            s.copy(noteType = type, fields = fields, sticky = stickyOf(type))
        }

    private fun stickyOf(type: NoteType): Set<String> =
        Prefs
            .get(Prefs.EDITOR_STICKY + type.id)
            ?.split('\u001f')
            ?.filter { it in type.fields }
            ?.toSet()
            .orEmpty()

    /** Pins or unpins [field]: a pinned field keeps its text for the next note. */
    fun toggleSticky(field: String) =
        _state.update { s ->
            val type = s.noteType ?: return@update s
            val next = if (field in s.sticky) s.sticky - field else s.sticky + field
            Prefs.set(Prefs.EDITOR_STICKY + type.id, next.joinToString("\u001f"))
            s.copy(sticky = next)
        }

    fun setField(
        name: String,
        value: String,
    ) = _state.update { it.copy(fields = it.fields + (name to value), lastSavedMessage = null) }

    fun setTags(tags: List<String>) = _state.update { it.copy(tags = tags) }

    /** Saves the note. When [another] is true, keeps the editor open for the next note. */
    fun save(
        another: Boolean,
        onDone: () -> Unit,
    ) {
        val s = _state.value
        val deck = s.deck ?: return
        val type = s.noteType ?: return
        if (!s.canSave) return
        viewModelScope.launch {
            try {
                if (s.editingNoteId != null) {
                    graph.notes.update(s.editingNoteId, deck.id, s.fields, s.tags)
                    onDone()
                } else {
                    graph.notes.create(deck.id, type, s.fields, s.tags)
                    val n = s.previews.size
                    if (another) {
                        _state.update {
                            it.copy(
                                fields = type.fields.associateWith { f -> if (f in it.sticky) it.fields[f].orEmpty() else "" },
                                savedCount = it.savedCount + 1,
                                lastSavedMessage = "Saved · $n card${if (n == 1) "" else "s"} added to ${deck.path}",
                            )
                        }
                    } else {
                        onDone()
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Could not save") }
            }
        }
    }

    fun createDeck(
        path: String,
        then: (Deck) -> Unit,
    ) {
        viewModelScope.launch {
            val id = graph.decks.createByPath(path)
            val decks = graph.decks.all()
            val deck = decks.first { it.id == id }
            _state.update { it.copy(decks = decks, deck = deck) }
            then(deck)
        }
    }
}
