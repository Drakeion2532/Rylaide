# Rylaide — Deep Dive: How Everything Actually Works

This document goes one level deeper than the project brief. It's not
"what Rylaide does" — it's "what is actually happening, mechanically,
inside each part of the app." Read this if you want to understand the
app well enough to explain it to a developer, not just to a rider.

---

## 1. The intercom — how two phones actually talk to each other

### The core problem being solved
Phones don't have a built-in way to act like walkie-talkies. A phone
call needs a cell network and a phone number. Rylaide needed something
that works with neither — just two or more phones, physically near each
other or far apart, with the app installed.

### Step one: finding each other
Before any voice can flow, two phones need to discover that the other
one exists and is running Rylaide. This happens one of two ways:

- **No internet (offline mesh):** Android's Nearby Connections API (which
  itself is built on Wi-Fi Direct and Bluetooth) and iOS's Multipeer
  Connectivity framework both let a phone broadcast "I'm here, join my
  ride" and listen for other phones doing the same, entirely over local
  radio signals — no router, no cell tower, no internet account involved
  at all.
- **Internet available:** the phone instead talks to a small **signaling
  server** Rylaide runs. This server's only job is matchmaking — "rider A
  wants to connect to rider B" — it never touches or stores any actual
  audio. Think of it like a phone book that also makes the introduction,
  not like a phone line itself.

### Step two: actually connecting
Once two phones know about each other, they need to build an actual
audio connection. Rylaide uses **WebRTC** for this — the same open-source
technology that powers video calls in browsers — because it already
solves the hard problem of streaming real-time audio between two devices
efficiently, using a codec called **Opus** that's specifically designed
for voice at low data rates.

The important design decision: **the same WebRTC connection mechanism is
used whether the two phones are talking over the offline mesh or over
the internet.** The only thing that changes is *how* the two phones
exchanged their connection details (directly over the mesh link, or via
the signaling server) — once connected, it's the same audio pipeline
either way. This is why the app doesn't need two separate systems for
"offline mode" and "online mode" — it's one system with two ways of
getting started.

### Step three: handling the internet path's fallback (TURN)
When two phones are connecting over the internet, the ideal case is a
**direct connection** — audio flows phone to phone with no server in the
middle, which is fastest and cheapest. But many home/mobile networks sit
behind firewalls or network address translation (NAT) that block direct
connections. When that happens, WebRTC automatically falls back to a
**TURN relay** — a server that simply forwards audio between the two
phones when they can't reach each other directly. This happens
automatically; the rider never sees a difference except possibly a
slightly higher delay.

### The "which path right now" decision — the Path Selector
This is the single most important piece of custom logic in the whole
app. For every other rider a phone is trying to reach, it continuously
asks: *am I in mesh range of them right now? If not, is the internet
available?* The order matters — mesh is checked first because it's free
and has zero latency from a server round-trip, and only falls through to
the internet path if mesh genuinely isn't an option right now.

This check doesn't just run once at the start of a ride — it keeps
running throughout, because conditions change: a rider might ride out of
Wi-Fi Direct range of the group but still have cell signal, or vice
versa. When the right path changes mid-conversation, WebRTC performs
what's called an **ICE restart** — a way of swapping the underlying
connection path without ending and restarting the call. The rider might
notice a brief quality shift, but the conversation itself never drops.

### Reaching more than one other rider — multi-hop mesh
If rider A and rider E aren't close enough to connect directly over
mesh, but riders B, C, and D are all within range of their neighbors in
between, Rylaide can relay A's voice through B, then C, then D, to reach
E — each phone in the chain passing the audio along to the next. This
works, but every hop adds a small amount of delay, which is why there's
a practical ceiling of around 4-5 hops before the lag becomes noticeable
in conversation — this sets the realistic group size for mesh-only
riding (roughly 6-10 riders in a loose line) without the internet path
helping extend reach further.

### Cleaning up the audio before it's ever sent — VAD
Riding generates a lot of noise that isn't speech — wind buffeting a
helmet mic, engine drone, road noise. Before any audio chunk is sent to
another rider at all, a **voice activity detector (VAD)** running
entirely on the rider's own phone checks whether that chunk actually
contains speech. If it's just noise, it's dropped before it ever leaves
the phone — which means less data sent, less battery used, and a
cleaner, quieter experience for everyone else in the group. This runs
continuously while a rider is unmuted; it's invisible to them, they just
notice the audio sounds cleaner than a raw open mic would.

### Getting audio in and out of a rider's ears — Audio Route Manager
Separately from all of the above, there's the question of *which
physical device* is carrying the rider's voice in and out. Rylaide
checks, in this specific order:
1. **Wired headset or earphones, if one's plugged in** — these route
   automatically once the app tells the phone it's in a "communication"
   audio mode, no extra connection step needed at all.
2. **Bluetooth headset, if no wired option is present** — this needs an
   explicit step (called starting a Bluetooth SCO connection) to route
   audio to it, which is why it's checked second, after the simpler
   wired case.
3. **The phone's own speaker and microphone, as a last resort** — mainly
   useful for testing, not realistic for actual riding.

This is exactly why Rylaide doesn't care what brand of headset a rider
owns, or whether they have a headset at all — a Cardo, a Sena, a $5
wired earbud, or nothing, all work, because this logic only cares about
*which standard category* of audio device is present, never about any
manufacturer-specific feature.

### Open mic, not push-to-talk — and why that choice has real consequences
Rylaide uses an always-on "open mic" model: tap once to unmute, tap again
to mute, no time limit, rather than holding a button down to talk. This
mirrors how dedicated motorcycle intercom hardware already works, and
avoids the real problem of a rider fumbling to hold a button with a
gloved hand mid-sentence. The tradeoff, which the VAD system above exists
specifically to manage, is that an open mic uses meaningfully more
battery and data than push-to-talk would, since it's a sustained
connection rather than short bursts — which is also why the UI shows a
persistent, hard-to-miss "you're live" indicator whenever a rider is
unmuted, so no one forgets they're broadcasting.

### Staying connected automatically — reconnection
Both the mesh discovery system and the internet connection are designed
to keep listening/retrying in the background. If a rider rides out of
range and comes back later, or their internet drops and returns, the
app doesn't require them to do anything — the same discovery and
connection logic that found everyone the first time simply finds them
again.

---

## 2. The navigation and trip companion — how it actually works

### Why free maps don't mean worse maps
Rylaide's map tiles come from **OpenStreetMap**, a collaboratively built,
freely licensed map of the world — the same underlying data many
commercial map products are built on top of. Turn-by-turn routing comes
from **OSRM**, an open-source routing engine that calculates roads-based
directions the same way commercial routing services do. Neither requires
an API key or a credit card, which is the entire reason the app can
promise to stay free.

### The one real limitation of the free routing service, explained
The specific OSRM server Rylaide can use for free is a shared, public
one meant for reasonable personal/testing use, not a server meant to
handle heavy business traffic. It's genuinely fine for riders using the
app normally. The honest long-term plan, once usage is real, is to run
Rylaide's *own* copy of the same open-source OSRM software — which is
still free to license and run, just needs its own small server instead
of the shared public one.

### How GPS and maps are actually two separate things
A rider's live position comes from **GPS**, which is a signal received
directly from satellites — this works with zero internet connection,
always, on every phone. What *does* need either an internet connection or
advance preparation is the **map image** underneath that position dot.
Rylaide lets a rider pre-download the map tiles for a region before a
ride, so the live position dot keeps showing correctly on a real map even
with zero connectivity during the ride itself.

### How a trip actually gets measured and saved
While a ride is being tracked, the app isn't just storing raw GPS
coordinates — it's actively computing the distance traveled using the
**Haversine formula** (a standard way to calculate real-world distance
between two latitude/longitude points on a sphere), while filtering out
small GPS jitter (tiny, physically impossible jumps that happen from
GPS signal noise, not real movement) so the recorded distance doesn't
drift upward from noise alone. To keep storage small, a finished trip's
route isn't saved at full resolution — it's thinned down to roughly 200
representative points, enough to redraw a recognizable route on a map
later without storing every single GPS reading from the whole ride.

### How "how far along the route am I" gets answered cheaply
Knowing a rider's live progress along a planned route — for showing an
updated ETA, for example — could mean comparing their current position
against every single point on the route on every GPS update, which gets
slow as a route gets longer. Instead, the route is turned into a small
weighted graph (think of it as a simplified map of just the route itself,
where each segment's "cost" is its real distance), and a routing
algorithm called **Dijkstra's algorithm** - the same fundamental approach
real-world mapping services use at a much larger scale - finds the
shortest path from the route's start to the rider's current position.
That shortest-path distance *is* the rider's progress along the route,
and this calculation stays fast even on a long route because of how the
graph is structured, not despite it.

### How the pre-ride checklist decides what to show
Rather than maintaining several completely separate checklists for every
combination of situation, every single checklist item is tagged once
with which vehicle types, trip lengths, and times of day it applies to.
The checklist screen itself doesn't contain any hardcoded lists — it
just filters the one master list live, based on three toggles the rider
sets (vehicle, trip length, time of day), so adding or adjusting an item
in the future means editing it in one place, not hunting through several
near-duplicate lists.

### How the day/night toggle for the checklist actually gets its answer
Rather than asking the rider to manually say whether it's day or night,
this reuses the same real sunrise/sunset calculation described in the
theming section below - calculated from the rider's actual location and
the current time, entirely on-device, no internet call needed.

### How fuel mileage gets calculated without the rider doing any math
Each time a rider logs a fill-up (liters, amount paid, odometer reading),
the app doesn't ask them to calculate anything. From the second fill-up
onward, it automatically computes real mileage using the standard
"fill-to-full" method: the distance traveled since the last fill
(current odometer minus the odometer at the last fill), divided by how
much fuel was used this time. Price-per-liter is just amount paid divided
by liters, and the trend shown (e.g. "4% cheaper than last fill") is a
simple percentage comparison against the previous entry.

### How the gauge cluster is actually drawn
The analog speedometer isn't an image — it's drawn entirely in code,
which is what allows it to be redrawn in any accent color the rider
picks. The needle's position is calculated from the current speed as a
fraction of the gauge's maximum, mapped onto a 270-degree sweep (the
needle's resting range leaves the bottom of the dial clear specifically
so a digital speed readout can sit there without the needle ever
crossing in front of it — this is why the "mixed" gauge style works
visually without careful manual tweaking per screen size). A shaded
"redline" zone covers roughly the top 17% of the scale, same as a real
motorcycle dashboard.

---

## 3. Personalization — how a rider actually changes how the app looks

### The day/night switch, specifically
Rather than relying only on whatever dark/light setting a rider happens
to have set system-wide for their whole phone, Rylaide can determine day
or night from the rider's actual real-world situation - their current
location and the actual time - so the app can look right even if a
rider never bothered to change their phone's own system setting for
whatever time they happen to be riding at. A manual override still
exists for edge cases a clock can't predict - riding through a tunnel,
unusual weather darkness, personal preference regardless of actual time.

### The accent color, specifically
The color isn't a hardcoded value sprinkled through every screen's code
— every screen reads its color from one shared setting. When a rider
picks a different accent in Settings, that one value changes, and every
screen that reads from it — the gauge, the mic button, the checklist
checkmarks, the buttons — updates immediately and consistently, because
they were all built to ask "what's the current accent?" rather than
being told a fixed answer.

### Glove mode, specifically
This is a single on/off setting that controls the *size* of tappable
elements across multiple screens at once — the microphone button, the
SOS button, rider avatars — scaling them up together when it's on. It
doesn't change colors or layout structure, just the literal touch target
size, because the problem it solves is purely physical (a gloved
fingertip is less precise than a bare one), not visual.

---

## 4. How the pieces connect — the full architecture, end to end

```
Rider's phone
 ├─ Bluetooth/wired audio routing  → decides which physical mic/speaker to use
 ├─ Voice activity detection       → filters the mic signal before sending
 ├─ Mesh discovery                 → finds nearby riders, no internet needed
 ├─ WebRTC audio engine            → the actual voice connection, either path
 ├─ Path selector                  → decides mesh vs internet vs TURN, live
 ├─ GPS + map rendering            → always-on location, cached/online map
 └─ UI (theme, accent, glove mode) → reads shared settings, same on every screen

                    ⇕ (only when using the internet path)

Signaling server      → introduces two riders' phones to each other, no audio
TURN relay server     → fallback audio path when a direct connection fails
```

Everything above the dotted line runs entirely on the rider's own phone
and needs nothing external to function at all when riders are within
mesh range of each other. Everything below the line only comes into play
when the internet path is actually being used.

---

## 5. What's genuinely built versus what's still a plan

It's worth being precise about this, since "deep dive" should mean
accurate, not aspirational:

- **Genuinely working, tested code today:** the signaling server (runs,
  builds, responds to real requests), the Android project structure, the
  audio-routing decision logic (wired/Bluetooth/speaker), and the actual
  interactive screens for starting a ride, the in-ride view, and
  settings/theming.
- **Designed in detail, not yet built in code:** the mesh discovery
  connection itself, the WebRTC audio engine wiring, and essentially all
  of the navigation/trip/checklist/fuel-log features — these exist as
  real specifications and mockups, not yet as running software.
- **Visually designed in a separate session, not yet connected to any of
  the above:** the much larger screen set covering accounts, rider
  groups with chat, Discover, Safety, and the garage/vehicle system.

The mesh voice connection between two real phones - proving that two
riders can actually talk to each other with zero internet - is the
single most important unbuilt piece, and deliberately the very first
thing being tackled, before any of the richer features above it.
