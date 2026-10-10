# Rylaide — Project Brief

## The one-line pitch

A free app that turns any rider's phone into a walkie-talkie for their
group — no internet required, no proprietary hardware required, no
subscription required — plus the navigation, trip-tracking, and
pre-ride tools a rider actually wants alongside it.

---

## Made by a rider, for riders

Rylaide isn't a generic communication app that happened to get a
motorcycle skin — it's being built by a rider, for riders, starting from
a real rider's own frustration with existing options. Every decision in
this document — the glove-sized buttons, the day/night switching, the
fact that a rider can personalize the app's whole look to their own
taste — comes from actually knowing what it's like to be on a bike with
a phone mounted on the handlebar, not from guessing.

**The main point of this entire app, the reason it exists at all, is the
intercom feature** — using the phone every rider already carries as the
communication device itself, instead of requiring anyone to buy separate
radio hardware. Everything else in this brief (navigation, checklist,
fuel log) is a genuinely useful companion built around that core idea,
but it is not why Rylaide exists. The intercom is.

---

## The idea

Motorcycle and scooter riders who want to talk to each other mid-ride
currently have one real option: buy matching intercom hardware from a
single brand (Cardo, Sena, EJEAS, and so on). These systems work well,
but they're closed — a Cardo headset can't talk to a Sena headset, the
hardware costs real money per rider, and the whole group has to buy into
the same brand to use it together.

Rylaide starts from a different premise: **every rider already carries
the one thing that can solve this — a phone.** Every modern phone has
Wi-Fi, Bluetooth, and a microphone. Instead of relying on proprietary
radio hardware, Rylaide builds its own peer-to-peer "mesh" network
directly between riders' phones:

- **No internet, no problem.** Phones relay each other's voice directly,
  rider to rider, over Wi-Fi Direct — exactly where hardware intercoms
  normally need their own radios, Rylaide uses what's already in every
  pocket.
- **Internet available? Even better.** The same conversation seamlessly
  switches to using the internet instead, so a group can stay connected
  even when they're miles apart, not just within radio range of each
  other.
- **Any headset works.** Rylaide doesn't try to replace a rider's Cardo
  or Sena headset — it just uses it as a normal Bluetooth headset (which
  every brand supports), or plain wired earphones if that's all someone
  has. The brand of hardware on someone's helmet stops being a barrier
  to who they can talk to.

Once riders are connected, Rylaide becomes more than a radio: it's also
a free navigation companion — a speedometer, turn-by-turn directions,
trip history, a pre-ride safety checklist, and fuel-mileage tracking —
all built on open, free map data instead of a paid mapping service.

---

## Who it's for

**Primary: everyday motorcycle and scooter riders who ride in groups.**
Weekend riding groups, daily commuters who ride with a friend, touring
groups — anyone who currently either does without group communication
entirely, or is locked into one brand's hardware ecosystem to get it.

**Specifically designed around how riding actually works, not how a
typical phone app is used:**
- Used with gloves on — every primary control is sized to be tapped
  without precision
- Used both day and night — the whole interface adapts automatically
- Used while glancing, not reading — information is big, simple, and
  immediate, not dense
- **Made to feel like the rider's own** — the theme (light, dark, or
  automatically switching with the time of day) and the entire app's
  accent color are both changeable by the rider in Settings, not fixed
  by the app. Two riders can run the exact same app and have it look
  genuinely different, tuned to their own taste, the same way riders
  already personalize their bike, their helmet, and their gear.

**Secondary, longer-term: fleets and organizations** — delivery riders,
tour operators, event/security teams — who need group coordination
without buying hardware for every rider, and who represent the project's
clearest path to real revenue once the free consumer app has real usage
behind it.

---

## How the app is structured, screen by screen

### Getting in
A rider opens the app for the first time, signs up or signs in, picks an
avatar/accent color and their vehicle during a short setup, and lands on
**Home**.

### Home — the hub
From Home, a rider can:
- **Start a ride** — choosing to ride solo (just navigation/trip tools
  for themselves) or with a group (the intercom, fully active)
- Check the **weather** before heading out
- Jump into their **garage** (saved vehicles), **fuel log**, **ride
  history**, **Discover** (places/routes), or **Safety** resources
- Open **Settings** to adjust theme, accent color, glove mode, and
  intercom preferences

### Starting a group ride
Choosing "ride with a partner" leads to **Groups** — create a new group
(gets a short shareable code, or a QR code to scan), or join one someone
else already started. Once in a group, there's a shared space with chat,
an activity log, and shared ride history — not just a connection that
exists only during the ride itself.

### Before setting off
Whether riding solo or with a group, the **pre-ride checklist** appears
next — a single adaptive list of safety items (helmet, gloves, lights,
fuel, and so on) that changes based on the vehicle, how long the ride is,
and whether it's day or night. The "Start ride" button won't activate
until everything relevant is checked.

### The ride itself — Ride Hub
This is where riders spend almost the entire ride, and it has two views
they can switch between:
- **Map view** — live navigation, turn-by-turn directions, the group's
  live positions, and the big open-mic button to talk — tap once to
  start talking, tap again to stop, no time limit, no button-holding
  required. An SOS button sits permanently in reach.
- **Speed-only view** — a full-screen speedometer (with a choice of
  digital, analog, or mixed gauge styles) plus ride stats, for when a
  rider wants to save battery or just doesn't need the map on screen.

### After the ride
The ride gets saved into **history** — distance, duration, route, speeds
— viewable any time, with a full map replay available for any past ride.

---

## What makes this different from existing options

| | Hardware intercoms (Cardo, Sena, etc.) | Generic walkie-talkie apps (Zello, etc.) | Rylaide |
|---|---|---|---|
| Cost | Hardware purchase per rider | Often free, sometimes subscription | Free |
| Works offline | Yes (own radio hardware) | No | Yes |
| Cross-brand | No | N/A (not rider-specific) | Yes |
| Built for riding | Yes | No | Yes |
| Navigation/trip tools included | Some, brand-dependent | No | Yes |

---

## The technology — and why it was chosen this way

This is worth spelling out on its own, because the technology choices
aren't incidental — they're a direct extension of the same idea that
started this whole project: **a rider's own phone should be enough.**
No proprietary radio chip, no required subscription, no paid API tier
standing between a rider and talking to their group.

**The intercom itself:**
- **Native Android (Kotlin) and iOS (Swift)** — not a cross-platform
  framework. The mesh networking and Bluetooth headset audio need direct
  access to the phone's hardware, which native code gets cleanly and a
  cross-platform layer would only get in the way of.
- **Wi-Fi Direct (Android) / Multipeer Connectivity (iOS)** — built into
  the phone's own operating system, this is what lets two or more
  riders' phones find and talk to each other directly with zero internet
  connection, no extra hardware, no cost.
- **WebRTC**, the same open-source audio engine used for both the
  offline mesh connection and the internet connection — one audio
  pipeline, so riders get the same voice quality whether they're
  connected directly or over the internet.
- **Standard Bluetooth (HFP)** for headset audio — this is the detail
  that makes Rylaide work with *any* brand of headset, or plain wired
  earphones. It's a universal phone feature, not something any one
  intercom manufacturer controls, which is exactly why it doesn't lock
  a rider into one brand the way hardware intercoms do.
- A small **signaling server** (just helps two riders' phones find each
  other over the internet — it never carries or stores any audio) and a
  **TURN relay** as a fallback when a direct internet connection isn't
  possible.

**The navigation and trip tools:**
- **OpenStreetMap** for map tiles and **OSRM** for turn-by-turn routing —
  both free, open, and keyless, instead of a paid mapping service. This
  is the same philosophy as the intercom itself: free should mean
  actually free, not free-until-you-scale.

**What this adds up to:** every core piece of this app - voice, maps,
headset support - runs on technology that's either built into the phone
already or free and open-source. That's not a cost-cutting afterthought,
it's the whole reason the app can promise to stay free for riders
permanently, rather than needing to eventually charge for the thing it
was built to give away.

---

## The business side, briefly

The core intercom stays free, permanently — that's the whole point of
the product. Revenue comes from optional extras on top (bigger groups,
extended history, priority connection quality) and, longer-term, from
licensing the underlying technology to fleets and organizations who need
group coordination at a scale individual riders don't.
