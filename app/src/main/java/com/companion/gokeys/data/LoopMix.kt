package com.companion.gokeys.data

object LoopMix {
    val STYLES = listOf(
        "Trance", "Funk", "House", "Drum N Bass", "Neo HipHop",
        "Pop", "Bright Rock", "Trap Step", "Future Bass", "Trad HipHop",
        "EDM", "R&B", "Reggaeton", "Cumbia", "ColombianPop",
        "Bossa Lounge", "Arrocha", "Drum N Bossa", "Bahia Mix", "Power Rock",
        "Classic Rock", "J-Pop",
    )

    val KEYS = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")

    private val NOTE_NAMES = listOf(
        "C", "C#", "D", "Eb", "E", "F", "F#", "G", "G#", "A", "Bb", "B",
    )

    fun midiNoteName(n: Int): String {
        val oct = (n / 12) - 1
        return "${NOTE_NAMES[n % 12]}$oct"
    }
}
