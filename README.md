# Rylaide

**A free riding companion for motorcycle riders, built by a rider.**

Rylaide's core idea: riders talk to each other through the phones and headsets
they already own. No dedicated intercom hardware, no brand lock-in, and it works
with **no internet** when riders are close together. Around that intercom sit the
things a rider actually needs: navigation, trip tracking, a pre-ride checklist,
a fuel log, group rides and safety tools.

> **Status: early development (Step 2).** The design is complete in Figma.
> The app is being built step by step, starting with the offline intercom.
> Nothing here is ready to install or ride with yet.

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

| Step | What | State |
|---|---|---|
| 0 | Project scaffold, theme, Home and in-ride screens | Done (placeholder UI) |
| 1 | Offline intercom: two phones talk with no internet | Tested on 2 phones: worked past 200 m, voice choppy, slow to connect |
| 2 | Better voice: Opus, jitter buffer, mixing, faster connection | Tested outdoors on phones: connects in about 1 s, no lost packets, wired and Bluetooth switch automatically. Wind and fan noise reducer (build 11) is new and still being tested |
| 3 | Background ride service, headset routing, noise test | Planned |
| 4 | Foundation: API 36, Figma design tokens, themes, Glove Mode | Planned |
| 5 | Accounts and onboarding | Planned |
| 6 | Garage, fuel log, pre-ride checklist | Planned |
| 7 | Maps and solo rides | Planned |
| 8 | Navigation | Planned |
| 9 | Groups | Planned |
| 10 | Online voice fallback | Planned |
| 11 | Safety and SOS | Planned |
| 12 | Voice assistant | Planned |
| 13 | Settings and languages | Planned |
| 14 | Hardening, security, privacy | Planned |
| 15 | Testing with real riders | Planned |
| 16 | Closed test and Play Store release | Planned |

Every push to `main` runs an automatic Android build (see
`.github/workflows/android-build.yml`), so compile errors show up straight away.

## Run Step 1 yourself

1. Open the `android` folder in Android Studio and let Gradle sync.
2. Install on two Android phones (Android 8 or newer).
3. On both: turn on Bluetooth, Wi-Fi and Location. Mobile data can be off.
4. Enter the same group code on both, tap Start, allow the permissions.
5. Tap the mic button on one phone and talk.

More detail is in [`android/SPRINT1.md`](android/SPRINT1.md).

## Contact and feedback

- Email: Relaide.com@gmail.com
- Instagram: [@relaid_bikers](https://instagram.com/relaid_bikers)

The project was previously called Relaide, so some older links and addresses
still use that name.

## License

No license has been chosen yet, so all rights are reserved for now.
