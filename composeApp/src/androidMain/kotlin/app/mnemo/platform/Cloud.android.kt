package app.mnemo.platform

import android.app.Activity
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** The visible activity, needed to show the Google account picker. Set by MainActivity. */
var currentActivity: WeakReference<Activity>? = null

actual object Cloud {
    /** Firestore documents max out at 1 MiB; the gzipped snapshot is split into pieces below that. */
    private const val CHUNK = 700_000

    private fun res(name: String): String? {
        val ctx = appContext
        val id = ctx.resources.getIdentifier(name, "string", ctx.packageName)
        return if (id == 0) null else ctx.getString(id)
    }

    private val app: FirebaseApp? by lazy {
        if (res("google_app_id") == null) null else FirebaseApp.getApps(appContext).firstOrNull() ?: FirebaseApp.initializeApp(appContext)
    }

    actual val supported: Boolean get() = app != null && res("default_web_client_id") != null

    private fun auth(): FirebaseAuth? = app?.let { FirebaseAuth.getInstance(it) }

    private fun db(): FirebaseFirestore = FirebaseFirestore.getInstance(app!!)

    private fun userRef(): DocumentReference {
        val uid = auth()?.currentUser?.uid ?: error("Not signed in")
        return db().collection("users").document(uid)
    }

    actual fun userEmail(): String? = auth()?.currentUser?.email

    actual suspend fun signIn(): Result<String> =
        runCatching {
            val activity = currentActivity?.get() ?: error("App is not open")
            val webClientId = res("default_web_client_id") ?: error("Sync is not set up in this build")
            val request =
                GetCredentialRequest
                    .Builder()
                    .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
                    .build()
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                "Unexpected sign-in result"
            }
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val result = auth()!!.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
            result.user?.email ?: "signed in"
        }

    actual suspend fun signOut() {
        auth()?.signOut()
        runCatching {
            currentActivity?.get()?.let {
                CredentialManager
                    .create(
                        it,
                    ).clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
            }
        }
    }

    private fun meta() = userRef().collection("sync").document("meta")

    actual suspend fun remoteHash(): String? = meta().get(Source.SERVER).await().getString("hash")

    actual suspend fun downloadSnapshot(): Pair<String, String>? {
        val m = meta().get(Source.SERVER).await()
        val hash = m.getString("hash") ?: return null
        val chunks = (m.getLong("chunks") ?: return null).toInt()
        val sb = StringBuilder()
        for (i in 0 until chunks) {
            sb.append(
                userRef()
                    .collection("sync")
                    .document("chunk_$i")
                    .get(Source.SERVER)
                    .await()
                    .getString("data") ?: return null,
            )
        }
        val bytes = Base64.decode(sb.toString(), Base64.NO_WRAP)
        val json = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes().decodeToString() }
        return json to hash
    }

    actual suspend fun uploadSnapshot(
        json: String,
        hash: String,
    ) {
        val gz = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(json.encodeToByteArray()) } }.toByteArray()
        val encoded = Base64.encodeToString(gz, Base64.NO_WRAP)
        val parts = encoded.chunked(CHUNK)
        val batch = db().batch()
        parts.forEachIndexed { i, p -> batch.set(userRef().collection("sync").document("chunk_$i"), mapOf("data" to p)) }
        batch.set(meta(), mapOf("hash" to hash, "chunks" to parts.size, "updatedAt" to System.currentTimeMillis()))
        // A top-level user document, so tools (the daily quiz task) can find the account.
        batch.set(userRef(), mapOf("email" to userEmail(), "updatedAt" to System.currentTimeMillis()))
        batch.commit().await()
    }

    actual suspend fun readDoc(
        collection: String,
        id: String,
    ): String? =
        userRef()
            .collection(collection)
            .document(id)
            .get(Source.SERVER)
            .await()
            .getString("json")

    actual suspend fun writeDoc(
        collection: String,
        id: String,
        json: String,
    ) {
        userRef()
            .collection(collection)
            .document(id)
            .set(mapOf("json" to json, "updatedAt" to System.currentTimeMillis()))
            .await()
    }

    actual fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
