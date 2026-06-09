# HeartBeets — Phase 1 implementation plan

This is the concrete plan for Phase 1: scaffold the Android project, build the
HR architecture described in [ARCHITECTURE.md](ARCHITECTURE.md), and ship a
working app that streams live heart rate from a real device.

For higher-level context (the goals, the abstractions, the diagrams), read
[ARCHITECTURE.md](ARCHITECTURE.md) first.

## Decisions locked in

| Item | Choice | Why |
|---|---|---|
| License | Apache-2.0, clean-room | Already chosen for HeartBeets; gives commercial flexibility. |
| UI | Jetpack Compose | Modern, Kotlin-first, much better fit for live BPM/chart UI. |
| Min SDK | 31 (Android 12) | Drops legacy BLE permission code paths; ~85% device coverage. |
| Target SDK | 35 (Android 15) | Current Play Store requirement at time of writing. |
| Language | Kotlin | Throughout. |
| Build | Gradle Kotlin DSL (`.gradle.kts`) | Cleaner config, type-safe. |
| Package | `com.heartbeets` | Will rename if we publish on Play and it conflicts. |
| BLE library | None — native `BluetoothGatt` | Avoids vendor SDK lock-in, gives full control over CCCD writes. |
| Persistence | Room | Standard, well-known. |
| Async model | Kotlin coroutines + Flow | Throughout. |
| First driver to ship | **VeePoo** (test on ET585) | Real hardware available; proves the architecture handles vendor protocols. |
| Second driver | **StandardHrsDriver** (`0x180D`) | Big coverage, simple, validates the abstraction with a different shape. |
| R-R intervals | Captured from day one | Future-proofs HRV without a schema migration. |

## Phase 1 sub-phases

| Sub-phase | Goal | Done when |
|---|---|---|
| **1.0 Scaffolding** | Android project compiles and runs. | `Hello HeartBeets` Compose screen on the emulator. |
| **1.1 Architecture skeleton** | All interfaces and core types exist. | `HrDriver`, `HrSample`, `DeviceRegistry`, etc. compile. No real driver yet. |
| **1.2 Persistence** | Room schema + repository. | `HrRepository.insert()` + `getRecent()` work in a unit test. |
| **1.3 ScanCoordinator + first stub driver** | Scanning UI flows from registry. | Scan screen lists nearby BLE devices; tapping one opens a "no driver" connect screen. |
| **1.4 VeePoo driver** | ET585 connects and emits live BPM. | BPM flowing on the live screen, persisted to Room. |
| **1.5 HrService** | Background streaming works. | Screen-off, BPM keeps streaming and being persisted. |
| **1.6 Standard HRS driver** | Emulator-tested against `nRF Connect`. | A simulated standard HRS device on a second phone streams to HeartBeets. |
| **1.7 Polish** | App is shippable. | Reconnect on app open, history screen, basic chart, settings. |

Each sub-phase is a commit (or a small commit series) and a meaningful checkpoint
for testing on the ET585.

---

## 1.0 Scaffolding

### Project layout

```
HeartBeets/
├── app/                                  # Application module (UI + assembly)
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── kotlin/com/heartbeets/
│       │   ├── HeartBeetsApp.kt          # Application class
│       │   ├── MainActivity.kt
│       │   └── ui/
│       │       ├── theme/
│       │       ├── scan/ScanScreen.kt
│       │       ├── live/LiveHrScreen.kt
│       │       └── history/HistoryScreen.kt
│       └── res/
│
├── core/                                 # Pure Kotlin, no Android deps
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/heartbeets/core/
│       ├── HrSample.kt
│       ├── HrDriver.kt
│       ├── HrDriverFactory.kt
│       ├── ConnectionState.kt
│       └── BleScanResult.kt
│
├── ble/                                  # Android, native BLE plumbing shared by drivers
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/heartbeets/ble/
│       ├── BleConnection.kt              # GATT wrapper, CCCD writes done correctly
│       ├── BleQueue.kt                   # Serialises GATT operations
│       └── ScanCoordinator.kt
│
├── data/                                 # Room schema + repository
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/heartbeets/data/
│       ├── HrDatabase.kt
│       ├── HrSampleEntity.kt
│       ├── HrSampleDao.kt
│       ├── HrSessionEntity.kt
│       ├── HrSessionDao.kt
│       ├── PairedDeviceEntity.kt
│       ├── PairedDeviceDao.kt
│       └── HrRepository.kt
│
├── service/                              # Foreground service that owns the active driver
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/heartbeets/service/
│       ├── HrService.kt
│       └── HrServiceController.kt        # Tiny client API (start/stop/observe)
│
├── driver-standard-hrs/                  # Plug-in driver
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/heartbeets/driver/standardhrs/
│       ├── StandardHrsDriver.kt
│       └── StandardHrsDriverFactory.kt
│
├── driver-veepoo/                        # Plug-in driver
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/heartbeets/driver/veepoo/
│       ├── VeePooDriver.kt
│       ├── VeePooDriverFactory.kt
│       └── VeePooProtocol.kt             # Command codes + parser, pure functions
│
├── settings.gradle.kts
├── build.gradle.kts                      # top-level
└── gradle/libs.versions.toml             # version catalog
```

### Gradle dependencies (top-level versions)

```toml
[versions]
agp = "8.7.0"
kotlin = "2.0.20"
compose-bom = "2024.10.00"
coroutines = "1.9.0"
room = "2.6.1"
lifecycle = "2.8.7"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version = "1.13.1" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version = "1.9.3" }
androidx-lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }

compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-tooling = { module = "androidx.compose.ui:ui-tooling" }

kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }

room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
```

No third-party BLE library. Native `android.bluetooth.*`.

### Tasks

1. `gradle init` style scaffolding — create modules listed above, all empty.
2. Top-level `build.gradle.kts` declares the version catalog and plugins.
3. `app` module: `MainActivity` with a single Compose screen showing the app name.
4. Verify build runs and the app launches on an emulator.

---

## 1.1 Architecture skeleton

Define interfaces and value types in `core/`, no implementations yet.

### Files

- `core/HrSample.kt` — data class as specified in [ARCHITECTURE.md](ARCHITECTURE.md).
- `core/ConnectionState.kt` — `enum class ConnectionState { Disconnected, Connecting, Connected, Error }`.
- `core/HrDriver.kt` — the interface.
- `core/HrDriverFactory.kt` — the factory interface and `Match` enum.
- `core/BleScanResult.kt` — a platform-agnostic projection of `android.bluetooth.le.ScanResult` so `core/` doesn't pull Android in. Built by `ble/ScanCoordinator` from the real platform type.
- `app/HeartBeetsApp.kt` — Application class. Holds the `DeviceRegistry` singleton. Driver factories register themselves here.

### Tasks

1. Add the four interface/data files in `core/`.
2. Create `DeviceRegistry` in `core/` (just a list + `register()` + `factories()`).
3. `HeartBeetsApp.onCreate()` populates the registry (initially empty — no factories registered yet).
4. Compile.

---

## 1.2 Persistence

### Room schema

Three entities and three DAOs, as in [ARCHITECTURE.md](ARCHITECTURE.md):

- `HrSampleEntity`
- `HrSessionEntity`
- `PairedDeviceEntity`

### Repository

`HrRepository` exposes:

```kotlin
suspend fun openSession(deviceAddress: String, driverId: String): Long
suspend fun closeSession(sessionId: Long)
suspend fun insertSample(sample: HrSample, sessionId: Long)
fun observeSamplesForSession(sessionId: Long): Flow<List<HrSampleEntity>>
fun observeRecentSamples(limit: Int = 200): Flow<List<HrSampleEntity>>
suspend fun rememberPaired(address: String, name: String?, driverId: String)
suspend fun lastPaired(): PairedDeviceEntity?
```

### Tasks

1. Add Room dependency and KSP plugin to `data/`.
2. Create entities and DAOs.
3. Create `HrDatabase` with a small singleton accessor.
4. Implement `HrRepository`.
5. Add a unit test that opens a session, inserts a sample, reads it back.

---

## 1.3 ScanCoordinator + UI

The first user-visible feature. No real driver yet — discovered devices show up
with the name "Unknown / no driver" if no factory matches.

### `ble/ScanCoordinator`

- Wraps `BluetoothLeScanner`.
- `scan(): Flow<DiscoveredDevice>` — cold flow, starts scan when collected, stops on cancellation.
- For each `ScanResult`: build a `BleScanResult`, ask `DeviceRegistry.factories()` for matches, pick the highest-confidence factory (or `null`), emit `DiscoveredDevice(address, name, factory, confidence, rssi)`.
- Permissions: throws if `BLUETOOTH_SCAN` or `BLUETOOTH_CONNECT` are not granted.

### `app/ui/scan/ScanScreen`

- Permission gate: requests both permissions if missing.
- "Scan" button starts a 30-second scan.
- Shows the live list of `DiscoveredDevice`s, sorted by signal strength.
- Each row shows: name, address, factory display name (or "no driver"), RSSI.
- Tapping a row navigates to `LiveHrScreen` with `address` + `factoryId`.

### `app/ui/live/LiveHrScreen`

For now: just a placeholder showing "would connect to address via factoryId."
Real connection happens in 1.4.

### Tasks

1. Manifest entries for the BLE permissions.
2. `ScanCoordinator` implementation.
3. `ScanScreen` Compose UI + permission flow.
4. `LiveHrScreen` placeholder.
5. Navigation between them (Compose `NavHost`).

---

## 1.4 VeePoo driver

This is the big one. Implements the protocol clean-room, on native
`BluetoothGatt`, with correct CCCD writes — fixing the bug that blocked
RythmOfLife.

### `driver-veepoo/VeePooProtocol.kt`

Pure Kotlin object holding command builders and response parsers. No Android imports.

```kotlin
object VeePooProtocol {
    val NUS_SERVICE: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    val NUS_RX: UUID     = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e") // write
    val NUS_TX: UUID     = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e") // notify
    val CCCD: UUID       = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    fun authCommand(timestampSec: Int): ByteArray   // A1 password
    fun queryStatus(): ByteArray                    // D8 00
    fun configure(): ByteArray                      // F4 02 02 00 01 + padding
    fun queryBattery(): ByteArray                   // A0 00
    fun startHr(): ByteArray                        // D0 01
    fun stopHr(): ByteArray                         // D0 00

    sealed interface Response {
        data class HeartRate(val bpm: Int) : Response
        data class Battery(val percent: Int) : Response
        data class AuthResult(val ok: Boolean) : Response
        data object Unknown : Response
    }

    fun parse(bytes: ByteArray): Response
}
```

Command bytes come from RythmOfLife's reverse-engineering notes. Each function
is unit-testable.

### `driver-veepoo/VeePooDriver.kt`

Implements `HrDriver` over `BleConnection` (the native GATT wrapper in `ble/`).

Connection sequence:

1. Connect GATT, discover services.
2. Find NUS service. Enable notifications on `NUS_TX`:
   - `setCharacteristicNotification(true)`
   - **Write `[0x01, 0x00]` to the `NUS_TX`'s `0x2902` CCCD** ← the step the SDK skipped.
3. Send `authCommand(timestampSec)` on `NUS_RX`. Wait for any response.
4. Send `queryStatus`, `configure`, `queryBattery` (best effort, log responses).
5. Send `startHr()`. Move to `Connected`.
6. On every notification on `NUS_TX`: parse, if `HeartRate(bpm)` emit
   `HrSample(bpm = bpm, timestamp = now, rrIntervalsMs = null, ...)`.
7. On `disconnect()`: send `stopHr()`, then `gatt.disconnect()` and `gatt.close()`.

R-R intervals: VeePoo doesn't expose them directly. Leave as `null`.

### `driver-veepoo/VeePooDriverFactory.kt`

```kotlin
class VeePooDriverFactory : HrDriverFactory {
    override val id = "veepoo"
    override val displayName = "VeePoo"
    override val manufacturerHint = "VeePoo"
    // ...
    override fun matches(scan: BleScanResult): Match {
        val name = scan.deviceName.orEmpty()
        return when {
            name.startsWith("H59") -> Match.EXACT
            name.startsWith("ET585") -> Match.EXACT
            name.startsWith("ET") && name.length >= 5 -> Match.LIKELY
            scan.serviceUuids.contains(VeePooProtocol.NUS_SERVICE) -> Match.LIKELY
            else -> Match.NO
        }
    }
    override fun create(...) = VeePooDriver(...)
}
```

NUS is used by lots of devices (it's literally the Nordic UART), so we score
service-only matches as `LIKELY`, not `EXACT`. Name match wins.

### `ble/BleConnection.kt`

Generic wrapper used by every driver:

- Single `BluetoothGatt` instance, single coroutine scope.
- `BleQueue` serialises operations: connect → discover → enable notify → CCCD
  write → write characteristic. Without serialisation, GATT will silently drop
  back-to-back ops.
- Exposes:
  - `suspend fun connect(): GattHandle`
  - `suspend fun GattHandle.enableNotifications(charUuid: UUID)` — does both `setCharacteristicNotification` and the CCCD write.
  - `suspend fun GattHandle.write(charUuid: UUID, data: ByteArray)`
  - `val notifications: Flow<NotificationEvent>` — every notify with characteristic UUID + bytes.

This is the file every future driver will import; getting it right matters more
than any one driver.

### Tasks

1. `BleQueue` (mutex-based, simple).
2. `BleConnection` with the four operations above.
3. `VeePooProtocol` with full unit tests for command builders and parser.
4. `VeePooDriver` wiring.
5. `VeePooDriverFactory` with `matches()` rules.
6. Register the factory in `HeartBeetsApp.onCreate()`.
7. `LiveHrScreen` connects via the factory and shows live BPM as Compose state.
8. Real-device test on the ET585.

### Acceptance test

Connect to ET585. Within 5 seconds, see BPM update on the screen, refreshing on
every notification (typically every 1–2 s). BPM persisted to Room (verify by
opening the DB inspector in Android Studio).

---

## 1.5 HrService

Foreground service that keeps the connection alive across screen-off and app
backgrounding.

### `service/HrService.kt`

- Foreground service, type `health`.
- On `onStartCommand` with intent extra `address` + `factoryId`: looks up the
  factory in the registry, builds a driver, calls `connect()`.
- Subscribes to `driver.samples`, persists each via `HrRepository`.
- Notification: "HeartBeets — listening to ET585 — 72 BPM" (updates every few
  seconds).
- On stop: `disconnect()`, close session.

### `service/HrServiceController.kt`

Small typed API for the UI (no manual `Intent` building):

```kotlin
class HrServiceController(private val context: Context) {
    fun start(address: String, factoryId: String)
    fun stop()
    val state: Flow<ServiceState>           // bound via Service connection
}
```

### Tasks

1. Manifest declaration with `foregroundServiceType="health"`.
2. `HrService` implementation.
3. `HrServiceController` + binding logic.
4. `LiveHrScreen` switches from in-memory driver to talking to the service.
5. Test: start streaming on ET585, lock screen for 5 minutes, unlock — samples
   should be in Room covering the whole interval.

---

## 1.6 Standard HRS driver

Validates that the architecture handles a totally different protocol shape with
a small, focused driver.

### `driver-standard-hrs/StandardHrsDriver.kt`

- Service: `0000180d-0000-1000-8000-00805f9b34fb`
- Characteristic: `00002a37-0000-1000-8000-00805f9b34fb` (Heart Rate Measurement, NOTIFY)
- Optional battery: `0000180f-...` / `00002a19-...`

Notification format (from the Bluetooth SIG spec):

```
byte 0: flags
  bit 0: HR value format — 0=uint8, 1=uint16
  bit 1-2: sensor contact bits
  bit 3: energy expended present
  bit 4: RR intervals present
byte 1..N: HR value (1 or 2 bytes)
optional: energy expended (2 bytes)
optional: one or more RR intervals (each 2 bytes, 1/1024 second units → multiply by 1000/1024 for ms)
```

The parser is small but worth testing carefully. Output: `HrSample` with
`bpm`, optional `rrIntervalsMs`, optional `contactDetected`,
optional `energyExpendedKj`.

### `driver-standard-hrs/StandardHrsDriverFactory.kt`

```kotlin
override fun matches(scan: BleScanResult): Match =
    if (scan.serviceUuids.contains(StandardHrs.SERVICE)) Match.EXACT else Match.NO
```

This is the cleanest possible match — devices that advertise the HRS UUID get
`EXACT`. No name patterns.

### Tasks

1. `StandardHrs` constants object.
2. `StandardHrsDriver` reusing `BleConnection` from `ble/`.
3. Parser with unit tests covering all flag combinations.
4. Register the factory.
5. Test with `nRF Connect` on a second phone, configured as a Standard HRS
   peripheral (it includes a built-in profile).

### Acceptance test

`nRF Connect`-simulated peripheral streams at 70/75/80 BPM with RR; HeartBeets
shows the BPM and persists samples with `rrIntervalsMs` populated.

---

## 1.7 Polish

- **Auto-reconnect**: on app start, if `lastPaired()` exists, offer "reconnect to ET585?" instead of going straight to scan.
- **History screen**: scroll list of sessions, tap one to see a simple BPM-over-time line chart.
- **Settings**: BLE permission status, paired devices list, "forget device".
- **App icon**: anything not the default Android robot.
- **Tested on a clean install**: run through scan → connect → background → reopen.

---

## What ships at end of Phase 1

A working Android app that:
- Scans for BLE devices.
- Auto-detects which driver to use, with the option to override.
- Connects to a VeePoo H59 / ET585 and streams live BPM.
- Connects to any standard-HRS device (chest strap, fitness watch in broadcast mode) and streams BPM + R-R.
- Keeps streaming when the screen is off.
- Persists everything to Room.
- Shows current BPM live and a simple history view.

That's the foundation. Phase 2 is the music engine on top of this.

---

## Open work for the future (NOT in Phase 1)

Captured here so we don't forget:

- Polar BLE SDK driver (BSD-licensed, ECG-quality RR).
- Wear OS companion app driver.
- Xiaomi/Huami protobuf driver.
- Moyoung / Da Fit driver.
- Music listening detection ported from VibeIn (Phase 2).
- Music engine (Phase 2).
- Social layer (Phase 3).
