# Docs

Public technical and design notes for Rylaide. Business plans, product briefs and security audits are kept private, outside this repo.

| File | What it is |
|---|---|
| [technical-deep-dive.md](technical-deep-dive.md) | How the intercom and offline link work |
| [build-plan.md](build-plan.md) | Plan, free-tier limits, store checklist, risks |
| [phase2-architecture.md](phase2-architecture.md) | How voice transports, states and encryption fit together |
| [security-and-privacy.md](security-and-privacy.md) | What must never be public, and where secrets live |
| [mockups/](mockups) | Early HTML mockups (open in a browser) |

**Note:** these were written while the plan was still forming. Where they
disagree with the [README](../README.md), the README is current. In particular,
the app is fully native (Kotlin and Swift, not React Native), and online voice
uses LiveKit rather than a custom WebRTC server.
