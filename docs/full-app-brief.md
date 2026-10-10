# Rylaide — Full App Brief

Consolidated from two parallel work streams: this conversation (mesh
intercom architecture, native Android/iOS build, Blip navigation
integration) and a separate Claude.ai session that built out a much
larger Figma screen set (auth, groups, ride hub, discover, safety,
garage, settings). This document merges both into one accurate picture,
and flags explicitly wherever the two streams haven't been reconciled yet
— nothing here is smoothed over or guessed.

---

## 1. What the app is

A free, cross-brand motorcycle/scooter rider app combining:
- An **offline mesh intercom** — riders talk to each other with no
  internet required, falling back to internet (with a TURN relay as a
  last resort) when mesh range isn't enough. Works with any Bluetooth
  headset brand (EJEAS, Cardo, Sena, generic) or plain wired earphones —
  not tied to any one hardware maker's proprietary mesh.
- A **navigation and ride-companion layer** — turn-by-turn navigation,
  a speedometer gauge, trip history, a pre-ride safety checklist, and
  fuel/mileage tracking — built on a fully free map/routing stack (no
  paid API tier required).
- A **social/group layer** (from the Figma session, not yet discussed or
  built in this conversation's code) — rider groups with chat, join by
  QR code, a "ride solo vs ride with a partner" choice, and a broader app
  shell (Discover, Safety, Garage, Weather) around the core ride
  experience.

**⚠️ Naming note:** this conversation and the Figma session used two
slightly different names — **"Rylaide"** here, **"Rylaide"** in the Figma
file. Not yet reconciled — pick one before this goes further.

---

## 2. Target user

Everyday motorcycle/scooter riders, often wearing gloves, riding both day
and night, who want to stay in voice contact with other riders in their
group without paying for or being locked into one brand's proprietary
intercom hardware — plus a connected, ride-focused way to track trips,
fuel, and safety prep.

---

## 3. Complete feature inventory

### 3.1 Core intercom (this conversation, partially built in real code)
- Open-mic voice, not push-to-talk: tap to unmute, tap again to mute, no
  time limit
- On-device voice activity detection (VAD) — filters wind/engine noise
  from the live mic before it's sent, saving bandwidth and battery
- Hybrid connectivity: offline mesh (Wi-Fi Direct / Multipeer
  Connectivity) when no internet is available, direct peer-to-peer
  WebRTC over the internet when it is, TURN relay as the fallback when
  direct connection fails — same WebRTC/Opus audio engine for all three
  paths
- Multi-hop mesh relay — a rider's voice can hop through other riders in
  range to reach someone further down the group, practical ceiling
  around 4-5 hops before latency becomes noticeable
- Automatic reconnection — a rider who drops out of mesh range rejoins
  automatically the moment they're back in range, no manual action
  needed
- Works with **any** headset brand via standard Bluetooth HFP, or with
  **plain wired earphones** (3.5mm/USB-C) — `AudioRouteManager` picks
  wired first (no connection step needed), Bluetooth second, phone
  speaker as a last resort
- SOS button, persistent on the in-ride screen, broadcasts last known
  location to the group
- Glove mode — a rider preference that scales every touch target up
  (mic button, SOS, rider avatars, card icons) for use with riding gloves
- Adaptive theme — Light/Dark/Auto, where Auto follows the actual clock
  (not just the phone's system dark-mode setting); user can override
  and lock one mode regardless of time
- Customizable accent color — not a fixed brand color; the rider picks
  from a palette in Settings, and it propagates across gauges, buttons,
  and highlights app-wide

### 3.2 Navigation & trip tools (ported concept from the "Blip" spec, not yet built in Compose code)
- Turn-by-turn navigation on free OpenStreetMap tiles, routed via OSRM —
  no API key, no billing exposure
- Reverse geocoding (trip from/to labels) via Nominatim, same free OSM
  family
- Speed gauge, three styles: digital (numeral only), analog (custom-drawn
  dial — bezel, tick marks, redline zone, tapered needle), mixed (analog
  dial with a digital readout inset in the zone the needle never sweeps)
- Two display modes: full map view, or a map-free "speed only" view for
  battery/data saving — switchable from Settings or via a one-tap corner
  widget on the map screen
- Unit toggle, km/h ↔ mph
- Trip recording: distance (Haversine, jitter-filtered), duration,
  max/avg speed, destination-arrival detection with a system notification,
  thinned route storage (~200 points/trip) to keep local storage light
- Ride history: list of past trips, tap into a fuller summary
- Pre-ride checklist: one tagged item list (not hardcoded per-scenario
  lists), filtered live by three toggles — vehicle type (scooter/bike),
  trip length (daily/long ride), time of day (day/night, reusing the
  app's own day/night detection). Gates the "Start ride" action.
  - **Open item, not yet resolved:** whether jacket/gloves should be
    downgraded to "recommended" rather than "required" specifically for
    a short daily scooter commute.
- Fuel log: log each fill-up (liters, amount paid, odometer reading),
  automatic price-per-liter calculation, fill-to-full mileage calculation
  from the second entry onward, price-per-liter trend vs the previous
  fill

### 3.3 Social/group layer (Figma session only — new scope, not yet discussed in this conversation)
- Choice between **"Ride Solo"** and **"Ride with a Partner"** (group)
  from the Home screen
- Rider groups: list of existing groups, create a new one, join via scan
  QR code or entering a code, a "group created/joined" confirmation step
- **Group Home** screen with three tabs: chat, log, history — i.e. groups
  have their own persistent space beyond just the live in-ride screen,
  not just an ephemeral ride session
- **Vehicle picker / Garage** — the rider's vehicle(s) are a first-class
  concept, selectable and presumably tied to the fuel log and checklist
- **Discover** screen — purpose not detailed in the handoff beyond its
  existence; likely points/events/routes to explore
- **Safety** screen — distinct from the in-ride SOS button; likely a
  broader safety-info or resources surface
- **Weather** — a header strip on Home plus a dedicated Weather detail
  screen

### 3.4 Account & onboarding (Figma session only)
- Sign in / Sign up choice, with full password-based auth (sign up form
  → verify code → create password → account created)
- Google account picker (sign-in-with-Google alternative)
- Location and Notification permission screens
- A multi-step walkthrough (dot-progression, 3-4 screens)
- Quick setup: accent color swatches + vehicle form — i.e. the
  accent-color choice happens during onboarding, not only buried in
  Settings later
- **Open question this raises:** the mesh intercom core (this
  conversation) was designed to need **no account at all** — groups are
  joined by a 6-character code, nothing server-side persists identity.
  Full password/Google auth is a materially different trust/privacy
  model, and needs a deliberate decision about whether accounts are
  required for the whole app or optional/skippable before this goes
  further.

---

## 4. Complete screen inventory (32 screens, Figma session)

**Auth/onboarding (13 screens):**
Sign in → Set up password · Sign up choice → Sign up form → Verify code →
Create password → Account created → Google picker → Location permission →
Notification permission → Walkthrough (3 screens) → Quick setup

**Core app (19 screens):**
Home → Ride type picker (Solo/Partner) → Groups list → Scan QR → Group
created → Group Home (chat/log/history tabs) → Vehicle picker/Garage →
Pre-ride checklist → Ride Hub (map tab + speed gauge tab) → Discover →
Safety → Weather detail → Ride History → Ride detail → Ride map full →
Fuel log → Settings

**Not a separate screen:** the splash/welcome screen is a JS/launch
animation in the app, not a distinct frame — correctly excluded from the
Figma file for that reason.

**Shared components (Figma):** Top Bar, Button (filled/outlined
variants), Text Field, Card — plus a "Rylaide/Rylaide Colors" variable
collection with Dark and Light modes driving every fill.

**Known placeholders, not full-fidelity yet (flagged honestly in the
Figma handoff itself):** the speedometer gauge SVG, map tiles, and live
chart data are simplified representations in Figma, not the real final
design for those specific elements.

---

## 5. Full navigation flow

```
[Launch animation - not a screen]
        |
        v
Sign in/Sign up choice
  |-- Sign in -> Set up password -----------------+
  |-- Sign up -> Sign up form -> Verify code ------+
                 -> Create password -> Account created
        |
        v
Google picker (alternative/additional sign-in path)
        |
        v
Location permission -> Notification permission
        |
        v
Walkthrough (3 screens, dot progression)
        |
        v
Quick setup (accent color swatches + vehicle form)
        |
        v
======================= HOME =======================
        |
        |-- Ride type picker
        |      |-- Solo -----------------------------+
        |      |-- Partner/Group                     |
        |            |-- Groups list                 |
        |            |     |-- Create new group       |
        |            |     |-- Scan QR / enter code   |
        |            |     '-> Group created/joined   |
        |            '-> Group Home (chat/log/history) |
        |                                              |
        |                        both paths converge   |
        |                                v              |
        |                    Pre-ride checklist <-------+
        |                                |
        |                                v
        |                         Ride Hub
        |                    (Map tab <-> Speed gauge tab)
        |
        |-- Vehicle picker / Garage
        |-- Discover
        |-- Safety
        |-- Weather detail
        |-- Ride History -> Ride detail -> Ride map full
        |-- Fuel log
        '-- Settings
```

Settings itself (per the interactive mockup already built in this
conversation) contains: Appearance (theme mode, accent color), Ride
preferences (glove mode, units), Intercom (open-mic default,
auto-reconnect) — the Figma session's Settings screen scope should be
checked against this to confirm they match or merged deliberately.

---

## 6. Design system

**Two parallel design directions exist and are not yet reconciled:**

| | This conversation | Figma session |
|---|---|---|
| Accent | Amber `#E8A33D` default, user-customizable via swatches (amber/blue/teal/crimson/violet demonstrated) | `#E8A33D` referenced as "the app's" accent in the handoff — **appears aligned**, worth confirming nothing drifted |
| Semantic colors | Red `#B3261E` (live/SOS), green `#1DAA5C` (connected/success) — fixed regardless of theme | Not detailed in handoff |
| Theme modes | Light/Dark/Auto (real clock-based) | Dark/Light variable modes exist in Figma, "Mid-Fidelity" mode also mentioned from an earlier unrelated design system import — worth confirming only Dark/Light actually matter |
| Component source | Hand-built Jetpack Compose (Android), no shared design-system file yet | Figma variable collection + 4 reusable components (Top Bar, Button, Text Field, Card) |

**Recommendation:** treat the Figma file as the visual source of truth
going forward (it's further along and more complete than this
conversation's UI mockups), and bring the Compose code into alignment
with it — rather than the reverse.

---

## 7. Technical architecture (this conversation)

- **Android:** Kotlin, Jetpack Compose
- **iOS:** Swift, SwiftUI (stubs only so far, Android-first per the
  sprint plan)
- **Mesh networking:** Nearby Connections API (Android, wraps Wi-Fi
  Direct), Multipeer Connectivity (iOS)
- **Audio engine:** WebRTC (open-source), same engine for mesh and
  internet paths, Opus codec
- **Backend:** Node.js/TypeScript signaling server (carries no audio,
  just helps peers find each other) — **built and tested, genuinely
  working**
- **TURN relay:** coturn, self-hosted, open-source
- **Maps/routing (Blip-derived):** OpenStreetMap tiles (osmdroid),
  OSRM routing, Nominatim geocoding — all free, keyless; OSRM/Nominatim's
  fair-use policies mean self-hosting is the eventual path once usage
  is real, not a day-one requirement
- **Hosting:** Render (free tier) for the signaling server
- **Cost floor:** effectively $0 except the $99/year Apple Developer
  fee (only needed for iOS) and the $25 one-time Google Play fee

---

## 8. Monetization (discussed, not yet decided)

Leading option: **freemium** — core intercom stays free forever
(the product's whole identity), paid tier unlocks things like larger
group size, extended ride history, priority TURN relay. Secondary path
with likely bigger revenue: **B2B/fleet licensing** of the mesh+hybrid
tech to delivery fleets, tour operators, or event organizers — this is
exactly why the OSRM/Nominatim "non-commercial fair use" limits need a
self-hosting plan before that path is pursued seriously.

---

## 9. Current build status — what's real vs. designed vs. conceptual

| Piece | Status |
|---|---|
| Backend signaling server | **Real, tested code.** Installs, builds, runs, responds to health checks. |
| Android project scaffold | **Real.** Gradle config, manifest, permissions all in place. |
| `AudioRouteManager.kt` / `BluetoothAudioManager.kt` | **Real logic**, not yet device-tested (needs Android Studio + real hardware, which is the user's side to run). |
| `HomeScreen.kt` / `InRideScreen.kt` / `RiderIntercomApp.kt` / `Settings.kt` | **Real Compose UI**, previewable in Android Studio, covers only the create/join + in-ride + basic theme/glove settings — a small fraction of the 32-screen Figma scope. |
| Mesh discovery (`MeshDiscoveryManager.kt`), WebRTC engine (`WebRtcAudioEngine.kt`) | **Stubs only** — this is sprint 1's actual remaining work. |
| iOS | **Stubs only**, intentionally deferred. |
| Navigation/gauge/checklist/fuel-log Compose code | **Not started** — exists only as HTML mockups in this conversation and as Figma frames in the other session. |
| Auth, groups, Discover, Safety, Garage, Weather, Ride Hub tabs | **Figma-only.** No backend, no data model, no Compose code anywhere yet. This is the largest gap between "designed" and "built." |

---

## 10. Open decisions, collected in one place

1. **Name:** "Rylaide" vs "Rylaide."
2. **Accounts:** does the whole app require sign-up/sign-in (as Figma's
   onboarding implies), or does the mesh intercom stay account-free with
   accounts only for the social/group layer? This materially affects
   privacy posture and backend scope.
3. **Design system source of truth:** align Compose code to the Figma
   file (recommended) or vice versa.
4. **Checklist jacket/gloves requirement:** required vs. recommended for
   short commutes.
5. **Placeholder fidelity:** the Figma speedometer gauge, map tiles, and
   chart data are simplified stand-ins — real versions needed before
   those screens are considered final.
6. **Scope sequencing:** the social/group/account layer (13+ screens) is
   entirely new scope relative to everything built in this conversation
   so far — worth deciding whether it's core to v1 or a later phase,
   given the sprint-based, mesh-audio-first plan this conversation has
   been following.
