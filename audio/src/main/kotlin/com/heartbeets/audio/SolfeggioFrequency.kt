package com.heartbeets.audio

/**
 * Standard Solfeggio frequencies with their traditional purposes.
 * Each frequency can be layered as a continuous tone under the heartbeat.
 */
enum class SolfeggioFrequency(val hz: Float, val label: String, val purpose: String) {
    NONE(0f, "None", ""),
    HZ_174(174f, "174 Hz", "Grounding & pain relief — deep sense of security and safety"),
    HZ_285(285f, "285 Hz", "Tissue healing — cellular repair and physical regeneration"),
    HZ_396(396f, "396 Hz", "Liberation — releasing guilt, fear, and old trauma"),
    HZ_417(417f, "417 Hz", "Transformation — clearing negativity, facilitating change"),
    HZ_432(432f, "432 Hz", "Earth harmony — natural tuning, balance and inner peace"),
    HZ_528(528f, "528 Hz", "Love & miracles — the most studied tone, associated with DNA repair"),
    HZ_639(639f, "639 Hz", "Heart connection — relationships, compassion, harmony"),
    HZ_741(741f, "741 Hz", "Clarity & expression — problem solving, intuition, detox"),
    HZ_852(852f, "852 Hz", "Spiritual awakening — returning to inner truth and order"),
    HZ_963(963f, "963 Hz", "Divine consciousness — crown chakra, pure awareness"),
}
