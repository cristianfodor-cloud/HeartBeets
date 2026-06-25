package com.heartbeets.audio

/**
 * Built-in sets of positive affirmations that can be spoken via TTS
 * during heartbeat playback.
 */
enum class AffirmationSet(val label: String, val affirmations: List<String>) {

    NONE("None", emptyList()),

    CALM("Calm", listOf(
        "I am at peace.",
        "I release all tension.",
        "My body is relaxed and calm.",
        "I am safe in this moment.",
        "I breathe in calm, I breathe out stress.",
        "Tranquility flows through me.",
        "I am grounded and centered.",
        "Every breath brings me deeper peace.",
        "I let go of what I cannot control.",
        "I am surrounded by serenity.",
    )),

    CONFIDENCE("Confidence", listOf(
        "I believe in myself.",
        "I am capable of great things.",
        "I trust my inner wisdom.",
        "I am worthy of success.",
        "My potential is limitless.",
        "I am strong and resilient.",
        "I embrace challenges as opportunities.",
        "I am enough, just as I am.",
        "My confidence grows every day.",
        "I am proud of who I am becoming.",
    )),

    SLEEP("Sleep", listOf(
        "I release this day with gratitude.",
        "My mind is quiet and still.",
        "Sleep comes to me naturally.",
        "I am drifting into peaceful rest.",
        "My body knows how to heal during sleep.",
        "I deserve deep, restorative sleep.",
        "Tomorrow will take care of itself.",
        "I surrender to the comfort of rest.",
        "My dreams are peaceful and kind.",
        "I am letting go completely.",
    )),

    GRATITUDE("Gratitude", listOf(
        "I am grateful for this moment.",
        "I appreciate the beauty around me.",
        "My life is full of blessings.",
        "I am thankful for my body.",
        "Gratitude fills my heart.",
        "I see the good in every situation.",
        "I am grateful for the people in my life.",
        "Every day is a gift.",
        "I appreciate the simple things.",
        "My heart overflows with thankfulness.",
    )),

    HEALING("Healing", listOf(
        "My body is healing itself.",
        "Every cell in my body radiates health.",
        "I am getting stronger every day.",
        "My body knows how to heal.",
        "I send love to every part of my body.",
        "Healing energy flows through me.",
        "I trust my body's wisdom.",
        "I am patient with my healing journey.",
        "I release all that no longer serves me.",
        "Wellness is my natural state.",
    )),

    CUSTOM("Custom", emptyList()),
}
