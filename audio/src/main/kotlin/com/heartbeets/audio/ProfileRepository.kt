package com.heartbeets.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Manages heartbeat profiles: preset (hardcoded) and user-created (persisted
 * as JSON in app-internal storage).
 */
class ProfileRepository(context: Context) {

    private val storageFile = File(context.filesDir, "heartbeat_profiles.json")

    /** Hardcoded preset profiles shipped with the app. */
    fun getPresets(): List<HeartbeatProfile> = PRESETS

    /** Returns all profiles (presets + user-created). */
    suspend fun getAll(): List<HeartbeatProfile> = PRESETS + loadUserProfiles()

    /** Returns only user-created profiles. */
    suspend fun getUserProfiles(): List<HeartbeatProfile> = loadUserProfiles()

    /** Save a user-created profile. If a profile with the same ID exists, it's replaced. */
    suspend fun save(profile: HeartbeatProfile) {
        val profiles = loadUserProfiles().toMutableList()
        profiles.removeAll { it.id == profile.id }
        profiles.add(profile)
        persist(profiles)
    }

    /** Delete a user-created profile by ID. Preset profiles cannot be deleted. */
    suspend fun delete(id: String) {
        val profiles = loadUserProfiles().toMutableList()
        profiles.removeAll { it.id == id }
        persist(profiles)
    }

    /** Generate a new unique ID for a user profile. */
    fun newId(): String = UUID.randomUUID().toString()

    // --- Persistence (JSON) ---

    private suspend fun loadUserProfiles(): List<HeartbeatProfile> =
        withContext(Dispatchers.IO) {
            if (!storageFile.exists()) return@withContext emptyList()
            try {
                val json = storageFile.readText()
                val array = JSONArray(json)
                (0 until array.length()).map { i -> fromJson(array.getJSONObject(i)) }
            } catch (_: Exception) {
                emptyList()
            }
        }

    private suspend fun persist(profiles: List<HeartbeatProfile>) =
        withContext(Dispatchers.IO) {
            val array = JSONArray()
            profiles.forEach { array.put(toJson(it)) }
            storageFile.writeText(array.toString(2))
        }

    private fun toJson(profile: HeartbeatProfile): JSONObject = JSONObject().apply {
        put("id", profile.id)
        put("name", profile.name)
        put("description", profile.description)
        put("isPreset", profile.isPreset)
        put("anchorMode", profile.anchorMode.name)
        if (profile.startBpm != null) put("startBpm", profile.startBpm)
        put("stages", JSONArray().apply {
            profile.stages.forEach { stage ->
                put(JSONObject().apply {
                    put("targetOffsetBpm", stage.targetOffsetBpm)
                    put("durationSec", stage.durationSec)
                    put("curve", stage.curve.name)
                })
            }
        })
    }

    private fun fromJson(obj: JSONObject): HeartbeatProfile {
        val stagesArray = obj.getJSONArray("stages")
        val stages = (0 until stagesArray.length()).map { i ->
            val s = stagesArray.getJSONObject(i)
            ProfileStage(
                targetOffsetBpm = s.getInt("targetOffsetBpm"),
                durationSec = s.getInt("durationSec"),
                curve = try {
                    EasingCurve.valueOf(s.getString("curve"))
                } catch (_: Exception) {
                    EasingCurve.LINEAR
                }
            )
        }
        return HeartbeatProfile(
            id = obj.getString("id"),
            name = obj.getString("name"),
            description = obj.optString("description", ""),
            stages = stages,
            isPreset = obj.optBoolean("isPreset", false),
            anchorMode = try {
                ProfileAnchorMode.valueOf(obj.optString("anchorMode", "RELATIVE"))
            } catch (_: Exception) {
                ProfileAnchorMode.RELATIVE
            },
            startBpm = if (obj.has("startBpm")) obj.getInt("startBpm") else null
        )
    }

    companion object {
        private val PRESETS = listOf(
            HeartbeatProfile(
                id = "preset-wind-down",
                name = "Wind Down",
                description = "Gradually slows the beat to guide you toward relaxation.",
                isPreset = true,
                stages = listOf(
                    ProfileStage(targetOffsetBpm = 0, durationSec = 30),
                    ProfileStage(targetOffsetBpm = -5, durationSec = 60, curve = EasingCurve.EASE_OUT),
                    ProfileStage(targetOffsetBpm = -10, durationSec = 90, curve = EasingCurve.EASE_OUT),
                    ProfileStage(targetOffsetBpm = -15, durationSec = 120, curve = EasingCurve.EASE_OUT),
                )
            ),
            HeartbeatProfile(
                id = "preset-pre-workout",
                name = "Pre-Workout Ramp",
                description = "Gradually increases the beat to energise before exercise.",
                isPreset = true,
                stages = listOf(
                    ProfileStage(targetOffsetBpm = 0, durationSec = 20),
                    ProfileStage(targetOffsetBpm = 10, durationSec = 45, curve = EasingCurve.EASE_IN),
                    ProfileStage(targetOffsetBpm = 20, durationSec = 45, curve = EasingCurve.EASE_IN),
                )
            ),
            HeartbeatProfile(
                id = "preset-steady-hold",
                name = "Steady Hold",
                description = "Maintains your current heart rate cadence for 5 minutes.",
                isPreset = true,
                stages = listOf(
                    ProfileStage(targetOffsetBpm = 0, durationSec = 300),
                )
            ),
        )
    }
}
