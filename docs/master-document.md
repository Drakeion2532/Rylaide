# Rylaide — Master Product & Technical Document

This merges the product/UX brief (pillars, personas, flow diagrams) with
the technical architecture and mechanics already designed and partly
built in this conversation. Where the two sources disagreed, that's
flagged explicitly rather than smoothed over — look for the ⚠️ markers.

---

## 1. Executive overview

Rylaide is a mobile app made for motorcycle riders, by a rider, bringing
communication, riding, preparation, safety, personalization, motorcycle
management, and ride history into one connected experience — not a
navigation app, not a social app, but a **digital riding companion** that
stays with the rider through the whole journey:

```
Open Rylaide → prepare → choose ride type → connect with riders
    → complete pre-ride checks → start ride → communicate while riding
    → monitor the ride → finish → review and manage afterward
```

**The central technology, and the reason this app exists at all, is the
rider-to-rider intercom** — using the phone every rider already carries
as the communication platform, through their own connected audio
hardware, rather than requiring dedicated intercom hardware at all.
Everything else is a supporting ecosystem built around that one idea.

---

## 2. The problem being solved

Riding involves far more than getting from A to B: preparing the bike,
checking weather, planning or starting a ride, navigating, talking to
other riders, joining or forming a group, tracking fuel and distance,
remembering past rides, managing vehicle info, finding places, and
accessing safety information. Today these live in separate apps — one
for maps, one for chat, one for weather, one for fuel tracking. Rylaide's
premise is bringing these into one rider-focused platform, connected
through the actual journey of riding rather than bolted together as
unrelated features.

---

## 3. The central idea — four elements

> Rylaide connects the **rider**, the **motorcycle**, the **ride**, and
> **other riders** through one personalized mobile experience.

- **Rider** — the app is designed around the individual
- **Motorcycle** — vehicle info lives in the app as part of ownership
- **Ride** — the full ride lifecycle is supported, prep to history
- **Riders** — groups and the intercom connect riders to each other

---

## 4. "Made for riders, by a rider"

This shapes the information shown, the order actions happen in, the
preparation experience, the group experience, the Ride Hub, the
motorcycle-management features, the visual design, and the
customization system. The goal: a rider feels the app understands the
context of riding, rather than being a generic product with motorcycle
features added afterward.

---

## 5. Who it's for

- **Solo riders** — ride independently, no group needed: `Home → Ride
  Solo → Pre-Ride → Ride Hub → Complete Ride → History`
- **Riders who ride with friends/groups** — create groups, join via
  code or QR scan, communicate, view group activity, review shared
  history
- **Motorcycle enthusiasts** — care about the bike beyond the ride
  itself: Garage, vehicle info, Fuel Log, ride history/detail
- **Fleets and organizations** (longer-term, from the business case) —
  delivery riders, tour operators, event/security teams needing group
  coordination at a scale individual riders don't, and the project's
  clearest path to real revenue once the free consumer app has usage
  behind it

---

## 6. The eight product pillars

| Pillar | What it covers |
|---|---|
| 1. Communication | The intercom — the core differentiator |
| 2. Riding | Solo/Group riding, the Ride Hub |
| 3. Preparation | Pre-ride checklist |
| 4. Community | Groups — the social/collective layer |
| 5. Motorcycle management | Garage, Fuel Log |
| 6. Safety & context | Weather, Safety |
| 7. History | Past rides as reviewable records |
| 8. Personalization | The rider makes the interface their own |

---

## 7. The intercom — why it's central, and how it actually works

### The conceptual shape
```
Rider A → Smartphone → Rylaide → Communication System
       → Rylaide → Smartphone → Rider B
```
The phone *is* the communication platform. Audio reaches the rider
through whatever they've connected — a Bluetooth helmet headset, plain
wired earphones, or (as a fallback) the phone's own mic/speaker.

### Why this is more than a standalone feature
```
Create / Join Group → Group Members → Start Ride
   → Intercom Connection → Riders Communicate → Ride Together → End Ride
```
This is what gives a Group real functional purpose beyond listing
members — the group *is* the live conversation during a ride.

### The actual mechanics (already designed, partly built in this conversation)

**Finding each other:** with no internet, phones discover each other
directly over Wi-Fi Direct / Multipeer Connectivity — no router, no
cell tower, no account involved. With internet, a lightweight
**signaling server** (already built and tested — runs, builds, responds
to real requests) makes the introduction between two riders' apps. It
never touches or stores audio, only helps two phones find each other.

**The connection itself:** **WebRTC**, using the **Opus** codec, is the
one audio engine used for *both* the offline mesh path and the internet
path — same pipeline either way, only how the two phones found each
other differs.

**The fallback when a direct internet connection fails:** a **TURN
relay** server forwards audio between the two phones. This is automatic
and invisible to the rider.

**The live decision ("Path Selector"):** for every other rider, the app
continuously asks *mesh available right now? If not, is internet
available?* — mesh first, since it's free and has no server round-trip.
This re-evaluates throughout a ride, not just once, and switches paths
via a WebRTC **ICE restart** that swaps the connection without dropping
the call.

**Reaching riders further away (multi-hop mesh):** a rider's voice can
relay through other riders in between to reach someone out of direct
range. Each hop adds latency, so the practical ceiling is roughly 4-5
hops — which sets the realistic mesh-only group size at about 6-10
riders in a loose line.

**Cleaning the audio before it's sent:** an on-device **voice activity
detector (VAD)** filters wind/engine noise from the mic before anything
is transmitted — less data, less battery, cleaner audio for the group.

**Getting audio to the right physical device:** checked in this specific
order — wired headset/earphones first (no connection step needed),
Bluetooth headset second (needs an explicit SCO connection step), phone
speaker/mic last as a fallback. This order, not brand detection, is why
*any* headset — Cardo, Sena, EJEAS, generic, or no headset at all —
works identically.

**Why open mic, not push-to-talk:** tap to unmute, tap again to mute, no
time limit — mirrors how dedicated intercom hardware already works and
avoids fumbling a hold-button with gloves. The tradeoff (more
battery/data than push-to-talk) is why VAD exists, and why the UI shows
a persistent "you're live" indicator so no one forgets they're
broadcasting.

**Staying connected automatically:** both the mesh discovery and
internet connection keep listening/retrying in the background, so a
rider who drops out of range or loses signal reconnects automatically
with no action needed.

**Smartphone capabilities this all depends on:**
```
            SMARTPHONE
                │
    ┌───────────┼───────────┐
    ↓           ↓           ↓
  GPS       Microphone   Bluetooth
    │           │           │
    ↓           ↓           ↓
  Ride       Intercom     Helmet
 Tracking     Audio       Audio
```

### ⚠️ Open item: microphone interaction pattern
The exact interaction (open-mic toggle, as currently built, vs.
push-to-talk, vs. voice-activation-only) should be treated as settled in
favor of **open mic with VAD**, per the mechanics above and the UI
already built — this doesn't need re-deciding unless there's a specific
reason to revisit it.

---

## 8. Navigation & trip companion (Blip-derived features)

- **Maps and routing:** OpenStreetMap tiles + OSRM routing — free,
  keyless, no billing exposure. The public OSRM server is fair-use/
  non-commercial only; self-hosting (still free, just needs a small
  server) is the eventual path once usage or any B2B revenue is real.
- **GPS vs. map tiles:** GPS position always works with zero internet
  (satellite signal); only the map *image* underneath needs pre-cached
  tiles for a fully offline ride.
- **Trip recording:** Haversine-distance calculation with GPS-jitter
  filtering, destination-arrival detection, route storage thinned to
  ~200 points/trip to keep local storage light.
- **Route progress, cheaply:** the fetched route becomes a small
  weighted graph; Dijkstra's algorithm finds the rider's shortest-path
  progress along it on every GPS tick, without rescanning the whole
  route each time.
- **Pre-ride checklist:** one tagged master list (not several hardcoded
  lists), filtered live by vehicle type, trip length, and time of day.
  ⚠️ Open question carried over: should jacket/gloves be "recommended"
  rather than "required" for a short daily commute specifically?
- **Fuel log:** fill-to-full mileage calculated automatically from
  consecutive odometer readings and liters used; price-per-liter and
  trend computed, not entered manually.
- **Speed gauge:** drawn in code (not an image), so it can be recolored
  to match the rider's chosen accent; needle sweep deliberately leaves
  the bottom of the dial clear for a digital readout inset (the "mixed"
  style) to never be crossed by the needle.

---

## 9. Personalization — token-based, not screen-by-screen

### The problem with the naive approach
```
Screen 1 = Orange
Screen 2 = Orange
Screen 3 = Orange
```
Hardcoding a color on every screen means changing it later means editing
every screen. Instead:
```
Theme → Design Tokens → All Components → All Screens
```
When the rider changes their accent (`Accent = Blue`), every screen
updates automatically because every screen reads the *current* token
rather than being told a fixed value.

### What's already real, specifically
- **Accent color:** a shared setting every screen reads from — already
  implemented this way in the Compose code built in this conversation
  (currently default `#E8A33D`, user-changeable via swatches)
- **Theme mode (Light/Dark/Auto):** Auto uses the rider's actual
  location + real clock time, not just the phone's system dark-mode
  setting, with a manual override for edge cases a clock can't predict
- **Glove mode:** one setting that scales touch targets (mic button,
  SOS, avatars) up together, independent of theme — a purely physical
  fix for gloved precision, not a visual one

This reinforces the core philosophy: **Rylaide is the rider's app, not
an app the rider happens to use** — reflected architecturally, not just
cosmetically.

---

## 10. Full screen inventory & end-to-end flow

```
                           R E L A I D E
                                │
                         SPLASH / LAUNCH
                     (animation, not a distinct screen)
                                │
                         AUTHENTICATION
                 ┌──────────────┴──────────────┐
              SIGN IN                       SIGN UP
                 │                             │
          Set Up Password               Sign Up Form
                                               ↓
                                        Verification Code
                                               ↓
                                         Create Password
                                               ↓
                 └──────────────┬──────────────┘
                         ACCOUNT CREATED
                                │
                           PERMISSIONS
                    ┌───────────┴───────────┐
                LOCATION              NOTIFICATIONS
                    └───────────┬───────────┘
                           WALKTHROUGH
                                ↓
                          QUICK SETUP
                     ┌──────────┴──────────┐
                 VEHICLE               PERSONALIZE
                     └──────────┬──────────┘
                              HOME
          ┌─────────────────────┼──────────────────────┐
      SOLO RIDE             GROUP RIDE             UTILITIES
          │                     │                 Fuel Log · Garage
          │                   GROUPS              Discover · Safety
          │            ┌────────┼────────┐        Weather · History
          │         Create     Join    Scan QR    Settings
          │            └────────┼────────┘
          │                 GROUP HOME
          │                (Chat/Log/History tabs)
          └───────────┬─────────┘
                PRE-RIDE CHECKLIST
                        ↓
                   START RIDE
                        ↓
                    RIDE HUB
          ┌───────────┼───────────┐
         MAP         SPEED      INTERCOM
                                     │
                                 MICROPHONE
          └───────────┬───────────┘
                  ACTIVE RIDE
                        ↓
                    END RIDE
                        ↓
                  RIDE RECORD
                        ↓
                  RIDE HISTORY
                   /         \
            RIDE DETAIL    FULL MAP
```

**32 screens total.** Shared components: Top Bar, Button
(filled/outlined), Text Field, Card — plus one color-variable collection
(Dark/Light modes) driving every fill, already established in the Figma
work.

---

## 11. How everything connects (not independent features)

```
Intercom ──→ Groups ──→ Rides ──→ GPS ──→ Maps
                 │         │
                 │         └──→ History ──→ Ride Detail / Maps
                 │
Vehicles ──→ Garage ──→ Fuel Log

Location ──→ Weather

Settings ──→ Personalization ──→ entire UI
```
This is what makes Rylaide a connected platform rather than a pile of
unrelated screens.

---

## 12. Technology — including one unresolved conflict

### ⚠️ Conflict to resolve: cross-platform vs. fully native

The incoming product brief recommends:
> React Native + TypeScript for the main app, with native Swift/Kotlin
> only where deeper device functionality (Bluetooth, audio, mic,
> background location) is required.

This conversation already decided, and has already written real code
against, the opposite:
> **Fully native — Kotlin on Android, Swift on iOS, no cross-platform
> layer at all** — specifically because the mesh networking and
> Bluetooth headset audio need native access *throughout* the app, not
> just in a few isolated modules.

**My recommendation: keep the native-only approach.** The intercom isn't
a small feature bolted onto a mostly-UI app — it's the central
technology, touching audio, Bluetooth, and networking on nearly every
screen (Ride Hub, Group Home, Settings, even Home's floating mic
button). A React Native shell with native modules for "the hard parts"
would mean constantly crossing a bridge between the two worlds for the
app's single most important feature — real risk, no clear upside, and
it would mean discarding working code. **This needs an explicit decision
either way before more screens get built in either direction.**

### Otherwise-agreed technology choices

| Layer | Choice | Why |
|---|---|---|
| Android | Kotlin, Jetpack Compose | Native hardware access |
| iOS | Swift, SwiftUI | Native hardware access |
| Mesh discovery | Nearby Connections (Wi-Fi Direct) / Multipeer Connectivity | Built into the OS, free |
| Audio engine | WebRTC + Opus | One pipeline for mesh and internet paths |
| Headset audio | Standard Bluetooth HFP | Works with any brand, not proprietary |
| Signaling | Node.js/TypeScript server | Built and tested; matchmaking only, no audio |
| TURN relay | coturn, self-hosted | Open-source, fallback path only |
| Maps/routing | OpenStreetMap + OSRM | Free, keyless |
| Geocoding | Nominatim | Free, same fair-use caveat as OSRM |
| Hosting | Render (free tier) | For the signaling server |

### Backend/database (from the incoming brief, not yet built)
A cloud backend and database for authentication, user/group/ride/vehicle
data, fuel records, and settings — this is real, necessary scope **if**
accounts become required (see Section 15's open question) that doesn't
exist yet in this conversation's build.

---

## 13. Architecture, combined view

```
┌─────────────────────────────────────────────┐
│              Rylaide Mobile App              │
│  UI/UX · Ride & Group Experience · Intercom  │
│  GPS/Location · Bluetooth/Audio · Settings   │
└──────────────────────┬───────────────────────┘
                        │
         ┌──────────────┴──────────────┐
         ↓ (internet path only)        ↓
   Signaling Server              TURN Relay Server
  (matchmaking, no audio)      (fallback audio relay)
         │
         ↓ (if accounts/groups persistence is adopted)
   Backend / Database
   Users · Groups · Rides · Vehicles · Fuel · Settings
```
Everything above the first dotted line runs entirely on the rider's
phone and needs nothing external when riders are within mesh range of
each other.

---

## 14. Security & privacy (new section — not previously detailed)

Given the app will touch account info, live location, ride routes, group
membership, voice communication, and vehicle data, the production build
needs:
- Secure authentication (if/once accounts exist — see open question
  below)
- Encrypted communication (TLS for any backend traffic; the WebRTC audio
  itself should not rely on network-level security alone)
- Protected, rate-limited API access on the signaling server
- Secure, unguessable group-join codes (already designed this way —
  6-character codes, not sequential IDs)
- Clear, explicit permission requests (location, Bluetooth, microphone)
  with real user-facing explanations, not just manifest entries
- Minimal data retention — avoid storing voice data at all where
  possible, since audio should route peer-to-peer/through TURN without
  being recorded or persisted anywhere
- A defined policy on location-data retention, especially for ride
  history

---

## 15. Business model

Core intercom stays free, permanently. Revenue paths: **freemium**
(larger groups, extended history, priority TURN relay as paid add-ons)
and, longer-term, **B2B/fleet licensing** of the mesh+hybrid technology
to delivery fleets, tour operators, or event/security organizations —
the clearer revenue path, and the reason OSRM/Nominatim's fair-use
limits need a self-hosting plan before being relied on commercially.

---

## 16. Current build status — what's real vs. designed vs. conceptual

| Piece | Status |
|---|---|
| Signaling server | **Real, tested.** Installs, builds, runs, responds to real requests. |
| Android project scaffold, permissions, Gradle setup | **Real.** |
| Audio routing logic (wired → Bluetooth → speaker priority) | **Real logic**, not yet device-tested. |
| Home/create-join screen, in-ride screen, Settings (theme, accent, glove mode) | **Real Compose UI**, previewable, covers a small fraction of the full 32-screen scope. |
| Mesh discovery, WebRTC engine wiring | **Stubbed** — this is the actual next build step, and deliberately the first priority over any other feature. |
| iOS | **Stubbed**, intentionally deferred (Android-first). |
| Navigation, gauge, checklist, fuel log | **Designed/mocked**, no Compose code yet. |
| Accounts, groups (chat/log/history), Discover, Safety, Garage, Weather, backend/database | **Designed in Figma only.** No backend, no data model, no app code anywhere yet — the largest gap between "documented" and "built." |

---

## 17. Open decisions, all in one place

1. **⚠️ Cross-platform (React Native) vs. fully native** — see Section
   12. Recommendation: stay native given what's already built and why.
2. **Naming:** "Rylaide" (used consistently in this document and most of
   this conversation) vs. "Rylaide" (used in the Figma file itself) —
   pick one.
3. **Are accounts required app-wide**, or does the intercom core stay
   account-free (join-by-code, no server-side identity) while only the
   social/group layer optionally uses accounts? This changes the backend
   scope, the security section above, and the privacy story
   substantially.
4. **Checklist jacket/gloves requirement** — required vs. recommended
   for short commutes.
5. **Design source of truth** — align the Compose code to the existing
   Figma file (recommended, since Figma is further along visually) or
   the reverse.
6. **Placeholder fidelity** — the Figma speedometer, map tiles, and
   chart data are simplified stand-ins, not final.
7. **Scope sequencing** — the account/group/social layer is large new
   scope relative to the sprint-based, mesh-audio-first plan this
   conversation has followed; worth deciding whether it's core to v1 or
   a deliberate later phase.
