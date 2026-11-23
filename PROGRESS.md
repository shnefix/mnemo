# Progress

## Done — Smoothness and safety (18 Nov 2026)

- **Speed**: Today and Decks compute due counts, card counts and mastery for every deck from a
  few grouped queries (`DailyQueue.snapshot`, `domain/stats/DeckOverview.kt`); weak spots load all
  cards in one query; Today shows top-level decks only; Browse, Today and Decks recompute only while
  shown; launch housekeeping waits 1.5 s and runs off the main thread; cloud sync never runs on the
  main thread; SQLite uses WAL; Stats loads text only for the cards it names; a startup profile
  (`androidMain/baseline-prof.txt`) pre-compiles the app at install.
- **Deck trash** (`data/io/DeckTrash.kt`): deleting a deck saves it (sub-decks, cards, history) to
  `trash/` for 30 days; Undo on the Decks tab, restore from Settings › Recently deleted.
- **Safe restores** (`JsonBackup.restoreDeleted`): backups, the trash and cloud copies bring back
  missing notes without duplicating or reverting what is still there.
- **Backups outside the app**: daily backups and exported files also go to Download/Mnemo; signed-in
  phones keep 7 daily cloud copies (`users/{uid}/history/{day}`) under Settings › Cloud copies.
- **Sync skips** the export and upload when nothing changed on either side (`syncFingerprint`).
- **Packs** ("Vocabulary 07") show as tiles inside their section on the Decks tab (`ui/decks/Packs.kt`).
- **Speak mode**: a mock interview — the question is read out, you answer out loud (system speech
  recognizer), then compare with the model answer.
- Removed the share button from cards.

## Done — Hardening (Nov 2026)

- **Built-in English (B1–B2) deck** (`data/seed/`): installed once per install (flag
  `seed.english`), ~500 notes in one transaction, split into packs of 10 under five sections.
- **Three examples per card** (`data/seed/EnglishDeckExtras.kt`): every English card shows three
  examples (situations: three other ways to say it). The seed is versioned (`EnglishDeck.VERSION`,
  pref `seed.english`); a newer version updates installed cards in place, keeping their reviews,
  unless the user edited them.
- **English B2 practice deck** (`data/seed/EnglishB2Deck.kt`, content in `EnglishB2Content.kt`):
  a second built-in deck with its own daily mix (pref `seed.english.b2`): My mistakes (empty, first
  in the mix), 135 phrasal verbs in 8 topic sub-decks
  (about 150 with the B1–B2 deck, none repeated), 53 word families as word-formation clozes, 39
  linking words (joined to the grammar topic "Linking words") and 60
  dictation sentences. Version 2 took out Collocations and Say it another way (older installs trash
  them on update, kept 30 days in the deck trash). Both English decks install through `SeedInstaller`.
- **Things to know** (English deck v3): the picture section is "4. Things to know" — the useful
  things and actions only (the rarely needed ones were removed, new everyday ones added). An update
  renames the old section, moves cards to their packs and deletes seed cards that were dropped
  (edited ones are kept). `SeedInstaller.adopt` / `alwaysCreate` make that possible.
- **German (A0) deck** (`data/seed/GermanA0Deck.kt`, content in `GermanA0Content.kt`, pref
  `seed.german.a0`, version 2): "1. Name it" is nested into numbered categories (Things first):
  pictures to name with der/die/das and the plural, verbs with the ich-form, colours and numbers
  1–10; "2. What to say" has 49 first phrases for real situations; "3. Type it (articles)" is a
  Type-mode drill where you type der/die/das for each noun (the answer is only the article, so a
  wrong one is never "close"). Every card has a simple example. Version 1 installs are moved over.
- **Taken out** (English deck v6): "Numbers, time & money" and the first six "Things" packs
  (Things 01–06; the rest keep their numbers, starting at 07). Older installs move them to the deck
  trash on update (`EnglishDeck.removeTakenOut`), new installs never get them.
- **More sections** (English deck v7, B2 deck v3, new German A1 deck v1; content in
  `EnglishMoreContent.kt` and `GermanA1Content.kt`): "6. Confusing pairs" (48 gap fills such as
  say/tell, borrow/lend) and "8. Pronunciation traps" (54 words with how they sound) in the B1–B2
  deck; "2. Work English" (44 situations) and "6. Everyday dialogues" (60 lines to listen to and
  type) in the B2 deck; and "German (A1)": 43 verbs (ich/du/er), 31 word-order puzzles, 30
  questions and a Type-mode drill for the du-form. Older installs get the new sections on update.
- **Keep the conversation going** (English deck v5, `EnglishTalkContent.kt`): "7. Keep the
  conversation going" — 78 situation cards in Speak mode (the question is asked aloud, you answer
  aloud): reactions, follow-up questions, polite & softening phrases, buying time & fixing what you
  said, natural fillers. Each card has the model phrase and two other ways. Added to older installs
  on update.
- **Dictation cards**: a `[listen: …]` marker in a card's front is read aloud when the card
  appears (and with "Listen again") instead of being shown; such cards always get the type box.
  Speech uses the card's own deck language, so it also works from "All decks".
- **Concept mastery** (`WeakSpots.mastery`): the mean recall of the concept's *studied* cards;
  never-reviewed cards count as coverage, not as 0%. The graph shows "new" for concepts not
  started and a ring for the share studied; weak spots, Today and Stats only list started concepts.
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
  code is the commit count.
- **On-device tests run in their own install**: the debug build type has
  `applicationIdSuffix = ".debug"` (label "Mnemo (test)"), so `make phone-test` installs
  `app.mnemo.debug` beside the real `app.mnemo` with a separate database, and Gradle
  uninstalls it afterwards. Firebase settings are only added to release (no sync in the test
  build, so it cannot push test decks to the account), and `Updater.supported` is true only for
  `app.mnemo` (the release APK must not install over the test build).
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
