# Contributing to HeartBeets

Thanks for your interest! HeartBeets is at the very early "shaping the idea" stage, so feedback, suggestions, and design discussion are at least as valuable as code right now.

## Code of conduct

Participation is governed by the [Code of Conduct](CODE_OF_CONDUCT.md). Please report unacceptable behaviour to **Cristian.Fodor@outlook.com**.

## Ways to help

- **Discuss the idea** — open an issue with a thought, a critique, or a sketch.
- **Test wearables** — once a prototype exists, try it with whatever HR-capable device you have and report back what worked / didn't.
- **Driver contributions** — if you've already got working code (or even just packet captures + notes) for a wearable's BLE protocol, that's gold.
- **Music engine ideas** — mappings from heart-rate to music are a creative space; we want a few different approaches we can swap in.
- **Bug reports** — once code lands, please use the issue templates.

## Reporting issues

- Search [existing issues](../../issues) first.
- Use the bug / feature templates under `.github/ISSUE_TEMPLATE/`.
- For security issues: see [SECURITY.md](SECURITY.md). Please don't open public issues for vulnerabilities.

## Development

Code does not exist yet. When the Android scaffold lands you'll find build instructions here. The plan:

- **Language:** Kotlin
- **Min SDK:** likely 26 (Android 8.0)
- **Build:** Gradle wrapper (no global install needed)
- **Audio:** Oboe / AAudio for low-latency synthesis
- **BLE:** Nordic Android-BLE-Library on top of stock Android BLE APIs
- **No secrets in source** — anything device-specific (API keys, etc.) goes via gitignored config files

## Branching and commits

- Work in a feature branch off `main` (e.g. `feat/polar-driver`, `fix/audio-glitch`).
- Use [Conventional Commits](https://www.conventionalcommits.org/):
  - `feat:` new user-visible capability
  - `fix:` bug fix
  - `chore:` tooling, deps, version bumps
  - `docs:`, `refactor:`, `test:`, etc.
- Keep commits focused — small commits over giant ones.

## Pull requests

Before opening a PR:

1. Build cleanly.
2. Lint cleanly.
3. Describe what changed, how to verify, screenshots / recordings if UI/audio.
4. Link related issues.

Maintainers will review as time allows. This is a personal project — be patient.

## Code style

- **Kotlin:** [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html). 4-space indent.
- **Comments:** explain *why*, not *what*. Only comment when behaviour would surprise the reader.
- **No reformatting commits** unrelated to the change.

## Licensing

By contributing, you agree your contributions will be licensed under the same [Apache License 2.0](LICENSE) that covers the rest of the project. If a contribution requires bringing in third-party code under a different license, flag it in the PR so we can confirm compatibility.
