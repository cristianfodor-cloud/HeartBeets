package com.heartbeets.app.billing

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Manages authentication (anonymous + optional Google Sign-In) and
 * server-side trial/user data persistence.
 */
class AuthManager(private val context: Context) {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _user = MutableStateFlow(auth.currentUser)
    val user: StateFlow<FirebaseUser?> = _user.asStateFlow()

    private val _isLinked = MutableStateFlow(isGoogleLinked())
    val isLinked: StateFlow<Boolean> = _isLinked.asStateFlow()

    val uid: String? get() = auth.currentUser?.uid

    /**
     * Sign in anonymously if not already signed in. Silent, no UI.
     */
    suspend fun ensureSignedIn() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
        _user.value = auth.currentUser
        _isLinked.value = isGoogleLinked()
    }

    /**
     * Get the Google Sign-In intent to launch.
     */
    fun getGoogleSignInIntent(): Intent {
        val webClientId = getWebClientId()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, gso).signInIntent
    }

    /**
     * Handle the result from Google Sign-In and link to the current anonymous account.
     */
    suspend fun handleGoogleSignInResult(data: Intent?): Boolean {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        val account = try { task.result } catch (_: Exception) { return false }
        val idToken = account?.idToken ?: return false

        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val currentUser = auth.currentUser

        return try {
            if (currentUser != null && currentUser.isAnonymous) {
                // Link anonymous account to Google — preserves UID
                currentUser.linkWithCredential(credential).await()
            } else if (currentUser == null) {
                // Sign in with Google directly
                auth.signInWithCredential(credential).await()
            } else {
                // Already linked, re-auth
                currentUser.reauthenticate(credential).await()
            }
            _user.value = auth.currentUser
            _isLinked.value = true
            true
        } catch (_: Exception) {
            // If link fails (e.g. Google account already used), sign in with Google
            // and merge data manually
            try {
                auth.signInWithCredential(credential).await()
                _user.value = auth.currentUser
                _isLinked.value = true
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    // --- Server-side trial ---

    /**
     * Get or create the trial start timestamp from Firestore.
     * Returns the trial start in epoch millis.
     */
    suspend fun getOrCreateTrialStart(): Long {
        val uid = uid ?: return System.currentTimeMillis()
        val doc = firestore.collection("users").document(uid)
        val snapshot = doc.get().await()

        return if (snapshot.exists() && snapshot.contains("trialStart")) {
            snapshot.getLong("trialStart")!!
        } else {
            val now = System.currentTimeMillis()
            doc.set(mapOf("trialStart" to now), com.google.firebase.firestore.SetOptions.merge()).await()
            now
        }
    }

    // --- Backup codes & friends ---

    /**
     * Back up HeartCodes and friends to Firestore under the user's UID.
     */
    suspend fun backupUserData(codes: List<String>, friends: List<String>) {
        val uid = uid ?: return
        val doc = firestore.collection("users").document(uid)
        doc.set(
            mapOf(
                "heartCodes" to codes,
                "friends" to friends,
            ),
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()
    }

    /**
     * Restore HeartCodes and friends from Firestore.
     * Returns (codes, friends) raw strings or nulls if no backup.
     */
    suspend fun restoreUserData(): Pair<List<String>?, List<String>?> {
        val uid = uid ?: return null to null
        val doc = firestore.collection("users").document(uid).get().await()
        if (!doc.exists()) return null to null

        @Suppress("UNCHECKED_CAST")
        val codes = doc.get("heartCodes") as? List<String>
        @Suppress("UNCHECKED_CAST")
        val friends = doc.get("friends") as? List<String>
        return codes to friends
    }

    private fun isGoogleLinked(): Boolean {
        return auth.currentUser?.providerData?.any { it.providerId == "google.com" } == true
    }

    private fun getWebClientId(): String {
        // Read from google-services.json via resources
        val resId = context.resources.getIdentifier(
            "default_web_client_id", "string", context.packageName
        )
        return if (resId != 0) context.getString(resId) else ""
    }
}
