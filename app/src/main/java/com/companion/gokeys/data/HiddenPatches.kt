package com.companion.gokeys.data

/**
 * "Hidden" patches surfaced from the goplus / rolandgo-hacking projects.
 *
 * These are GM/expansion sounds that the keyboard's panel UI does not let
 * you reach but that respond to ordinary Bank Select + Program Change. Most
 * are labeled by their original General MIDI 2 program number; a handful
 * come from the bonus banks documented by the goplus hack.
 *
 * The list is intentionally curated (not every PC is exposed) — only sounds
 * confirmed to play on GO:KEYS / GO:PIANO / GO:61K via MSB 121 LSB 0..3.
 */
object HiddenPatches {

    private val GM2_NAMES = listOf(
        "AcousticPiano", "BrightPiano", "ElGrand", "HonkyTonk", "RhodesEP",
        "ChorusedEP", "Harpsichord", "Clavinet", "Celesta", "Glockenspiel",
        "MusicBox", "Vibraphone", "Marimba", "Xylophone", "TubularBell",
        "Dulcimer", "DrawbarOrg", "PercussiveOrg", "RockOrgan", "ChurchOrgan",
        "ReedOrgan", "Accordion", "Harmonica", "TangoAccord", "NylonGuitar",
        "SteelGuitar", "JazzGuitar", "CleanGuitar", "MutedGuitar", "OverdriveGtr",
        "DistortionGtr", "GuitarHarm", "AcousticBass", "FingeredBass", "PickedBass",
        "FretlessBass", "SlapBass1", "SlapBass2", "SynthBass1", "SynthBass2",
        "Violin", "Viola", "Cello", "Contrabass", "TremoloStr",
        "PizzicatoStr", "OrchestralHarp", "Timpani", "StringEns1", "StringEns2",
        "SynStrings1", "SynStrings2", "ChoirAahs", "VoiceOohs", "SynthVoice",
        "OrchestraHit", "Trumpet", "Trombone", "Tuba", "MutedTrumpet",
        "FrenchHorn", "BrassSection", "SynthBrass1", "SynthBrass2", "SopranoSax",
        "AltoSax", "TenorSax", "BaritoneSax", "Oboe", "EnglishHorn",
        "Bassoon", "Clarinet", "Piccolo", "Flute", "Recorder",
        "PanFlute", "BlownBottle", "Shakuhachi", "Whistle", "Ocarina",
        "SquareLead", "SawLead", "CalliopeLead", "ChiffLead", "CharangLead",
        "VoiceLead", "FifthsLead", "Bass+Lead", "FantasiaPad", "WarmPad",
        "PolySynPad", "ChoirPad", "BowedPad", "MetallicPad", "HaloPad",
        "SweepPad", "RainFx", "Soundtrack", "CrystalFx", "AtmosphereFx",
        "BrightnessFx", "GoblinsFx", "EchoesFx", "SciFiFx", "Sitar",
        "Banjo", "Shamisen", "Koto", "Kalimba", "Bagpipe",
        "Fiddle", "Shanai", "TinkleBell", "Agogo", "SteelDrums",
        "Woodblock", "Taiko", "MelodicTom", "SynthDrum", "ReverseCymbal",
        "GtrFretNoise", "BreathNoise", "Seashore", "BirdTweet", "TelephoneRing",
        "Helicopter", "Applause", "Gunshot",
    )

    private val DRUM_KITS = listOf(
        "Standard", "Standard 2", "Room", "Power", "Electronic",
        "Analog", "Jazz", "Brush", "Orchestra", "SFX",
    )
    private val DRUM_KIT_PCS = listOf(1, 2, 9, 17, 25, 26, 33, 41, 49, 57)

    /** Bonus tones documented by the goplus hidden bank scan (subset). */
    private val BONUS_TONES = listOf(
        "AnalogPad 1", "AnalogPad 2", "VintageEP+", "FunkyClav+", "RetroOrg",
        "DigiHorns", "BigSweep", "DreamPad", "PolyKeys", "PadOfDoom",
        "8bitLead", "WahGtr+", "DubBass", "JazzKit+", "OrchHit XL",
    )

    val ALL: List<Patch> = buildList {
        // GM2 bank — MSB 121, LSB 0 (Capital Tones)
        addAll(GM2_NAMES.mapIndexed { i, name ->
            Patch("GM2 $name", "GM2", 121, 0, i + 1)
        })
        // GM2 — variation 1 (LSB 1)
        addAll(GM2_NAMES.mapIndexed { i, name ->
            Patch("GM2v1 $name", "GMV", 121, 1, i + 1)
        }.take(32))
        // Drum kits exposed via MSB 120, LSB 0
        DRUM_KITS.forEachIndexed { i, name ->
            add(Patch("Kit: $name", "DRM", 120, 0, DRUM_KIT_PCS[i]))
        }
        // Bonus tones from goplus (high bank index, GO:KEYS only)
        BONUS_TONES.forEachIndexed { i, name ->
            add(Patch("Bonus $name", "BNS", 87, 80, i + 1))
        }
    }
}
