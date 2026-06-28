package com.heartbeets.audio

/** How affirmation messages are delivered during playback. */
enum class AffirmationMode {
    /** No affirmations. */
    NONE,
    /** System Text-to-Speech engine. */
    TTS,
    /** User-recorded voice messages. */
    RECORDED,
}
