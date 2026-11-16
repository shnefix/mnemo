# Progress

## Done — Phase 2 (Anki import + polish)

- **`.apkg` import** (`data/io/ApkgImporter.kt`): legacy `collection.anki2`/`anki21` (JSON note
  types/decks in `col`) and 2.1.50+ `collection.anki21b` (zstd, `notetypes`/`fields`/`decks`
  tables). Imports nested decks, notes (HTML → Markdown), cards with scheduling mapped onto
  FSRS (stability ≈ interval, difficulty from ease), suspended/buried flags, the full
  review log, and media (JSON and protobuf media maps). Re-import skips notes by Anki GUID.
- **Media in cards**: `![](file)` images and `[sound:file]` clips render inline from the
  app's media folder (`ui/components/Media.kt`); audio plays with the platform player.
- **Onboarding**: first launch asks "What are you learning?" and creates a starter deck
  (German, Spanish, Japanese, Medicine, Biochemistry, Programming, or a custom name).
- **Theme**: OLED dark / Light / System, persisted in `prefs.json`. Colors are a swappable
  palette (`ui/theme/Color.kt`).
- **Files**: a composable file picker (`rememberFilePicker`) with Swing on desktop and the
  Storage Access Framework on Android; byte-level, so `.apkg` works on both.
- Tests: 38, including a synthetic `.apkg` round trip (`ApkgImportTest`) and the HTML
  conversion. Screenshot test now also renders onboarding and the light theme.

## Done — Phase 1 (core MVP, no AI)

- Compose Multiplatform scaffold, OLED theme, adaptive shell (phone tab bar / desktop sidebar).
- SQLDelight schema; FSRS-6 scheduler with learning steps, fuzz, leech detection (17 pinned tests).
- Review with undo/shortcuts/swipe/Type mode; Today, Decks, Deck settings, Editor, Browse,
  Stats, Graph (manual concepts), Settings with CSV import and JSON backup.

## Next — Phase 3 (AI generation)

- `llm/` provider layer (Anthropic, OpenAI, Ollama) configured from `.env`, prompts as files.
- Generate-from-source: text, PDF (text extraction), URL; accept/edit screen; dedup via embeddings.

## Known issues / gaps

- Android build untested here (no SDK on this machine).
- `.apkg` import maps note types by shape: Basic-like types with more than two fields fold the
  extra fields into Back with their names; custom templates (CSS, conditional sections) are
  not reproduced. Scheduler values for learning cards are approximate.
- LaTeX and syntax-highlighted code in fields are still plain text (markdown code blocks only).
- Desktop audio opens the system player rather than playing inline.
- Graph layout is a simple spring layout; large graphs get crowded.
- Speak mode disabled (Phase 4); FSRS optimizer disabled (Phase 5).
