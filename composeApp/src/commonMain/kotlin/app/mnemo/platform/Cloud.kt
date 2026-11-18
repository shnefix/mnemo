package app.mnemo.platform

/**
 * The online account (Google sign-in + Firestore on Android). Everything is stored under
 * `users/{uid}/…`, readable only by that user. Desktop has no account: [supported] is false.
 */
expect object Cloud {
    val supported: Boolean

    /** Email of the signed-in account, or null. */
    fun userEmail(): String?

    suspend fun signIn(): Result<String>

    suspend fun signOut()

    /** Hash of the snapshot in the cloud, or null when there is none. */
    suspend fun remoteHash(): String?

    /** The whole-collection snapshot (a JsonBackup export) and its hash. */
    suspend fun downloadSnapshot(): Pair<String, String>?

    suspend fun uploadSnapshot(
        json: String,
        hash: String,
    )

    /**
     * Daily copies of the snapshot at `users/{uid}/history/{day}` (day = "yyyymmdd"), so a
     * deletion that synced to every phone can still be undone. Newest first.
     */
    suspend fun listHistory(): List<String>

    suspend fun saveHistory(
        day: String,
        json: String,
    )

    suspend fun loadHistory(day: String): String?

    suspend fun deleteHistory(day: String)

    /** Small JSON documents (daily quiz, answers) at `users/{uid}/{collection}/{id}`. */
    suspend fun readDoc(
        collection: String,
        id: String,
    ): String?

    suspend fun writeDoc(
        collection: String,
        id: String,
        json: String,
    )

    fun hash(text: String): String
}
