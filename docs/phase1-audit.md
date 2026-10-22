# Phase 1 audit: voice and transport (state of main at commit cd897d0)

Scope: read-only audit. No production code was changed. CI on `main` is green (build + unit tests).
Naming: the repository is **Rylaide**. Nothing in it says "Trilyt", so this report uses Rylaide.

## 1. What actually exists

```
mic -> HighPass 200 Hz -> NoiseSuppressor (STFT, levels) -> mic gain -> VAD gate -> pre-roll -> AutoGain
    -> Opus 16 kHz mono, 16 kbps, 20 ms frames (Concentus) -> RelayPacket [type|origin|ttl|seq|body]
        -> MeshDiscoveryManager.sendAudio
             |- Nearby Connections (BYTES payloads, P2P_CLUSTER or P2P_POINT_TO_POINT, NON_DISRUPTIVE)
             |- InternetPath (UdpLink udp://host:port, or InternetLink WebSocket)   [only if a relay URL is set]
receive: Nearby / internet -> RelayRouter (dedupe per (type, origin), 128-slot window, TTL 4, loop drop)
    -> forward to other links (bridge) -> IntercomAudioEngine.enqueuePlayback
    -> per-origin JitterBuffer (adaptive 2..5 frames) -> Opus decode/PLC -> mixer -> AudioTrack (low-latency mode)
side channels on the same packets: PRESENCE (name, every 3 s), POSITION (GPS, every 2 s), PING/PONG (RTT, every 2 s)
```

Other pieces: `RideService` (foreground service, mic|location|connectedDevice, wake lock), `AudioRouteManager`
(wired / Bluetooth call / speaker), distance readout and drop-distance logging, two Node relays in `/server`
(UDP and WebSocket, both tested) and a Cloudflare Worker version of the WebSocket relay.

## 2. Working, unverified, and not built

| Item | Status |
|---|---|
| Offline voice between two phones over Nearby, with the foreground service | Worked on a Poco + realme pair at close range (round trip 70 ms avg, 146 ms worst, 0 drops, Build 20, no headset). |
| Same link in a road test with a Bluetooth headset (Build 17) | Round trips of 2 to 5 s and repeated drops. Cause not proven. |
| GPS distance between riders | Showed a farthest-linked distance of 50 m on phones. Accuracy not characterised. |
| Multi-hop relay (router, TTL, dedupe) | Unit and simulation tests only. Never run on 3 phones. |
| Internet path (UDP and WebSocket) | Code and local tests only. No relay deployed. Never used on a phone or on a mobile network. |
| Adaptive jitter buffer, PLC, noise levels, VAD modes | Unit tests. Real-world benefit of each is unmeasured. |
| `PathSelector`, `SignalingClient`, `WebRtcAudioEngine` | **Empty stubs** with TODO comments. Nothing calls them. They describe a WebRTC design that was never built. |
| `prototypes/signaling-server` (TypeScript, Render) | A 20-line `index.ts` plus empty folders. Not functional. |
| `HomeScreen`, `InRideScreen`, `MapManager`, `ios/` | UI and platform scaffolding, not wired to the working audio path. |
| Cellular voice call fallback, Wi-Fi Direct / Aware voice link | Not built. |
| Encryption and authenticated membership | Not built (see section 5). |

Notes for later phases: the working path is Opus over our own packets, not WebRTC, and the offline and
internet roads already share one engine. The stubs should be replaced or repurposed, not extended as written.

## 3. Latency (estimate, not a measurement)

The screen estimates about 185 ms without a headset. From the code: capture frame 20 ms, noise reducer 10 ms,
Opus frame 20 ms, link one-way about 35 ms (half of 70 ms), jitter prebuffer 40 ms (2 frames), playback buffer
43 to 60 ms. A Bluetooth headset adds about 100 to 200 ms on top. No true mouth-to-ear measurement exists yet.

Likely bottlenecks, in order:
1. **Nearby Connections under load.** It delivers in order, so a congested link queues voice for seconds
   (seen in Build 17). A backlog guard now skips voice at over 1 s round trip, but the queue is Nearby's own.
2. **Bluetooth headset audio** (SCO call link). Not fixable in the app.
3. **Fixed buffers**: jitter 40 ms and playback 60 ms are about 100 ms of the 185 ms.
4. **TCP head-of-line blocking** on the WebSocket path (the UDP path avoids this, unmeasured).

## 4. Device and Android constraints

- minSdk 26, targetSdk 34. Google Play now requires a newer target for new apps, so a raise is needed before release.
- Nearby Connections needs Google Play services, so it will not work on phones without them.
- Permissions: Bluetooth runtime permissions on Android 12+, NEARBY_WIFI_DEVICES on 13+, notification permission on 13+.
- Android 12+ rejects a microphone foreground service started from the background. Android 10+ silences a
  background mic. Xiaomi, realme and similar phones add their own battery killers.
- Radio sharing: Bluetooth, Nearby's Wi-Fi medium and Wi-Fi internet share 2.4 GHz. On some phones a Wi-Fi Direct
  or hotspot upgrade can interrupt the phone's own Wi-Fi internet, which affects "offline and internet at once".
  Not yet checked on the test phones.
- Wi-Fi Aware needs Android 8+ and hardware support. Which test phones have it is unknown (checkable with
  `PackageManager.FEATURE_WIFI_AWARE`).
- Tested phones: Poco M2007J20CI (Android 12), realme RMX2001 (Android 11), earlier an OnePlus 15.

## 5. Security and privacy risks (current state)

1. **Nearby auto-accepts** any endpoint advertising the same group code, without checking the authentication
   token. The code "TEST" is guessable. Anyone in radio range with the code can join and listen.
2. **No application-layer encryption or authentication.** On the internet path the relay and anyone who learns
   the room name see Opus audio, rider names and GPS positions in the clear. Packet origins and sequence numbers
   can be forged or replayed.
3. **UDP reflection risk.** The UDP relay registers a sender by source address, which can be spoofed, so an
   attacker could make the relay send room traffic to a victim. Needs a handshake token before forwarding.
4. **Location sharing** is always on while a ride runs and goes to every room member and the relay.
5. Battery: 1 s GPS updates and a 12 h wake lock while running. Acceptable for tests, not for normal rides.
6. The relay accepts any room name and has no rate limits beyond 32 riders per room.

## 6. Highest-risk unknowns

1. Whether the Build 17 link collapse came from Bluetooth coexistence, range, or Nearby's behaviour.
   (Needs a headset run with the round-trip readout.)
2. Real mouth-to-ear latency on each path and with each headset type.
3. Whether the UDP path survives mobile-carrier NAT, and its real latency and loss on mobile data.
4. Behaviour of the offline link when the internet path is also active on the same phone.
5. Multi-hop relay behaviour with 3 or more phones.
6. Which phones support Wi-Fi Aware, and whether a direct UDP link over Wi-Fi Direct or Aware would beat Nearby.
7. Cellular fallback (section 7B): not feasible as a seamless handoff.

## 7. Recommended architecture for Phases 2 to 4

**A. Internet voice.** Keep the UDP relay as the default and the WebSocket path as fallback. Do not adopt WebRTC
or LiveKit until measurements on a real mobile network show the UDP path is not good enough: replacing a working
component needs evidence. Add per-session authentication (section D) and measure first.

**B. Cellular voice through the SIM.** On ordinary Android there is no public API for an app to bridge its audio
into a carrier voice call, or to convert a running internet call into one. `TelecomManager.placeCall` needs the
CALL_PHONE permission and shows the normal dialer, and self-managed `ConnectionService` is for VoIP calls only.
VoLTE, VoNR and Wi-Fi Calling are carrier features that apps cannot drive. A group call would need the carrier's
conference service or a paid PSTN bridge (cost, and phone numbers would have to be shared). So: **do not build
automatic SIM fallback.** At most, an optional, explicitly consented prompt "no data: call [rider]" using the
dialer, as a safety aid. Mobile data over the SIM (what the internet path uses) is the supported way to use the SIM.

**C. Offline.** Keep Nearby for discovery and the first working link. Evaluate a direct UDP voice link over
Wi-Fi Direct or Wi-Fi Aware (phones that support it) to remove Nearby's in-order queue, only if measured round
trips with Nearby stay bad. Define a device matrix by testing `FEATURE_WIFI_AWARE`, Wi-Fi Direct and concurrency
on each test phone.

**D. Session and transport abstraction.** Introduce a `RideSession` (membership, keys, rider identities) over a
small `Transport` interface (`send`, `receive`, `health`, `availability`), with Nearby, UDP and WebSocket as
implementations. Reuse the existing `RelayPacket` and `RelayRouter` as the common layer.
Encryption: a per-ride secret shared through the invite (QR or code), HKDF-derived keys, AEAD per packet
(nonce from origin and sequence), and a signed token for the relay handshake. This also closes risks 1 to 3.

**Transport selection.** Do not switch exclusively between paths. The app already sends on every available
path and drops duplicates, which gives make-before-break by default. The selector's job becomes health scoring
(round trip, loss, jitter, age of last packet), hysteresis to avoid flapping, stopping sends on a clearly dead
or congested path (as the backlog guard does), and showing the rider the current state:
`BOTH`, `INTERNET`, `MESH (direct or N hops)`, `DEGRADED`, `DISCONNECTED`.

**Phase 4 measurements to add first:** a loopback audio latency test per device and headset, one-way latency
estimates from timestamps, loss and jitter counters per path, reconnect-time logging.
Proposed targets (to be validated, not promised): wired, close range, offline: mouth-to-ear under 250 ms at
the 95th percentile; internet on LTE: under 350 ms; reconnect after a drop: under 5 s.

## 8. Features that must not regress
Foreground service and mic-silenced detection, 1 to 4 s connect time, relay router dedupe, backlog guard,
distance readout and drop logging, noise levels, adaptive jitter buffer, existing unit tests and CI.

## 9. Decisions for you
None blocks Phase 2. Defaults I will use unless you say otherwise: keep the name Rylaide, keep UDP as the main
internet path, no automatic cellular fallback, and design encryption before building the session layer.
