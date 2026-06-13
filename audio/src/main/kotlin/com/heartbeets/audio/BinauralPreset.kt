package com.heartbeets.audio

enum class BinauralPreset(val carrierHz: Float, val beatHz: Float) {
    NONE(0f, 0f),
    DELTA(150f, 2f),      // 0.5-4 Hz — deep sleep
    THETA(200f, 6f),      // 4-8 Hz — meditation
    ALPHA(200f, 10f),     // 8-14 Hz — relaxed focus
    BETA(250f, 20f),      // 14-30 Hz — alertness
    CUSTOM(200f, 10f),    // user-defined
}
