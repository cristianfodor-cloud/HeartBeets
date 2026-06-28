package com.heartbeets.audio

/** Easing function applied to a timeline segment's BPM ramp. */
enum class EasingCurve { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT }

/**
 * A complete heartbeat definition: sound + BPM timeline + layers + voice messages.
 *
 * This is the single unit that users create, play, and share via heartbeat codes.
 */
data class Heartbeat(
    val id: String,
    val displayName: String,
    val description: String = "",
    // Heartbeat sound
    val synthParams: SynthParams = SynthParams.CLASSIC,
    // BPM timeline (sequence of segments)
    val timeline: List<TimelineSegment> = listOf(TimelineSegment(bpmStart = 65, bpmEnd = 65, durationSec = 300)),
    // Background noise
    val noiseType: NoiseType = NoiseType.NONE,
    val noiseVolume: Float = 0.1f,
    // Binaural beats
    val binauralPreset: BinauralPreset = BinauralPreset.NONE,
    val binauralCarrierHz: Float = 200f,
    val binauralBeatHz: Float = 10f,
    val binauralVolume: Float = 0.3f,
    // Solfeggio tone
    val solfeggioFrequency: SolfeggioFrequency = SolfeggioFrequency.NONE,
    val solfeggioVolume: Float = 0.3f,
    // Voice messages (own recorded voice)
    val voiceEnabled: Boolean = false,
    val voiceRecordings: List<String> = emptyList(),
    val voiceIntervalSec: Int = 30,
    val voiceVolume: Float = 0.8f,
) {
    /** Total duration of the BPM timeline in seconds. */
    val totalDurationSec: Int get() = timeline.sumOf { it.durationSec }
}

/**
 * A segment in the BPM timeline.
 * If [bpmStart] == [bpmEnd], it's a constant hold.
 * If they differ, it's a ramp over [durationSec].
 */
data class TimelineSegment(
    val bpmStart: Int,
    val bpmEnd: Int,
    val durationSec: Int,
    val easing: EasingCurve = EasingCurve.LINEAR,
)
