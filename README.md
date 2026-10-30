# Rylaide

**A free riding companion for motorcycle riders, built by a rider.**

Rylaide's core idea: riders talk to each other through the phones and headsets
they already own. No dedicated intercom hardware, no brand lock-in, and it works
with **no internet** when riders are close together. Around that intercom sit the
things a rider actually needs: navigation, trip tracking, a pre-ride checklist,
a fuel log, group rides and safety tools.

> **Status: early development.** The offline intercom works on test phones and
> the app is being built step by step. It is a test build, not ready for real
> rides or the Play Store yet.

## What it will do

- **Offline rider intercom** between phones using Google Nearby Connections
  (Bluetooth and Wi-Fi Direct, roughly 100 m), with an online fallback when
  riders spread out.
- **Any headset:** standard Bluetooth headsets or wired earphones. No special
  intercom unit needed.
- **Groups:** create or join by code or QR, group chat, ride plans, invites.
- **Navigation and trips:** free maps, turn-by-turn, ride recording and history.
- **Pre-ride checklist** that adapts to your vehicle, trip length and time of day.
- **Fuel log** with mileage and price trends.
- **Safety:** SOS, emergency contacts and crash alerts.
- **Your look:** light/dark themes, your own accent colour, Glove Mode with
  bigger touch targets.

## Tech stack

| Part | Choice |
|---|---|
| Android app | Kotlin, Jetpack Compose, Material 3 (native, Android first) |
| Offline voice | Google Nearby Connections |
| Online voice | LiveKit, self-hosted |
| Accounts, chat, sync | Firebase |
| Maps | MapLibre with OpenFreeMap tiles |
| Routing and search | OSRM and Nominatim |
| Weather | MET Norway |
| iOS | Swift and SwiftUI, planned after Android |

The aim is that running it costs nothing beyond the one-time Google Play
developer fee.

## Repository layout

```
android/       The Android app (Kotlin, Compose)
ios/           iOS app skeleton (not started)
prototypes/    Early experiments, kept for reference
docs/          Product brief, technical notes and design mockups
```

## Build progress

The full plan, with models, free-tier limits, store checklist and risks, is in
[`docs/build-plan.md`](docs/build-plan.md).

Legend: ✅ done · 🟡 built, needs testing on phones · 🔵 partly built · ⬜ planned

| # | Step | Status | Where it stands |
|---|---|---|---|
| 0 | Scaffold, theme, Home and in-ride screens | ✅ | Placeholder screens only. The app still opens to the test screen |
| 1 | Offline intercom: two phones, no internet | ✅ | Worked past 200 m on two phones |
| 2 | Better voice: Opus, jitter buffer, mixing, fast connect | ✅ | Connects in about 1 s, no lost packets outdoors, wired and Bluetooth switch automatically |
| 3 | Background ride service, headset routing, noise test | 🟡 | Ride service, auto headset switching, mic-blocked alert and noise levels are in. Needs a long real ride, and a Bluetooth headset test |
| 3b | Internet path, GPS distance, session state | 🟡 | UDP and WebSocket relay code, rider distances, Session state line and the transport layer are in (Build 22). No relay is deployed yet, so the internet path has never run on phones |
| 3c | Encryption and authenticated riders | 🔵 | Packet encryption is built and unit-tested (SessionCrypto). Not wired into the app yet |
| 4 | Foundation: API 36, design tokens, themes, Glove Mode | 🔵 | Themes and Glove Mode exist on the placeholder screens. API 36 target still to do |
| 5 | Accounts and onboarding | ⬜ | |
| 6 | Garage, fuel log, pre-ride checklist | ⬜ | |
| 7 | Maps and solo rides | ⬜ | |
| 8 | Navigation | ⬜ | |
| 9 | Groups | ⬜ | |
| 10 | Online voice fallback | 🔵 | Same work as 3b: the relay code exists, deployment and testing remain |
| 11 | Safety and SOS | ⬜ | |
| 12 | Voice assistant | ⬜ | |
| 13 | Settings and languages | ⬜ | |
| 14 | Hardening, security, privacy | 🔵 | Encryption built (see 3c). Relay and Nearby authentication still to do |
| 15 | Testing with real riders | ⬜ | |
| 16 | Closed test and Play Store release | ⬜ | |

Voice is tap to talk, tap to mute (not hold to talk).

Every push to `main` runs an automatic Android build (see
`.github/workflows/android-build.yml`), so compile errors show up straight away.

## Run Step 1 yourself

1. Open the `android` folder in Android Studio and let Gradle sync.
2. Install on two Android phones (Android 8 or newer).
3. On both: turn on Bluetooth, Wi-Fi and Location. Mobile data can be off.
4. Enter the same group code on both, tap Start, allow the permissions.
5. Tap the mic button on one phone and talk.

More detail is in [`android/SPRINT1.md`](android/SPRINT1.md).

## About

Rylaide is built by **Shubhan Punde**, a rider who wanted to talk to the people
he rides with using the phone and headset already in his pocket, without paying
for intercom hardware or an app subscription.

Principles the project follows:

- **Free for riders.** Running costs stay at zero apart from the one-time
  Google Play developer fee.
- **Honest about limits.** No "zero delay", no "unlimited range", no claim that
  wind noise is fully removed. Real measurements go in the docs.
- **Built in small steps.** Every piece is pushed to GitHub and built
  automatically, and tested on real phones before the next one starts.

## Contact and feedback

- Email: Relaide.com@gmail.com
- Instagram: [@rylaide_](https://instagram.com/rylaide_)

The project was previously called Relaide, so some older links and addresses
still use that name.

## License

No license has been chosen yet, so all rights are reserved for now.
