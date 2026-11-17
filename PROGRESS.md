# Progress

## Done — Hardening (Nov 2026)

- **Built-in English (B1–B2) deck** (`data/seed/`): installed once per install (flag
  `seed.english`), ~500 notes in one transaction, split into packs of 10 under five sections.
- **Three examples per card** (`data/seed/EnglishDeckExtras.kt`): every English card shows three
  examples (situations: three other ways to say it). The seed is versioned (`EnglishDeck.VERSION`,
  pref `seed.english`); a newer version updates installed cards in place, keeping their reviews,
  unless the user edited them.
- **Graph for all decks** (`domain/graph/DeckGraph.kt`): each top-level deck and sub-deck is a
  concept (packs roll up), every card is linked to its decks' concepts, and the English grammar
  cards are grouped into 17 topics with "learn first" links. Synced at launch and on opening the
  Graph; deleted deck concepts stay deleted (`graph.hidden`). Stable force layout; crowded graphs
  label only the biggest concepts and the selection's neighbours.
- **Daily limits** (`domain/review/DailyQueue.kt`): a deck's new-cards-per-day caps its subtree
  and each sub-deck caps its share, Anki-style, so a parent deck gives a mix; "All decks" adds the
  default limit on top. Today, Decks and Review share it, so their counts agree.
- **Deck list**: parents start folded; opened decks are remembered (`decks.expanded`).
- **Release signing**: `keystore.properties` (gitignored) points at a dedicated release key in
  `~/.config/mnemo/release.jks`; without it, or with `make phone SIGN=debug`
  (`-Pmnemo.signing=debug`), release uses the debug key. Back the key up. The Android version
  code is the commit count. `make phone-test` runs the on-device tests without uninstalling.
- **Sync**: notes carry a stable `uid` (schema v2, `1.sqm`), and deletions leave tombstones
  (`deleted_note`) that travel in backups, so deleted notes stay deleted and notes stamped with
  the same time (an `.apkg` import) no longer fold together. Old backups still match by deck +
  creation time + fields. Review logs from both devices are unioned. Tombstones older than 180
  days are pruned at launch.
- **Cards**: LaTeX (`$…$`, `$$…$$`, `\(…\)`, `\[…\]`, `[$]…[/$]`) as Unicode math; fenced code is
  syntax-highlighted.
- **Graph**: Neighbours / 2-steps focus around the selected concept; layout spreads large graphs.
- **Accessibility**: labelled 40dp remove buttons in the graph, larger tag remove target, buttons
  and selects grow with the text size instead of clipping.
- **CI**: builds the R8 release APK and uploads it.

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

## Known issues / gaps

- `.apkg` import maps note types by shape: Basic-like types with more than two fields fold the
  extra fields into Back with their names; custom templates (CSS, conditional sections) are
  not reproduced. Scheduler values for learning cards are approximate.
- LaTeX is shown as Unicode math (Greek, operators, sub/superscripts, fractions, roots), not typeset;
  big matrices and aligned environments come out as flat text.
- Desktop audio opens the system player rather than playing inline.
- Graph layout is a spring layout; on large graphs use the Neighbours / 2 steps focus.
