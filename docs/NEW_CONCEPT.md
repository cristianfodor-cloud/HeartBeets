# HeartBeets — New Concept

## One-Liner
Create your heartbeat, share it via a code, others listen to it on their phone.

---

## Heartbeat Creator (single unified screen)

You build your complete heartbeat in one place:

### 1. BPM Timeline
- **Constant sections** — e.g., 72 BPM for 5 minutes
- **Ramps** — e.g., ramp from 72 → 55 BPM over 10 minutes
- User builds a sequence of segments (any combination of constants and ramps)
- Total duration is the sum of all segments

### 2. Heartbeat Sound (synth params)
- Lub/dub frequencies, amplitudes, durations
- Attack/decay envelopes
- Body resonance, noise texture, master gain
- This defines *what* the heartbeat sounds like

### 3. Binaural Beats
- Presets (alpha, theta, delta, gamma) or custom carrier/beat frequencies
- Volume control

### 4. Solfeggio Frequencies
- 174, 285, 396, 417, 528, 639, 741, 852, 963 Hz
- Volume control

### 5. Background Noise
- White / Pink / Brown
- Volume control

### 6. Voice Messages (own voice only)
- Record affirmation messages using your microphone
- Set interval (how often they play during the session)
- Volume control
- No TTS — only your real voice

---

## Sharing via Heartbeat Codes

- Each heartbeat you create gets a **10-character code**
- The code maps to your heartbeat configuration stored in Firebase
- Voice recordings are uploaded to **Firebase Storage**
- Someone enters your code → downloads everything → plays locally on their phone

---

## App Flow

```
Home
├── Create My Heartbeat → Heartbeat Creator
├── My Heartbeats → List of your creations (each with a shareable code)
└── Listen to a Heartbeat → Enter a code → Download & play
```

---

## What's Gone

| Removed | Reason |
|---------|--------|
| BLE / wearable scanning | Not needed — BPM is defined in timeline |
| All device drivers | No wearables |
| Foreground service | No BLE connection to maintain |
| Live HR streaming | Replaced by static config sharing |
| Billing / subscriptions | App is free |
| TTS affirmations | Only own voice recordings |
| Preset sound packs | User creates everything from scratch |
| Profile as separate concept | Merged into Heartbeat Creator as BPM timeline |

---

## Technical Modules (after cleanup)

| Module | Purpose |
|--------|---------|
| **app** | UI (Compose), ViewModels, navigation |
| **audio** | Synth engine, binaural, solfeggio, noise, voice recorder, cadence playback |
| **core** | Shared models, utilities |
| **sharing** | Firebase: store/retrieve heartbeat configs + voice file upload/download |
