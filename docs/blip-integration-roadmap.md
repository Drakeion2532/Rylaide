# Blip → Rider Intercom integration roadmap

How Blip's navigation/trip-tracking feature set gets ported into Rider
Intercom's native Kotlin/Compose (Android) + Swift/SwiftUI (iOS)
architecture. Nothing here is a direct code copy - Blip is Flutter/Dart,
Rider Intercom is native - every phase below is a *port of the design*,
rewritten in the right language, keeping the mesh/Bluetooth architecture
we already built intact.

---

## Phase 0 — One decision to make before UI work starts

**Design language: reskin Blip's features to match Rider Intercom's
current look, or unify around Blip's "supercar dash" aesthetic app-wide?**

This doesn't block Phase 1 (pure logic, no UI), but it does block Phases
4 onward (gauges, checklist, fuel log all need a visual identity). Worth
deciding once you've seen both in front of you rather than guessing now -
flag it back to me whenever you're ready and I'll build a quick side-by-side
comparison the same way we did for the earlier UI options.

---

## Phase 1 — Free maps & routing foundation

Replaces the still-empty `maps/MapManager.kt` stub.

- **Map tiles**: OpenStreetMap tiles via an open-source Android tile
  renderer (osmdroid is the standard native equivalent of Flutter's
  `flutter_map` package) - free, no API key, matches the "keep it free"
  priority better than the Mapbox/HERE suggestion from earlier.
- **Routing**: OSRM, same as Blip - **but with a caveat worth building in
  from day one**: the public OSRM demo server is explicitly restricted to
  "reasonable, non-commercial use" with no uptime guarantee and access
  that can be withdrawn at any time. Fine for development and early
  testing; **not fine once real revenue is involved** (directly conflicts
  with the B2B/fleet licensing path from our revenue discussion). Since
  OSRM is open-source, the real fix later is self-hosting it against the
  same free OpenStreetMap data - worth planning for, not urgent yet.
- **Reverse geocoding**: OpenStreetMap's Nominatim service (same
  free/keyless family as OSRM) - has a similar fair-use policy (roughly
  1 request/second, requires a proper User-Agent identifying your app,
  not meant for bulk/heavy use). Same self-hosting escape hatch applies
  later if needed.

Why this comes first: every later phase (trip history, checklist context,
fuel log location tagging) assumes a working map/location layer underneath
it.

---

## Phase 2 — Trip recording & history

Fills in the already-stubbed `data/RideHistoryStore.kt`.

- Port Blip's trip data model directly (start/end time, from/to labels via
  reverse geocoding, distance via Haversine with jitter filtering,
  max/avg speed, thinned route sample capped around 200 points/trip to
  keep local storage light).
- Port the destination-arrival detection (proximity radius, default ~40m,
  triggers a system notification) - straightforward with Android's
  location APIs.
- History screen: list of past rides, tap into a full summary - this
  slots in naturally next to the ride-summary screen concept from our
  earlier screen-flow design.

Why this comes second: it depends on Phase 1's location layer, and every
other new screen (checklist, fuel log) benefits from trip context already
existing.

---

## Phase 3 — Real day/night detection

Upgrade to `Settings.kt` (the `ThemeMode`/`isNightTimeNow()` logic we
already built for the mesh app).

- Replace the current fixed 7pm-6am clock boundary with Blip's actual
  approach: on-device sunrise/sunset calculation (NOAA solar position
  formula) using the rider's real lat/lng and system clock - no API call,
  works fully offline, objectively more accurate than a fixed boundary.
- Keep the manual Light/Dark override exactly as it is now - Blip has the
  same "override for edge cases" pattern (tunnels, unusual lighting), so
  no redesign needed there, just a better Auto calculation underneath it.

Why this comes third: small, self-contained, immediately improves
something that already exists rather than adding new surface area.

---

## Phase 4 — Gauge system & display modes

New UI, depends on the Phase 0 design decision.

- Speed gauge, three styles: digital (numeral only), analog (custom-drawn
  dial via Compose `Canvas`, ported from Blip's custom-painted approach -
  bezel ring, tick marks, tapered needle, redline zone), and mixed (analog
  dial with a digital readout inset in the zone the needle never sweeps).
- Display mode toggle: full map view vs. speed-only view (battery/data
  saving) - fits naturally alongside the existing `InRideScreen`, either
  as a mode switch on that same screen or a distinct screen reached from
  it.
- Unit toggle (km/h ↔ mph), straightforward, same UI pattern as the
  theme/glove-mode chips already built into `HomeScreen.kt`.

---

## Phase 5 — Pre-ride checklist

New screen and data model, no dependencies on earlier phases besides
Phase 0's visual direction.

- Port Blip's tagged-item design directly - one filtered list rather than
  N hardcoded lists, driven by three toggles (vehicle type, trip length,
  time of day), the last of which can reuse Phase 3's day/night detection
  instead of computing it separately.
- Gates the "start ride" action, same as Blip.
- One open question inherited from the spec, not yet resolved there
  either: whether jacket/gloves should be "recommended" rather than
  "required" for a short daily commute - worth deciding before this phase,
  not during it.

---

## Phase 6 — Fuel log & mileage tracking

New screen and data model, fully independent of every other phase -
can be built any time after Phase 0.

- Port the fill-to-full mileage calculation and price-per-liter/price-trend
  logic directly - it's pure arithmetic, no Dart-specific behavior to
  translate.

---

## Phase 7 — Motion polish

Once the above screens exist, port Blip's transition philosophy rather
than leaving Android's default abrupt navigation:

- A shared fade+slide screen transition (Compose's `AnimatedContentTransitionScope`
  gives you this natively) instead of the default screen-to-screen cut.
- Crossfade between display modes and gauge styles instead of hard swaps.
- Animated (not instant) color transitions when the day/night theme
  changes.

Left for last deliberately - it's the kind of thing that's easy to add
once the screens it's animating already exist, and wasted effort to build
early against screens still likely to change.

---

## Phase 8 — Infrastructure, only once real usage justifies it

- Self-host OSRM and a tile server against the same OSM data extract, if
  usage ever approaches what the free public demo servers are meant for -
  both are open-source specifically to make this possible.
- Move from the 200-point thinned trip storage to SQLite if full-resolution
  route replay becomes something riders actually want.

---

## Suggested order summary

```
0. Design decision (unblocks 4-6 visually)
1. Free maps & routing        -> unblocks 2, 5
2. Trip recording & history
3. Real day/night detection   -> feeds into 5
4. Gauges & display modes
5. Pre-ride checklist
6. Fuel log
7. Motion polish
8. Self-hosted infra (later, only if needed)
```

Phases 1-3 are foundation and logic-heavy, low visual risk. Phases 4-6 are
where the Phase 0 design decision actually matters. Phase 7 is polish, and
Phase 8 is a "when you actually need it" item, not a launch blocker.
