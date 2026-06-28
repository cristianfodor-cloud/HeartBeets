package com.heartbeets.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONException
import java.io.File
import java.util.UUID

/**
 * Persists user-created [SoundPack]s (with their [SynthParams]) to JSON.
 * On load, also registers them into [SoundPackRegistry].
 */
class SoundPackRepository(context: Context) {

    private val storageFile = File(context.filesDir, "user_sound_packs.json")

    /** Load user packs from disk and register them. Call once at app startup. */
    suspend fun loadAndRegister() {
        val packs = load()
        packs.forEach { SoundPackRegistry.addUserPack(it) }
    }

    /** Save a user-created sound pack. */
    suspend fun save(pack: SoundPack) {
        SoundPackRegistry.addUserPack(pack)
        persist()
    }

    /** Delete a user-created sound pack. */
    suspend fun delete(id: String) {
        SoundPackRegistry.removeUserPack(id)
        persist()
    }

    /** Generate a new unique ID. */
    fun newId(): String = UUID.randomUUID().toString()

    private suspend fun load(): List<SoundPack> = withContext(Dispatchers.IO) {
        if (!storageFile.exists()) return@withContext emptyList()
        try {
            val array = JSONArray(storageFile.readText())
            (0 until array.length()).map { i -> fromJson(array.getJSONObject(i)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun persist() = withContext(Dispatchers.IO) {
        val packs = SoundPackRegistry.getUserPacks()
        val array = JSONArray()
        packs.forEach { array.put(toJson(it)) }
        storageFile.writeText(array.toString(2))
    }

    private fun toJson(pack: SoundPack): JSONObject = JSONObject().apply {
        put("id", pack.id)
        put("displayName", pack.displayName)
        put("description", pack.description)
        pack.synthParams?.let { p ->
            put("synth", JSONObject().apply {
                put("lubFrequency", p.lubFrequency.toDouble())
                put("lubAmplitude", p.lubAmplitude.toDouble())
                put("lubDurationMs", p.lubDurationMs)
                put("lubAttackMs", p.lubAttackMs)
                put("lubDecayMs", p.lubDecayMs)
                put("dubFrequency", p.dubFrequency.toDouble())
                put("dubAmplitude", p.dubAmplitude.toDouble())
                put("dubDurationMs", p.dubDurationMs)
                put("dubAttackMs", p.dubAttackMs)
                put("dubDecayMs", p.dubDecayMs)
                put("dubOffsetMs", p.dubOffsetMs)
                put("bodyFrequency", p.bodyFrequency.toDouble())
                put("bodyAmplitude", p.bodyAmplitude.toDouble())
                put("bodyDurationMs", p.bodyDurationMs)
                put("lowPassHz", p.lowPassHz.toDouble())
                put("noiseAmplitude", p.noiseAmplitude.toDouble())
                put("masterGain", p.masterGain.toDouble())
            })
        }
        // Background noise
        put("noiseType", pack.noiseType.name)
        put("noiseVolume", pack.noiseVolume.toDouble())
        // Binaural beats
        put("binauralPreset", pack.binauralPreset.name)
        put("binauralCarrierHz", pack.binauralCarrierHz.toDouble())
        put("binauralBeatHz", pack.binauralBeatHz.toDouble())
        put("binauralVolume", pack.binauralVolume.toDouble())
        // Solfeggio tone
        put("solfeggioFrequency", pack.solfeggioFrequency.name)
        put("solfeggioVolume", pack.solfeggioVolume.toDouble())
        // Affirmations
        put("affirmationMode", pack.affirmationMode.name)
        put("affirmationSet", pack.affirmationSet.name)
        put("affirmationCustomTexts", JSONArray(pack.affirmationCustomTexts))
        put("affirmationRecordings", JSONArray(pack.affirmationRecordings))
        put("affirmationIntervalSec", pack.affirmationIntervalSec)
        put("affirmationVolume", pack.affirmationVolume.toDouble())
        put("affirmationSpeechRate", pack.affirmationSpeechRate.toDouble())
        put("affirmationPitch", pack.affirmationPitch.toDouble())
        if (pack.affirmationVoiceName != null) put("affirmationVoiceName", pack.affirmationVoiceName)
    }

    private fun fromJson(obj: JSONObject): SoundPack {
        val synth = obj.optJSONObject("synth")
        val params = synth?.let { s ->
            SynthParams(
                lubFrequency = s.optDouble("lubFrequency", 65.0).toFloat(),
                lubAmplitude = s.optDouble("lubAmplitude", 1.0).toFloat(),
                lubDurationMs = s.optInt("lubDurationMs", 80),
                lubAttackMs = s.optInt("lubAttackMs", 5),
                lubDecayMs = s.optInt("lubDecayMs", 60),
                dubFrequency = s.optDouble("dubFrequency", 95.0).toFloat(),
                dubAmplitude = s.optDouble("dubAmplitude", 0.6).toFloat(),
                dubDurationMs = s.optInt("dubDurationMs", 60),
                dubAttackMs = s.optInt("dubAttackMs", 5),
                dubDecayMs = s.optInt("dubDecayMs", 45),
                dubOffsetMs = s.optInt("dubOffsetMs", 150),
                bodyFrequency = s.optDouble("bodyFrequency", 35.0).toFloat(),
                bodyAmplitude = s.optDouble("bodyAmplitude", 0.3).toFloat(),
                bodyDurationMs = s.optInt("bodyDurationMs", 100),
                lowPassHz = s.optDouble("lowPassHz", 0.0).toFloat(),
                noiseAmplitude = s.optDouble("noiseAmplitude", 0.02).toFloat(),
                masterGain = s.optDouble("masterGain", 0.9).toFloat(),
            )
        }
        return SoundPack(
            id = obj.getString("id"),
            displayName = obj.optString("displayName", "Custom"),
            description = obj.optString("description", ""),
            synthParams = params,
            isUserCreated = true,
            noiseType = try { NoiseType.valueOf(obj.optString("noiseType", "NONE")) } catch (_: Exception) { NoiseType.NONE },
            noiseVolume = obj.optDouble("noiseVolume", 0.1).toFloat(),
            binauralPreset = try { BinauralPreset.valueOf(obj.optString("binauralPreset", "NONE")) } catch (_: Exception) { BinauralPreset.NONE },
            binauralCarrierHz = obj.optDouble("binauralCarrierHz", 200.0).toFloat(),
            binauralBeatHz = obj.optDouble("binauralBeatHz", 10.0).toFloat(),
            binauralVolume = obj.optDouble("binauralVolume", 0.3).toFloat(),
            solfeggioFrequency = try { SolfeggioFrequency.valueOf(obj.optString("solfeggioFrequency", "NONE")) } catch (_: Exception) { SolfeggioFrequency.NONE },
            solfeggioVolume = obj.optDouble("solfeggioVolume", 0.3).toFloat(),
            affirmationMode = run {
                val explicit = obj.optString("affirmationMode", "")
                if (explicit.isNotEmpty()) {
                    try { AffirmationMode.valueOf(explicit) } catch (_: Exception) { AffirmationMode.NONE }
                } else {
                    // Backward compat: infer TTS mode from old packs that had affirmationSet
                    val set = obj.optString("affirmationSet", "NONE")
                    if (set != "NONE") AffirmationMode.TTS else AffirmationMode.NONE
                }
            },
            affirmationSet = try { AffirmationSet.valueOf(obj.optString("affirmationSet", "NONE")) } catch (_: Exception) { AffirmationSet.NONE },
            affirmationCustomTexts = obj.optJSONArray("affirmationCustomTexts")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList(),
            affirmationRecordings = obj.optJSONArray("affirmationRecordings")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList(),
            affirmationIntervalSec = obj.optInt("affirmationIntervalSec", 30),
            affirmationVolume = obj.optDouble("affirmationVolume", 0.8).toFloat(),
            affirmationSpeechRate = obj.optDouble("affirmationSpeechRate", 0.9).toFloat(),
            affirmationPitch = obj.optDouble("affirmationPitch", 1.0).toFloat(),
            affirmationVoiceName = obj.optString("affirmationVoiceName").ifEmpty { null },
        )
    }
}
