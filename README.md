# GO:Keys / GO:Piano Companion (Android)

Native Android companion app for the **Roland GO:KEYS** and **Roland GO:PIANO** family
(incl. GO:61K) over **USB MIDI** and **Bluetooth LE MIDI**.

Rewritten from the ground up to address the previous version's limitations:

- **Layering now works** — uses Roland DT1 SysEx to enable Performance Parts and route
  each part to its own MIDI channel. Channel 2 alone is ignored by the keyboard;
  the Performance Part Switch is what actually unlocks layering.
- **Scrollbar on the patch list** — custom `LazyColumnScrollbar` next to the patch
  list with a draggable thumb.
- **Persistent UI state** — the last selected category, search query and scroll
  position of the patch list are restored across launches via `DataStore`.
- **MIDI monitor** — true rolling buffer (200 events) backed by `SharedFlow`, so
  every message reflects the actual current state.
- **English-only UI** — clean, single-locale resource set.
- **Full sound shaping** — every MIDI CC documented in the rolandgo-hacking README is
  exposed per part: Expression (CC11), Filter Cutoff/Resonance (CC74/CC71), Attack /
  Decay / Release (CC73/75/72), Vibrato Rate / Depth / Delay (CC76/77/78), Portamento
  Time + on/off (CC5/65), Mono/Poly (CC126/127), plus a one-tap "Reset CC defaults".
- **Demo songs** — selects and triggers the five built-in GO:KEYS demos via the exact
  SysEx documented by rolandgo-hacking.

## Patch v4 — usability + reset fix

- **Sound library is always open for the Main channel.** The Performance
  screen now embeds the patch picker inline (no Pick-Sound round-trip).
  The list reserves >55 % of the screen height so the custom scrollbar
  is comfortable to drag.
- **Per-part collapsible sections.** Layer / Split (lower) / extra parts
  and Master / Demo are collapsible cards.  Each part's *Sound shaping*
  CC panel now lives inside that part's expand area, so it folds away
  with the rest of the part.
- **Reset to default actually changes the sound.** `resetPartCC` now
  pushes every individual CC after reverting the sliders, so the engine
  can never lag behind the UI.  Each shaping slider also has its own
  inline reset button that resets that single CC and pushes the change
  immediately — no need to nudge the slider afterwards.
- **GO:KEYS / GO:PIANO badges.** Per the rolandgo-hacking and goplus
  documentation, model-specific features are now badged: LoopMix's full
  style/key/variation engine and the five built-in Demo songs are
  GO:KEYS only (GO:PIANO has only a one-shot LoopMix trigger).  Shared
  features (Performance Parts, layering/split, sound-shaping CCs,
  master volume, tempo, reverb/chorus, profiles, macros, automations,
  MIDI monitor) remain unbadged.

## What's new beyond the report

- Multi-screen architecture (bottom nav): Connection, Performance, LoopMix, Profiles,
  Macros, Automations, MIDI Monitor, Help.
- **4 Performance Parts** with independent patch / channel / volume / pan / reverb /
  chorus / split zone (key range).
- **Hidden patches** unlocked from the goplus / rolandgo-hacking documentation
  (GM2 capital + variation banks, drum kits, bonus tones).
- **Profiles** — save and recall the full app state.
- **Macros** — record any sequence of outgoing MIDI messages and replay it with the
  original timing.
- **Automation engine** — declarative trigger → action rules. Triggers: noteOn / CC /
  SysEx prefix / patch select. Actions: panic, send raw bytes, switch patch, toggle
  LoopMix, play macro.
- **LoopMix** style / key / variation via NRPN + DT1 (channel 1).
- **Effects block** writes (reverb / chorus type and level via System Common Effects).

## Build

```
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requirements: Android Studio Hedgehog+ / JDK 17 / Android SDK 35.

> **Note**: this archive does not include the Gradle wrapper JAR. After unpacking,
> run `gradle wrapper --gradle-version 8.7` once (or open the project in Android
> Studio, which will offer to download the wrapper for you). Everything else —
> source, resources, manifest, gradle scripts — is ready to build.

## Project layout

```
app/src/main/java/com/companion/gokeys/
├── MainActivity.kt
├── midi/
│   ├── MidiService.kt        # USB + BLE transport, send queue, rolling monitor
│   ├── BleMidiClient.kt      # BLE MIDI 1.0 GATT client (bypasses broken openBluetoothDevice)
│   └── RolandSysEx.kt        # DT1 builder, Performance Parts, Zones, NRPN, effects
├── data/
│   ├── AppState.kt           # PerformanceConfig (4 parts + 4 zones), profiles, macros, automations
│   ├── Patches.kt            # ~1380 stock patches (GO:KEYS / GO:PIANO)
│   ├── HiddenPatches.kt      # Hidden GM2 + bonus banks (goplus)
│   ├── LoopMix.kt            # Styles / keys
│   └── Repository.kt         # DataStore-backed JSON persistence
├── automation/
│   └── AutomationEngine.kt   # Trigger->action engine + macro player
├── viewmodel/
│   └── CompanionViewModel.kt # Single VM driving every screen
└── ui/
    ├── App.kt                # NavHost + NavigationBar (8 screens)
    ├── components/Common.kt  # SectionCard, sliders, LazyColumnScrollbar
    ├── theme/                # Material 3 dark theme
    └── screens/              # Connection, Performance, Patches, LoopMix,
                              # Profiles, Macros, Automations, Monitor, Help
```

## SysEx reference

All Roland addresses, bank/program assignments and NRPN definitions are derived
from the public documentation in:

- https://github.com/waldt/goplus
- https://github.com/waldt/rolandgo-hacking

The `RolandSysEx` object exposes `dt1(...)` for raw access if you need to script
addresses that aren't covered by the high-level helpers.

## Screenshots

<img width="470" height="auto" alt="gkc1" src="https://github.com/user-attachments/assets/eb822c29-99c5-4e1d-a15a-f6edd47a5686" /> 
<img width="470" height="auto" alt="gkc2" src="https://github.com/user-attachments/assets/b40f054b-9c0c-4d31-beeb-845172608eb3" />


