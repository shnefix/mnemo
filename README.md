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
make android    # ./gradlew :composeApp:assembleDebug
```

## Using it

- **Today**: what's due, streak, retention, decks with mastery, 30-day forecast.
- **Decks**: `New deck` accepts `German::Vocab::A1` to nest. Each deck has FSRS settings
  (desired retention, daily limits, learning steps, leech threshold, review mode).
- **Add cards** (`+`): Basic, Basic + Reversed, Cloze (`{{c1::text}}`; select a word and
  press *Cloze*), Type-the-answer. Fields take light Markdown.
- **Review**: `Space` reveal · `1–4` grade · `U` undo · `E` edit · `S` suspend · `B` bury
  · `Esc` leave. On a phone, swipe left = Again, right = Good.
- **Browse**: search with `tag:x deck:y is:due is:new is:learn is:review is:suspended
  is:leech "phrase" -excluded`; multi-select for tag/move/suspend/reset/delete.
- **Graph**: create concepts, link cards, connect concepts (related / prerequisite / part
  of). Node color = mean recall probability of its cards.
- **Settings**: Anki `.apkg` import (decks, cards, scheduling, review history, media),
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
