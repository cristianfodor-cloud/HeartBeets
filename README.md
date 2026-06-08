# HeartBeets

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](#)
[![Status](https://img.shields.io/badge/status-early_development-yellow.svg)](#)

> **Your heartbeat, your soundtrack — and your friends'.**

HeartBeets is an open-source Android app that turns the live heart-rate signal from a wearable into music — beats, rhythm, and (eventually) full musical motifs that follow your pulse in real time. Lower BPM → calmer, slower beats. Higher BPM → driving, faster beats. Hear what your body is doing.

The longer-term vision is **social heart-music**: friend groups where members can tune in to each other's HeartBeets stream and listen to a friend's heart compose its own little track. Run together at a distance. Hear your partner's calm. Share a workout's intensity without saying a word.

> **Status:** very early. The repo currently contains documentation, license, and contribution guidelines only. Code scaffolding and the first prototype start in the next session.

---

## The idea

1. **Connect a wearable** that exposes live heart-rate over Bluetooth LE — chest straps (Polar H10, Wahoo TICKR), smartwatches that expose the standard BLE Heart Rate Service (UUID `0x180D`), or vendor-specific protocols where we can support them.
2. **Stream BPM into a music engine** that maps heart-rate to musical parameters — tempo, intensity, instrumentation, key changes — producing a continuous, evolving piece of music.
3. *(Later)* **Share** your live HeartBeets with a private friend group, so others can listen along.

---

## Why this is interesting

- **Personal feedback loop**: hearing your own heart as music is a surprisingly direct form of biofeedback. Useful for relaxation, focus, runs, recovery.
- **Empathic distance**: hearing a friend's heart-music while they work out, study, or wind down is an unusually intimate way to feel "with" someone without a video call.
- **A creative constraint**: the heartbeat is a great composer — it changes slowly, has obvious phrases (resting, exertion, recovery), and never quite repeats.

---

## Architecture (planned)

```
┌──────────────────────┐    BLE     ┌───────────────────────────┐
│   Wearable (HR sensor)│ ─────────► │  Android client           │
│   • BLE HRS (0x180D)  │            │                           │
│   • Vendor protocols  │            │  • HrSourceManager         │
└──────────────────────┘            │    (one driver per device)│
                                    │  • HrStream (BPM + RR)    │
                                    │  • MusicEngine            │
                                    │    (BPM → tempo/intensity)│
                                    │  • AudioPlayer (Oboe?)    │
                                    │                           │
                                    │  • SocialClient (later)   │
                                    └─────────────┬─────────────┘
                                                  │
                                                  │ (Phase 2)
                                                  ▼
                                    ┌───────────────────────────┐
                                    │   Backend (Firebase TBD)   │
                                    │  • Auth                   │
                                    │  • Live HR stream relay   │
                                    │  • Friend groups          │
                                    └───────────────────────────┘
```

### Heart-rate sources we want to support

- **BLE Heart Rate Service (`0x180D`)** — the universal standard. Works with most chest straps and many smartwatches. This is the default driver and what we'll prototype against first.
- **Wear OS** — read HR from the phone's paired Wear OS watch via `HealthServices`/`PassiveListenerService`.
- **Polar H9 / H10 / OH1** — open SDK with extra goodies (RR intervals for HRV).
- **Vendor protocols (HBand, VeryFit, etc.)** — case-by-case, only where there is a documented or already-reverse-engineered protocol.

The `HrSourceManager` will treat all of these as plug-in drivers behind a single `HrStream` interface, so the music engine never cares where the BPM came from.

### Music engine (very early thinking)

- Pulse pattern locked to BPM (or to a multiple/sub-multiple to stay musical).
- BPM ranges drive intensity zones: rest, light, moderate, vigorous, peak.
- HRV (RR-interval variance) modulates "expressiveness" — relaxed heart = looser timing, stressed heart = tighter, faster-evolving motifs.
- Audio stack: probably **Oboe / AAudio** for low-latency synthesis, with a sample-based percussion engine first and synthesised pads layered on top later.

These choices are starting points — open to changing them once we hit real-world latency.

---

## Roadmap

### Phase 0 — Repo scaffolding *(now)*
- [x] License, README, contributing guides.
- [ ] Android project skeleton (next session).

### Phase 1 — MVP, single user
- [ ] Connect to any BLE Heart Rate Service device.
- [ ] Show live BPM on screen.
- [ ] First music engine: tempo-locked percussion that follows the heart.
- [ ] Background service so playback survives screen-off.

### Phase 2 — More sources, better music
- [ ] Wear OS support.
- [ ] Polar SDK driver (RR + HRV).
- [ ] Layered instrumentation, intensity zones.
- [ ] Recording & export of "heart sessions".

### Phase 3 — Social
- [ ] Account + auth.
- [ ] Friend groups.
- [ ] Live "tune in to a friend" mode.
- [ ] Privacy: opt-in per friend group, easy mute / pause sharing.

---

## Acknowledgements (planned)

We expect to lean on existing open-source work for the BLE plumbing — projects like **Nordic Semiconductor's Android-BLE-Library**, **Polar BLE SDK**, and various community drivers for vendor wearables. Each driver will land with proper attribution and license compliance; nothing gets vendored without checking it can be redistributed.

---

## Privacy

Heart-rate is health data. HeartBeets will treat it as such:

- Nothing leaves your device until you explicitly opt in to a social feature.
- All sharing is per-group and reversible; sharing can be paused with one tap.
- Recordings stay local by default.
- A clear data policy will land before the social phase ships.

---

## Contributing

This is at the "we're shaping the idea" stage, but contributions, feedback, and ideas are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md), and please be kind — see the [Code of Conduct](CODE_OF_CONDUCT.md).

Security-relevant findings: see [SECURITY.md](SECURITY.md). Please don't open public issues for vulnerabilities.

---

## License

[Apache License 2.0](LICENSE). You may fork, modify, and redistribute including commercially, subject to the license's attribution and patent-grant terms.

The name "HeartBeets" and any future logo are not part of the trademark grant — please use a different name and icon if you publish a derivative app.
