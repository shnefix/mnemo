# Progress

## Done — Phase 1 (core MVP, no AI)

- Gradle scaffold: Kotlin 2.4.21, Compose Multiplatform 1.12.2, SQLDelight 2.4.1,
  navigation-compose, lifecycle, kotlinx-datetime/serialization, ktlint. Android target is
  applied only when an SDK is present, so the project builds on a JDK-only machine.
- OLED dark theme (true black, coral/lime/lavender accents), Bricolage Grotesque + DM Sans.
- Adaptive shell: bottom tab bar under 600 dp (Design C), sidebar at 840 dp+ (Design A).
- Data: decks (nested), note types (Basic, Reversed, Cloze, Type), notes, cards, review log,
  concepts + links + edges. Repositories with Flows.
- Scheduler: FSRS-6 port with learning/relearning steps, fuzz, leech detection; preview of
  all four intervals; retrievability. 17 tests pinned to reference values.
- Review: queue (learning → review → new, per-deck limits), reveal, grade with intervals,
  undo, suspend, bury, edit, keyboard shortcuts, swipe on phone, Type mode with normalized
  exact match and suggested grade (override logged), session summary.
- Today, Decks, Deck settings, Note editor (cloze helper, live card preview), Browse
  (search syntax, sort, bulk actions), Stats (heatmap, retention, forecast, leeches,
  hardest), Graph (manual concepts, links, edges, mastery colouring), Settings (CSV import,
  JSON backup/restore).
- Tests: 35 (common + desktop), including the end-to-end "create deck → 20 cards → review →
  check due dates" flow.

## Next — Phase 2

- `.apkg` import (notes, cards, media, review history), with media rendering.
- Android: file picker (SAF) for import/export; test on a phone.
- Images, audio, LaTeX (KaTeX-equivalent) and syntax-highlighted code in fields.
- Onboarding ("What are you learning?" → starter deck).
- Light theme toggle (OLED dark is the only theme right now).

## Known issues / gaps

- Android build is untested here (no SDK on this machine).
- Graph layout is a simple spring layout, recomputed on load; large graphs (100+ concepts)
  will be slow and crowded.
- Browse re-renders every card's question on each change; fine below ~10k cards.
- Speak mode is disabled (needs AI, Phase 4). Type mode uses exact match only until Phase 4.
- FSRS optimizer button is disabled until Phase 5.
- Mastery per concept uses default scheduler parameters, not per-deck retention.
- Desktop file dialogs use Swing's JFileChooser (functional, not themed).
