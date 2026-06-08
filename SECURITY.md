# Security Policy

## Reporting a vulnerability

If you discover a security vulnerability in HeartBeets, **please do not open a public issue**. Instead, report it privately so it can be fixed before being disclosed.

**Preferred channels** (in order):

1. [GitHub Security Advisories](../../security/advisories/new) — encrypted, tracked, and the recommended path.
2. Email **Cristian.Fodor@outlook.com** with the subject line `[HeartBeets security]`.

Please include:

- A description of the issue and its impact.
- Steps to reproduce (proof-of-concept code, screenshots, or a video are all welcome).
- The version of the app or commit hash you tested against.
- Your name / handle if you'd like to be credited.

## What to expect

- An acknowledgement within a few days.
- A fix or mitigation plan, with a target timeline based on severity.
- Public disclosure coordinated with you once a fix is shipped.

## Scope

When the codebase exists, in scope will be:

- The Android app source.
- Any backend code in this repository.
- Build / packaging configuration.

Out of scope (please don't report these):

- Issues that require a rooted device or compromised host.
- Brute-force attacks against your own account.
- Findings against third-party services or SDKs — report those upstream.

## Special note: heart-rate data

Heart-rate is health data. We treat any vulnerability that could leak HR data, expose another user's stream without consent, or bypass sharing controls as a high-severity issue.

## Hall of fame

Researchers who report valid vulnerabilities will be credited here once fixes are released, unless they prefer to remain anonymous.
