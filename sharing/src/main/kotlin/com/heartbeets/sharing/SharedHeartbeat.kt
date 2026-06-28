package com.heartbeets.sharing

import com.heartbeets.audio.BinauralPreset
import com.heartbeets.audio.EasingCurve
import com.heartbeets.audio.Heartbeat
import com.heartbeets.audio.NoiseType
import com.heartbeets.audio.SolfeggioFrequency
import com.heartbeets.audio.SynthParams
import com.heartbeets.audio.TimelineSegment

/**
 * Firebase-storable DTO for a shared heartbeat.
 * All fields have defaults for Firestore deserialization.
 */
data class SharedHeartbeat(
    val id: String = "",
    val displayName: String = "",
    val description: String = "",
    // Synth
    val lubFrequency: Double = 65.0,
    val lubAmplitude: Double = 1.0,
    val lubDurationMs: Int = 80,
    val lubAttackMs: Int = 5,
    val lubDecayMs: Int = 60,
    val dubFrequency: Double = 95.0,
    val dubAmplitude: Double = 0.6,
    val dubDurationMs: Int = 60,
    val dubAttackMs: Int = 5,
    val dubDecayMs: Int = 45,
    val dubOffsetMs: Int = 150,
    val bodyFrequency: Double = 35.0,
    val bodyAmplitude: Double = 0.3,
    val bodyDurationMs: Int = 100,
    val lowPassHz: Double = 0.0,
    val noiseAmplitude: Double = 0.02,
    val masterGain: Double = 0.9,
    // Timeline segments
    val timeline: List<Map<String, Any>> = emptyList(),
    // Layers
    val noiseType: String = "NONE",
    val noiseVolume: Double = 0.1,
    val binauralPreset: String = "NONE",
    val binauralCarrierHz: Double = 200.0,
    val binauralBeatHz: Double = 10.0,
    val binauralVolume: Double = 0.3,
    val solfeggioFrequency: String = "NONE",
    val solfeggioVolume: Double = 0.3,
    // Voice
    val voiceEnabled: Boolean = false,
    val voiceRecordingUrls: List<String> = emptyList(),
    val voiceRecordingData: List<String> = emptyList(), // base64-encoded voice files
    val voiceIntervalSec: Int = 30,
    val voiceVolume: Double = 0.8,
) {
    fun toHeartbeat(localVoicePaths: List<String> = emptyList()): Heartbeat = Heartbeat(
        id = id,
        displayName = displayName,
        description = description,
        synthParams = SynthParams(
            lubFrequency = lubFrequency.toFloat(),
            lubAmplitude = lubAmplitude.toFloat(),
            lubDurationMs = lubDurationMs,
            lubAttackMs = lubAttackMs,
            lubDecayMs = lubDecayMs,
            dubFrequency = dubFrequency.toFloat(),
            dubAmplitude = dubAmplitude.toFloat(),
            dubDurationMs = dubDurationMs,
            dubAttackMs = dubAttackMs,
            dubDecayMs = dubDecayMs,
            dubOffsetMs = dubOffsetMs,
            bodyFrequency = bodyFrequency.toFloat(),
            bodyAmplitude = bodyAmplitude.toFloat(),
            bodyDurationMs = bodyDurationMs,
            lowPassHz = lowPassHz.toFloat(),
            noiseAmplitude = noiseAmplitude.toFloat(),
            masterGain = masterGain.toFloat(),
        ),
        timeline = timeline.map { seg ->
            TimelineSegment(
                bpmStart = (seg["bpmStart"] as? Number)?.toInt() ?: 65,
                bpmEnd = (seg["bpmEnd"] as? Number)?.toInt() ?: 65,
                durationSec = (seg["durationSec"] as? Number)?.toInt() ?: 300,
                easing = try { EasingCurve.valueOf(seg["easing"] as? String ?: "LINEAR") } catch (_: Exception) { EasingCurve.LINEAR },
            )
        }.ifEmpty { listOf(TimelineSegment(65, 65, 300)) },
        noiseType = try { NoiseType.valueOf(noiseType) } catch (_: Exception) { NoiseType.NONE },
        noiseVolume = noiseVolume.toFloat(),
        binauralPreset = try { BinauralPreset.valueOf(binauralPreset) } catch (_: Exception) { BinauralPreset.NONE },
        binauralCarrierHz = binauralCarrierHz.toFloat(),
        binauralBeatHz = binauralBeatHz.toFloat(),
        binauralVolume = binauralVolume.toFloat(),
        solfeggioFrequency = try { SolfeggioFrequency.valueOf(solfeggioFrequency) } catch (_: Exception) { SolfeggioFrequency.NONE },
        solfeggioVolume = solfeggioVolume.toFloat(),
        voiceEnabled = voiceEnabled,
        voiceRecordings = localVoicePaths,
        voiceIntervalSec = voiceIntervalSec,
        voiceVolume = voiceVolume.toFloat(),
    )

    companion object {
        fun from(heartbeat: Heartbeat, voiceUrls: List<String> = emptyList()): SharedHeartbeat {
            val p = heartbeat.synthParams
            return SharedHeartbeat(
                id = heartbeat.id,
                displayName = heartbeat.displayName,
                description = heartbeat.description,
                lubFrequency = p.lubFrequency.toDouble(),
                lubAmplitude = p.lubAmplitude.toDouble(),
                lubDurationMs = p.lubDurationMs,
                lubAttackMs = p.lubAttackMs,
                lubDecayMs = p.lubDecayMs,
                dubFrequency = p.dubFrequency.toDouble(),
                dubAmplitude = p.dubAmplitude.toDouble(),
                dubDurationMs = p.dubDurationMs,
                dubAttackMs = p.dubAttackMs,
                dubDecayMs = p.dubDecayMs,
                dubOffsetMs = p.dubOffsetMs,
                bodyFrequency = p.bodyFrequency.toDouble(),
                bodyAmplitude = p.bodyAmplitude.toDouble(),
                bodyDurationMs = p.bodyDurationMs,
                lowPassHz = p.lowPassHz.toDouble(),
                noiseAmplitude = p.noiseAmplitude.toDouble(),
                masterGain = p.masterGain.toDouble(),
                timeline = heartbeat.timeline.map { seg ->
                    mapOf(
                        "bpmStart" to seg.bpmStart,
                        "bpmEnd" to seg.bpmEnd,
                        "durationSec" to seg.durationSec,
                        "easing" to seg.easing.name,
                    )
                },
                noiseType = heartbeat.noiseType.name,
                noiseVolume = heartbeat.noiseVolume.toDouble(),
                binauralPreset = heartbeat.binauralPreset.name,
                binauralCarrierHz = heartbeat.binauralCarrierHz.toDouble(),
                binauralBeatHz = heartbeat.binauralBeatHz.toDouble(),
                binauralVolume = heartbeat.binauralVolume.toDouble(),
                solfeggioFrequency = heartbeat.solfeggioFrequency.name,
                solfeggioVolume = heartbeat.solfeggioVolume.toDouble(),
                voiceEnabled = heartbeat.voiceEnabled,
                voiceRecordingUrls = voiceUrls,
                voiceIntervalSec = heartbeat.voiceIntervalSec,
                voiceVolume = heartbeat.voiceVolume.toDouble(),
            )
        }
    }
}
