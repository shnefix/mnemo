package app.mnemo.platform

import java.security.MessageDigest

actual object Cloud {
    actual val supported: Boolean = false

    actual fun userEmail(): String? = null

    actual suspend fun signIn(): Result<String> = Result.failure(UnsupportedOperationException("Sign-in is on the phone app"))

    actual suspend fun signOut() {}

    actual suspend fun remoteHash(): String? = null

    actual suspend fun downloadSnapshot(): Pair<String, String>? = null

    actual suspend fun uploadSnapshot(
        json: String,
        hash: String,
    ) {}

    actual suspend fun readDoc(
        collection: String,
        id: String,
    ): String? = null

    actual suspend fun writeDoc(
        collection: String,
        id: String,
        json: String,
    ) {}

    actual suspend fun listHistory(): List<String> = emptyList()

    actual suspend fun saveHistory(
        day: String,
        json: String,
    ) {}

    actual suspend fun loadHistory(day: String): String? = null

    actual suspend fun deleteHistory(day: String) {}

    actual fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
