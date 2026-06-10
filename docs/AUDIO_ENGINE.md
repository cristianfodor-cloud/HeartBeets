# HeartBeets — Audio Engine: Heartbeat Sound Synthesis

> This document describes the audio layer that turns live BPM into audible
> heartbeat sounds, and the **profile** system that lets users shape how those
> sounds evolve over time.

## Core concept

The app synthesises (or plays back) a **heartbeat sound** — a realistic lub-dub
or stylised pulse — timed so that its cadence matches the user's live heart rate.
If the user's BPM is 72, the listener hears 72 beats per minute of the heartbeat
sound.

This is the simplest and most visceral audio mode: *hear your own heart.*

## Profiles — guided cadence curves

A **Profile** defines a sequence of **stages** that modify the playback cadence
relative to the user's current BPM. Each stage describes:

| Field | Type | Meaning |
|---|---|---|
| `targetOffsetBpm` | `Int` | Where this stage ends, expressed as an offset from the user's live BPM at profile start. E.g. `−10` means "10 BPM slower than current." |
| `durationSec` | `Int` | How long (seconds) the transition to `targetOffsetBpm` takes. |
| `curve` | `EasingCurve` | How the cadence ramps — linear, ease-in, ease-out, etc. |

### Example: "Wind Down" profile

Assume user starts at 80 BPM:

| Stage | Target offset | Duration | Effect |
|---|---|---|---|
| 1 | 0 | 30 s | Hold at current BPM (80) — settle in. |
| 2 | −5 | 60 s | Ramp down to 75 BPM over 1 min. |
| 3 | −10 | 90 s | Ramp down to 70 BPM over 1.5 min. |
| 4 | −15 | 120 s | Ramp down to 65 BPM over 2 min. |

The idea: by hearing a progressively slower heartbeat, the user's own heart rate
may entrain (cardiac biofeedback / auditory pacing).

### Example: "Pre-Workout Ramp" profile

| Stage | Target offset | Duration | Effect |
|---|---|---|---|
| 1 | 0 | 20 s | Hold at resting BPM. |
| 2 | +10 | 45 s | Gradually increase to resting + 10. |
| 3 | +20 | 45 s | Continue ramping up. |

### Profile types

- **Preset profiles** — shipped with the app. Curated for common use cases
  (relaxation, focus, warm-up, cool-down). Exact set TBD.
- **User-created profiles** — the user builds their own stage list in a simple
  editor (slope/time graph).

## Data model (draft)

```kotlin
data class HeartbeatProfile(
    val id: String,                  // UUID or slug for presets
    val name: String,
    val description: String,
    val stages: List<ProfileStage>,
    val isPreset: Boolean            // true = shipped, false = user-created
)

data class ProfileStage(
    val targetOffsetBpm: Int,        // relative to BPM at profile activation
    val durationSec: Int,
    val curve: EasingCurve = EasingCurve.LINEAR
)

enum class EasingCurve {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT
}
```

## Playback behaviour

1. User activates a profile (or stays in "mirror" mode = no profile, just follow
   live BPM 1:1).
2. The audio engine captures the user's BPM at activation time as `anchorBpm`.
3. Each tick, the engine computes `desiredCadence` by interpolating through the
   active stage's offset curve.
4. A heartbeat sound is scheduled at `60 / desiredCadence` second intervals.
5. If the profile ends, playback holds at the final cadence (or returns to mirror
   mode — TBD).

## Decisions

| Item | Choice | Why |
|---|---|---|
| Audio framework | **AudioTrack** (Kotlin, `MODE_STREAM`, low-latency performance mode) | Pure Kotlin, no NDK/CMake. Timing precision (~20–50 ms) is more than sufficient for heartbeat cadences (300–1500 ms intervals). Swap to Oboe later only if jitter proves noticeable. |
| Sound design | **Multiple selectable sound packs** — short PCM samples (~200–400 ms each) bundled as raw resources or loaded from assets. | Gives users personality/preference. Architecture must make the sound interchangeable without touching the scheduler. |

## Sound packs

A **SoundPack** is a named collection of one or more audio samples that
represent a single heartbeat (lub-dub). The user picks their preferred pack in
settings; the audio engine plays whichever pack is active.

### Data model (draft)

```kotlin
data class SoundPack(
    val id: String,              // e.g. "classic", "soft", "mechanical"
    val displayName: String,
    val description: String,
    val sampleRes: Int,          // R.raw.* resource ID (for bundled packs)
    // future: URI for downloaded/user-imported packs
)
```

### Architecture implications

- The **AudioEngine** accepts a `SoundPack` (or its loaded PCM buffer) and uses
  it for all subsequent beats until changed.
- Changing the sound pack mid-playback is seamless — the next beat simply uses
  the new sample.
- Sound packs are listed in a registry (similar pattern to driver factories).
  Bundled packs ship as raw resources; future expansion could allow downloading
  or recording custom packs.
- The cadence scheduler is **decoupled** from the sound: it only decides *when*
  to trigger; the sound pack decides *what* plays.

### Shipped packs (planned, TBD)

- **Classic** — realistic lub-dub, warm.
- **Soft** — muffled, ambient-style pulse.
- **Mechanical** — click/tick, metronome-like.
- More TBD after testing.

## Open questions

- **Entrainment feedback**: should the app show when the user's actual BPM is
  converging toward the profile's target cadence?
- **Profile editor UX**: graphical slope editor, or simple list of
  (offset, duration) pairs?
- **Persistence**: store user profiles in Room alongside HR data, or in a
  separate lightweight store (DataStore / JSON file)?
- **Custom sound import**: allow users to record or import their own heartbeat
  samples? (stretch goal)

## Implementation plan

### New module: `audio/`

The audio engine lives in its own Android library module, parallel to `service/`.
It depends on `core/` (for `HrSample`) but not on `ble/`, `data/`, or `service/`.

```
audio/
├── build.gradle.kts
└── src/main/
    ├── kotlin/com/heartbeets/audio/
    │   ├── AudioEngine.kt              # Public API: start/stop/setSoundPack/setCadence
    │   ├── CadenceScheduler.kt         # Timing loop — writes PCM into AudioTrack
    │   ├── SoundPack.kt                # Data class + registry
    │   ├── SoundPackRegistry.kt        # Lists available packs
    │   ├── PcmLoader.kt               # Decodes raw resources into PCM ShortArrays
    │   ├── HeartbeatProfile.kt         # Profile + Stage data classes
    │   ├── ProfileInterpolator.kt      # Computes desiredCadence from elapsed time
    │   └── ProfileRepository.kt        # Load/save profiles (presets + user-created)
    └── res/raw/
        ├── heartbeat_classic.wav       # ~300 ms lub-dub sample, 44.1 kHz 16-bit mono
        ├── heartbeat_soft.wav
        └── heartbeat_mechanical.wav
```

### Detailed steps

---

#### Step 1 — Module scaffolding & AudioTrack spike

**Goal**: a new `audio` module that can play a heartbeat sound once on command.

**Tasks**:
1. Create `audio/build.gradle.kts` — Android library, depends on `:core`, uses
   `kotlinx-coroutines-android`.
2. Add `include(":audio")` to `settings.gradle.kts`.
3. Add `implementation(project(":audio"))` to `app/build.gradle.kts`.
4. Create `PcmLoader.kt`:
   - Takes a `Context` + `@RawRes Int`.
   - Decodes WAV (or use `MediaExtractor`/`MediaCodec`) into a `ShortArray` of
     PCM samples at known sample rate (44100 Hz, 16-bit, mono).
5. Create `AudioEngine.kt`:
   - `fun start()` — allocates an `AudioTrack` (`MODE_STREAM`,
     `ENCODING_PCM_16BIT`, 44100 Hz, mono, `PERFORMANCE_MODE_LOW_LATENCY`).
   - `fun stop()` — releases the `AudioTrack`.
   - `fun playBeatOnce()` — writes the loaded PCM buffer into the track (for
     testing).
6. Add a placeholder WAV file in `res/raw/` (any short heartbeat sample, even a
   sine beep — we'll replace later).
7. Wire a "test play" button in the existing `LiveHrScreen` to call
   `audioEngine.playBeatOnce()`.
8. **Verify**: tap the button, hear the sound with acceptable latency.

**Done when**: button tap → sound plays, no perceptible delay.

---

#### Step 2 — SoundPack model & registry

**Goal**: multiple heartbeat sounds selectable at runtime.

**Tasks**:
1. Create `SoundPack.kt`:
   ```kotlin
   data class SoundPack(
       val id: String,
       val displayName: String,
       val description: String,
       @RawRes val sampleRes: Int
   )
   ```
2. Create `SoundPackRegistry.kt`:
   - Hardcoded list of bundled packs.
   - `fun getAll(): List<SoundPack>`
   - `fun getById(id: String): SoundPack?`
   - `fun getDefault(): SoundPack`
3. Update `AudioEngine` to accept a `SoundPack`, load its PCM via `PcmLoader`,
   and hold the buffer in memory. Expose `fun setSoundPack(pack: SoundPack)`.
4. Add 2–3 placeholder WAV files (different timbres — even if rough, we iterate
   later).

**Done when**: calling `setSoundPack(...)` then `playBeatOnce()` plays the
correct sound.

---

#### Step 3 — Cadence scheduler (mirror mode)

**Goal**: continuous rhythmic playback that follows live BPM.

**Tasks**:
1. Create `CadenceScheduler.kt`:
   - Runs on a dedicated coroutine (`Dispatchers.Default`).
   - Maintains `currentIntervalMs: Long` (= `60_000L / bpm`).
   - Loop: write the heartbeat PCM into `AudioTrack`, then write silence for the
     remainder of `currentIntervalMs` minus the sample duration.
   - Exposes `fun updateBpm(bpm: Int)` — recalculates interval, takes effect on
     the next beat (no mid-sample interruption).
   - Exposes `fun start()` / `fun stop()`.
2. Update `AudioEngine`:
   - `fun startMirrorMode(bpmFlow: Flow<Int>)` — collects the flow and calls
     `scheduler.updateBpm()` on each emission.
   - `fun stopPlayback()`.
3. In `HrService` (or in the ViewModel), when the user starts audio playback,
   map `HrDriver.samples` to a `Flow<Int>` of BPM values and feed it to
   `audioEngine.startMirrorMode(...)`.
4. **Verify on device**: connect wearable → hear heartbeat in sync with live BPM.
   Change activity level → cadence follows.

**Done when**: live BPM → rhythmic heartbeat sound, cadence updates smoothly.

---

#### Step 4 — Profile data model & persistence

**Goal**: profiles can be created, stored, and loaded.

**Tasks**:
1. Create `HeartbeatProfile.kt` and `ProfileStage.kt` in `audio/` (as in the
   draft model above, plus `EasingCurve` enum).
2. Create `ProfileRepository.kt`:
   - For now, use a simple JSON file in app-internal storage (avoid coupling to
     Room — profiles are small, don't need SQL queries).
   - `suspend fun getAll(): List<HeartbeatProfile>`
   - `suspend fun save(profile: HeartbeatProfile)`
   - `suspend fun delete(id: String)`
   - `fun getPresets(): List<HeartbeatProfile>` — hardcoded in code, always
     available.
3. Seed presets:
   - "Wind Down" (the example from this doc).
   - "Pre-Workout Ramp" (the example from this doc).
   - "Steady Hold" (single stage, offset 0, 5 min — basically mirror mode with a
     timer).

**Done when**: unit test creates, saves, reloads, and deletes a profile.

---

#### Step 5 — Profile interpolation engine

**Goal**: given an active profile and a start time, compute the target cadence at
any moment.

**Tasks**:
1. Create `ProfileInterpolator.kt`:
   ```kotlin
   class ProfileInterpolator(
       private val profile: HeartbeatProfile,
       private val anchorBpm: Int,           // user's BPM when profile was activated
       private val startTimeMs: Long = System.currentTimeMillis()
   ) {
       fun cadenceAt(nowMs: Long): Int       // returns target BPM at this instant
       fun isFinished(nowMs: Long): Boolean
   }
   ```
2. Implement stage progression: accumulate stage durations, find current stage,
   interpolate between previous offset and current `targetOffsetBpm` using the
   stage's `EasingCurve`.
3. Implement easing math (linear, ease-in = quadratic, ease-out, ease-in-out).
4. Unit tests: verify cadence values at stage boundaries, mid-stage, and after
   profile completion.

**Done when**: `ProfileInterpolator` produces correct BPM values for all test
cases, including edge cases (single stage, zero-duration stage, profile finished).

---

#### Step 6 — Wire profiles into playback

**Goal**: user can activate a profile and hear the cadence follow the curve
instead of live BPM.

**Tasks**:
1. Add to `AudioEngine`:
   - `fun startProfile(profile: HeartbeatProfile, currentBpm: Int)` — creates a
     `ProfileInterpolator`, starts a ticker that calls
     `scheduler.updateBpm(interpolator.cadenceAt(now))` every ~200 ms.
   - `fun stopProfile()` — returns to mirror mode (or stops).
2. Add state: `enum PlaybackMode { STOPPED, MIRROR, PROFILE }`.
3. When a profile finishes, either hold the last cadence or switch back to mirror
   mode (make this configurable later; default = hold).
4. UI: add a "Profiles" button on `LiveHrScreen` that opens a bottom sheet with
   the list of available profiles. Tapping one calls `startProfile(...)`.

**Done when**: selecting "Wind Down" → cadence audibly decreases over time
regardless of actual BPM changes.

---

#### Step 7 — Profile editor UI

**Goal**: users can create and edit custom profiles.

**Tasks**:
1. New Compose screen: `ProfileEditorScreen.kt` (in `app/`).
2. UI elements:
   - Profile name text field.
   - Stage list (LazyColumn): each row shows offset, duration, curve. Swipe to
     delete, drag to reorder.
   - "Add stage" button at the bottom.
   - Each stage row expands to sliders: offset (−30 to +30 BPM), duration (10 to
     300 s), curve picker (4 options).
3. Visual preview: a simple line chart showing the cadence curve over total
   duration (Canvas composable, draw the interpolated line).
4. Save button → `ProfileRepository.save(...)`.
5. Navigation: Profile list screen → tap "+" → editor. Tap existing user profile
   → editor pre-filled.

**Done when**: create a custom profile in the editor, save it, see it in the
profile list, activate it, hear it play.

---

#### Step 8 — Sound pack selector UI

**Goal**: users can browse and switch sound packs.

**Tasks**:
1. Settings screen (or bottom sheet from `LiveHrScreen`): list all packs from
   `SoundPackRegistry`.
2. Each row: name, description, "Preview" button (plays one beat), radio selector.
3. Selection persists in `SharedPreferences` / DataStore.
4. On app start, `AudioEngine` loads the last-selected pack.

**Done when**: user switches pack, subsequent playback uses the new sound.

---

### Dependency graph

```
Step 1 (AudioTrack spike)
  └──► Step 2 (Sound packs)
         └──► Step 8 (Pack selector UI)
  └──► Step 3 (Cadence scheduler / mirror mode)
         └──► Step 6 (Wire profiles into playback)
                └──► Step 7 (Profile editor UI)

Step 4 (Profile model + persistence)  ← independent, can run in parallel with 1–3
  └──► Step 5 (Interpolation engine)
         └──► Step 6 (Wire profiles into playback)
```

Steps 1 → 3 are the critical path to "hear your heartbeat." Steps 4 → 5 can
proceed in parallel. Step 6 is the join point. Steps 7 and 8 are UI polish.

---

### Module dependency additions

```
settings.gradle.kts:  include(":audio")

audio depends on:     :core (for HrSample)
app depends on:       :audio (to instantiate AudioEngine and wire UI)
service depends on:   :audio (optional — if service owns playback lifecycle)
```

## Relationship to architecture

This layer sits on top of the HR streaming foundation described in
[ARCHITECTURE.md](ARCHITECTURE.md). It consumes the `HrDriver.samples` flow and
does not affect scanning, connection, or persistence.

```
HrDriver.samples  ──►  AudioEngine  ──►  Speaker / headphones
                            ▲
                            │
                     HeartbeatProfile (active)
```
