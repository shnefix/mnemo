# Mnemo

A spaced-repetition app that keeps Anki's proven scheduling and adds AI that creates,
grades and connects your cards. Kotlin + Compose Multiplatform, one codebase for Android
and desktop, local-first SQLite, OLED-dark UI.

**Status: Phase 2 (Anki import + polish).** Decks, four note types, FSRS scheduling with
learning steps, review with undo/keyboard shortcuts/swipe, Browse with search, Today
dashboard, Stats, a manual knowledge graph, `.apkg` import with media and review history,
CSV import, JSON backup, onboarding, OLED dark / light themes. See
[PROGRESS.md](PROGRESS.md) for what's next.

## Run it

Requirements: a JDK (any recent one; Gradle downloads a JDK 21 toolchain for compilation).

```bash
make dev        # desktop app (./gradlew :composeApp:run)
make test       # scheduler, parsers, repositories, end-to-end review flow
make lint       # ktlint
```

The database lives in the platform app-data folder (`~/.local/share/Mnemo/mnemo.db` on
Linux). Set `MNEMO_DB=/path/to/file.db` to use another one.

### Android

The Android target is wired but only builds when an Android SDK is present
(`ANDROID_HOME` or `local.properties` with `sdk.dir=`). Then:

```bash
make android      # ./gradlew :composeApp:assembleDebug
make phone        # fast R8 release build onto the connected phone (app.mnemo)
make phone-test   # on-device tests (./gradlew :composeApp:connectedDebugAndroidTest)
```

The debug build installs as its own app, `app.mnemo.debug` ("Mnemo (test)"), next to the
real `app.mnemo` rather than over it. It has its own database and prefs, no Google sync and
no self-update, so the on-device tests can never read, change or delete the decks in the
real app. Gradle uninstalls it after `make phone-test`.

## Using it

- **Today**: what's due, streak, retention, decks with mastery, 30-day forecast.
- **Built-in decks**: English (B1–B2, with real photos of everyday things in *9. Name it
  (photos)*), English B2 practice, German A0/A1, and **AI & ML**: history, people, landmark papers,
  failures and ethics, confusable pairs, formulas, diagrams (image occlusion), "what's wrong?",
  numbers, code and tools, with its topics as a concept map on the Graph. Photo credits are in
  [CREDITS.md](CREDITS.md); `scripts/seed/` rebuilds the diagrams and photos.
- **Decks**: `New deck` accepts `German::Vocab::A1` to nest. Each deck has FSRS settings
  (desired retention, daily limits, learning steps, leech threshold, review mode, new-card order,
  burying siblings, load balancing and easy days) and can share them through a named preset.
  *Custom study* on a deck page: forgotten cards, review ahead, extra new cards, preview, or a search.
- **Add cards** (`+`): Basic, Basic + Reversed, Cloze (`{{c1::text}}`; select a word and
  press *Cloze*), Type-the-answer, Image occlusion, and your own note types (Settings › Note
  types: fields and `{{Field}}` templates). Fields take light Markdown; the pin keeps a field's
  text for the next note.
- **Review**: `Space` reveal · `1–4` grade · `U` undo · `E` edit · `S` suspend · `B` bury
  · `Ctrl+1–7` flag · `I` card info · `Esc` leave. On a phone, swipe left = Again, right = Good.
- **Browse**: search with `tag:x deck:y is:due is:new is:learn is:review is:suspended
  is:leech "phrase" -excluded`, plus `flag:1 rated:7:1 added:3 edited:2 introduced:1
  prop:ivl>=10 prop:due=1 note:Basic card:2 nid:… cid:…`; multi-select for tag/move/suspend/
  reset/delete/flag/set due date/reposition/find and replace; the tools menu finds duplicates.
- **Graph**: create concepts, link cards, connect concepts (related / prerequisite / part
  of). Node color = mean recall probability of its cards.
- **Settings**: Anki `.apkg` import and export (decks, cards, scheduling, review history, media,
  flags, image occlusion), CSV export,
  CSV import (`front,back,tags`), JSON backup export/restore, theme (OLED dark / light / system).

## Scheduling

FSRS-6 with the published default parameters, ported from the reference implementation
(`open-spaced-repetition/py-fsrs`) in `domain/scheduler/Fsrs.kt` and pinned by
`FsrsTest`. Learning/relearning steps follow py-fsrs semantics. Parameter optimisation
on your own review log arrives in Phase 5.

## Layout

```
composeApp/src/commonMain/kotlin/app/mnemo/
  domain/     models, scheduler (FSRS + steps), cloze parser, card generator, search, review session
  data/       SQLDelight mappers, repositories, CSV import, JSON backup
  ui/         theme, adaptive shell (phone tab bar / desktop sidebar), screens
  di/         AppGraph — hand-wired dependencies
composeApp/src/commonMain/sqldelight/   schema + queries
composeApp/src/commonTest, desktopTest/ tests
```

## Database migrations

The schema is `composeApp/src/commonMain/sqldelight/**/*.sq`; `databases/<n>.db` are
snapshots of every released schema version and `verifyMigrations` runs against them.
To change the schema: edit the `.sq` files, add `src/commonMain/sqldelight/<n>.sqm`
with the `ALTER`/`CREATE` statements that take version `n` to `n+1`, then run
`./gradlew :composeApp:generateCommonMainMnemoDatabaseSchema` to write the new snapshot.
Both drivers apply pending migrations on open.

## AI

Nothing in Phase 1 calls an AI provider. `.env.example` lists the keys later phases read;
without a key every AI feature stays hidden or disabled with a note.
