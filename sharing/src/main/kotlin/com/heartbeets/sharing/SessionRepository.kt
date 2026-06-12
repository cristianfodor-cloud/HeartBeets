package com.heartbeets.sharing

import android.content.Context
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

/**
 * Manages HeartCodes (multiple per user) and live status via Firebase RTDB.
 *
 * RTDB structure: /heartbeats/{code} → [HeartbeatLive]
 * Local storage: list of HeartCode (code, name, enabled)
 */
class HeartbeatRepository(private val context: Context) {

    private val db = FirebaseDatabase.getInstance()
    private val prefs = context.getSharedPreferences("heartbeets_sharing", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_HEART_CODES = "heart_codes" // code|name|enabled,...
        private const val KEY_FRIENDS = "friends" // code|name,...
    }

    // --- HeartCodes (user's own codes) ---

    fun getHeartCodes(): List<HeartCode> {
        val raw = prefs.getString(KEY_HEART_CODES, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { entry ->
            val parts = entry.split("|", limit = 3)
            if (parts.size == 3) {
                HeartCode(code = parts[0], name = parts[1], enabled = parts[2] == "1")
            } else null
        }
    }

    /**
     * Generate a new HeartCode with the given name.
     */
    fun createHeartCode(name: String): HeartCode {
        val code = generateCode()
        val heartCode = HeartCode(code = code, name = name, enabled = true)
        val codes = getHeartCodes().toMutableList()
        codes.add(heartCode)
        saveHeartCodes(codes)
        return heartCode
    }

    /**
     * Toggle a HeartCode enabled/disabled.
     */
    fun setHeartCodeEnabled(code: String, enabled: Boolean) {
        val codes = getHeartCodes().toMutableList()
        val idx = codes.indexOfFirst { it.code == code }
        if (idx >= 0) {
            codes[idx] = codes[idx].copy(enabled = enabled)
            saveHeartCodes(codes)
        }
    }

    /**
     * Delete a HeartCode locally (instant). Returns immediately.
     */
    fun deleteHeartCodeLocal(code: String) {
        val codes = getHeartCodes().toMutableList()
        codes.removeAll { it.code == code }
        saveHeartCodes(codes)
    }

    /**
     * Remove a HeartCode from RTDB (best-effort network call).
     */
    suspend fun deleteHeartCodeRemote(code: String) {
        try {
            db.getReference("heartbeats/$code").removeValue().await()
        } catch (_: Exception) { /* best-effort */ }
    }

    private fun saveHeartCodes(codes: List<HeartCode>) {
        val raw = codes.joinToString(",") { "${it.code}|${it.name}|${if (it.enabled) "1" else "0"}" }
        prefs.edit().putString(KEY_HEART_CODES, raw).apply()
    }

    /**
     * Get only the enabled codes.
     */
    fun getEnabledCodes(): List<HeartCode> = getHeartCodes().filter { it.enabled }

    // --- Live status (broadcasts to all enabled codes) ---

    /**
     * Go live on all enabled codes.
     */
    suspend fun goLive(profileId: String) {
        val enabledCodes = getEnabledCodes()
        val data = HeartbeatLive(
            bpm = 0,
            updatedAt = System.currentTimeMillis(),
            status = HeartbeatLive.STATUS_LIVE,
            profileId = profileId,
        )
        for (hc in enabledCodes) {
            db.getReference("heartbeats/${hc.code}").setValue(data).await()
        }
    }

    /**
     * Go offline on all enabled codes.
     */
    suspend fun goOffline() {
        val enabledCodes = getEnabledCodes()
        val updates = mapOf<String, Any>(
            "status" to HeartbeatLive.STATUS_OFFLINE,
            "updatedAt" to System.currentTimeMillis(),
        )
        for (hc in enabledCodes) {
            db.getReference("heartbeats/${hc.code}").updateChildren(updates).await()
        }
    }

    /**
     * Push BPM update to all enabled codes while live.
     */
    suspend fun pushBpm(bpm: Int) {
        val enabledCodes = getEnabledCodes()
        val updates = mapOf<String, Any>(
            "bpm" to bpm,
            "updatedAt" to System.currentTimeMillis(),
        )
        for (hc in enabledCodes) {
            db.getReference("heartbeats/${hc.code}").updateChildren(updates).await()
        }
    }

    // --- Observing (receiver side) ---

    /**
     * Observe a friend's heartbeat live data.
     */
    fun observe(code: String): Flow<HeartbeatLive?> = callbackFlow {
        val ref = db.getReference("heartbeats/$code")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val data = snapshot.getValue(HeartbeatLive::class.java)
                trySend(data)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // --- Friends (local storage) ---

    fun getFriends(): List<Friend> {
        val raw = prefs.getString(KEY_FRIENDS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { entry ->
            val parts = entry.split("|", limit = 2)
            if (parts.size == 2) Friend(code = parts[0], name = parts[1]) else null
        }
    }

    fun addFriend(code: String, name: String) {
        val friends = getFriends().toMutableList()
        if (friends.any { it.code == code }) return
        friends.add(Friend(code = code, name = name))
        saveFriends(friends)
    }

    fun removeFriend(code: String) {
        val friends = getFriends().toMutableList()
        friends.removeAll { it.code == code }
        saveFriends(friends)
    }

    private fun saveFriends(friends: List<Friend>) {
        val raw = friends.joinToString(",") { "${it.code}|${it.name}" }
        prefs.edit().putString(KEY_FRIENDS, raw).apply()
    }

    /**
     * Generate a 10-character code from a safe alphabet (no O/0/I/1 confusion).
     * 32^10 ≈ 1.1 quadrillion combinations.
     */
    private fun generateCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..10).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}
