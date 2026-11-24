package app.mnemo.platform

/**
 * Strips diacritics after Unicode canonical decomposition (NFD), so `é` → `e`,
 * `ä` → `a`, and folds the few letters that have no decomposition (`ß` → `ss`,
 * `ø` → `o`). Used to compare typed answers fairly on language decks.
 */
expect fun foldDiacritics(s: String): String

/** SHA-1 of [bytes] (Anki's note checksum). */
expect fun sha1(bytes: ByteArray): ByteArray
