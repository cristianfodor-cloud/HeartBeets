package com.heartbeets.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Persists user-created [Heartbeat]s to JSON on local storage.
 */
class HeartbeatRepository(context: Context, fileName: String = "heartbeats.json") {

    private val storageFile = File(context.filesDir, fileName)

    fun newId(): String = UUID.randomUUID().toString()

    suspend fun loadAll(): List<Heartbeat> = withContext(Dispatchers.IO) {
        if (!storageFile.exists()) return@withContext emptyList()
        try {
            val array = JSONArray(storageFile.readText())
            (0 until array.length()).map { fromJson(array.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun save(heartbeat: Heartbeat) = withContext(Dispatchers.IO) {
        val all = loadAll().toMutableList()
        val idx = all.indexOfFirst { it.id == heartbeat.id }
        if (idx >= 0) all[idx] = heartbeat else all.add(heartbeat)
        persist(all)
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val all = loadAll().filter { it.id != id }
        persist(all)
    }

    private fun persist(heartbeats: List<Heartbeat>) {
        val array = JSONArray()
        heartbeats.forEach { array.put(toJson(it)) }
        storageFile.writeText(array.toString(2))
    }

    private fun toJson(h: Heartbeat): JSONObject = JSONObject().apply {
        put("id", h.id)
        put("displayName", h.displayName)
        put("description", h.description)
        // Synth params
        put("synth", JSONObject().apply {
            val p = h.synthParams
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
        // Timeline
        put("timeline", JSONArray().apply {
            h.timeline.forEach { seg ->
                put(JSONObject().apply {
                    put("bpmStart", seg.bpmStart)
                    put("bpmEnd", seg.bpmEnd)
                    put("durationSec", seg.durationSec)
                    put("easing", seg.easing.name)
                })
            }
        })
        // Layers
        put("noiseType", h.noiseType.name)
        put("noiseVolume", h.noiseVolume.toDouble())
        put("binauralPreset", h.binauralPreset.name)
        put("binauralCarrierHz", h.binauralCarrierHz.toDouble())
        put("binauralBeatHz", h.binauralBeatHz.toDouble())
        put("binauralVolume", h.binauralVolume.toDouble())
        put("solfeggioFrequency", h.solfeggioFrequency.name)
        put("solfeggioVolume", h.solfeggioVolume.toDouble())
        // Voice
        put("voiceEnabled", h.voiceEnabled)
        put("voiceRecordings", JSONArray(h.voiceRecordings))
        put("voiceIntervalSec", h.voiceIntervalSec)
        put("voiceVolume", h.voiceVolume.toDouble())
    }

    private fun fromJson(obj: JSONObject): Heartbeat {
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
        } ?: SynthParams.CLASSIC

        val timelineArr = obj.optJSONArray("timeline")
        val timeline = if (timelineArr != null && timelineArr.length() > 0) {
            (0 until timelineArr.length()).map { i ->
                val seg = timelineArr.getJSONObject(i)
                TimelineSegment(
                    bpmStart = seg.getInt("bpmStart"),
                    bpmEnd = seg.getInt("bpmEnd"),
                    durationSec = seg.getInt("durationSec"),
                    easing = try { EasingCurve.valueOf(seg.optString("easing", "LINEAR")) } catch (_: Exception) { EasingCurve.LINEAR },
                )
            }
        } else {
            listOf(TimelineSegment(bpmStart = 65, bpmEnd = 65, durationSec = 300))
        }

        return Heartbeat(
            id = obj.getString("id"),
            displayName = obj.optString("displayName", "My Heartbeat"),
            description = obj.optString("description", ""),
            synthParams = params,
            timeline = timeline,
            noiseType = try { NoiseType.valueOf(obj.optString("noiseType", "NONE")) } catch (_: Exception) { NoiseType.NONE },
            noiseVolume = obj.optDouble("noiseVolume", 0.1).toFloat(),
            binauralPreset = try { BinauralPreset.valueOf(obj.optString("binauralPreset", "NONE")) } catch (_: Exception) { BinauralPreset.NONE },
            binauralCarrierHz = obj.optDouble("binauralCarrierHz", 200.0).toFloat(),
            binauralBeatHz = obj.optDouble("binauralBeatHz", 10.0).toFloat(),
            binauralVolume = obj.optDouble("binauralVolume", 0.3).toFloat(),
            solfeggioFrequency = try { SolfeggioFrequency.valueOf(obj.optString("solfeggioFrequency", "NONE")) } catch (_: Exception) { SolfeggioFrequency.NONE },
            solfeggioVolume = obj.optDouble("solfeggioVolume", 0.3).toFloat(),
            voiceEnabled = obj.optBoolean("voiceEnabled", false),
            voiceRecordings = obj.optJSONArray("voiceRecordings")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList(),
            voiceIntervalSec = obj.optInt("voiceIntervalSec", 30),
            voiceVolume = obj.optDouble("voiceVolume", 0.8).toFloat(),
        )
    }
}
