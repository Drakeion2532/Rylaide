# Offline rider link: findings and plan

Goal: riders stay connected and hear each other whatever the distance, group
size or interference, as far as physics allows. This file records what we
measured, what the phones limit, and the order we fix things in.

## 1. What Build 13 showed (Poco on Android 12, realme on Android 11)

- Both phones logged 4 dropped links in a few minutes, with 8011 and 8012 errors
  and connect times from 7 s to 52 s.
- Link delay: about 200 ms round trip on average, spikes of 630 to 680 ms.
- The link speed code moved between 3 (high) and 2 (medium), and each drop sat
  next to one of those changes. Nearby seems to be switching between Bluetooth
  and Wi-Fi and breaking the link when it does. This is a pattern in the logs,
  not a proven cause. Build 14 tests it.
- "Lost and patched: 0" only counts packets lost while connected, so it hides
  gaps caused by a dropped link. Use "dropped links" instead.

## 2. Phone limits (checked or flagged)

| Limit | Status |
|---|---|
| Nearby's own range is about 100 m per Google. We saw more than 200 m in the open | Checked: [Google strategies page](https://developers.google.com/nearby/connections/strategies) |
| Group mode (Cluster) is the lowest-bandwidth Nearby mode. Point-to-point is the highest but allows one connection | Checked, same page |
| Nearby can change the medium under a live connection. The library has a "non-disruptive" type, a "no disruptive upgrade" flag and a low-power (Bluetooth) mode | Checked in the library itself (19.3.0) |
| Wi-Fi Aware (direct phone-to-phone Wi-Fi) needs Android 8+ and support in the phone's own hardware and firmware | Checked: [Android docs](https://source.android.com/docs/core/connect/wifi-aware). Which of our phones have it is not known yet |
| Bluetooth and Wi-Fi usually share one 2.4 GHz radio on a phone, so a Bluetooth headset, the phone link and a connected Wi-Fi router can fight for air time | General knowledge, not measured by us. Build 14 tests it |
| A helmet, a body and a pocket all cut radio range. Other 2.4 GHz traffic adds noise | General knowledge. Test with the phone on the handlebar and in a pocket |

No app can promise "never disconnects, any distance". Radio range ends. What
we can do is keep the link steady inside range, reconnect fast, pass voice
through other riders, and fall back to the internet when there is signal.

## 3. Design: four layers

1. **Direct link** between phones (Nearby now). Fix drops, make reconnect fast.
2. **Relay through other riders.** Each phone forwards voice it hears to its
   other connections, with a hop limit and duplicate check, so a rider at the
   back reaches the front through the riders between them. Needs an origin id
   in each voice packet and one voice stream per speaker rather than per
   neighbour. **Built in Build 15** (our own design, no Serval code). Voice and
   "presence" messages carry an origin id, a hop limit of 4 and a sequence
   number. A phone plays a new message, passes it to its other connections and
   drops copies it already saw. Each hop adds roughly the single-link delay
   (about 100 ms one way in Build 13), so four hops is around 400 ms.
3. **Online fallback** (LiveKit, plan step 10) when riders are too far apart
   but have mobile signal.
4. **Graceful loss.** When a rider drops out, say so ("Asha lost, rejoining"),
   keep the others talking, rejoin by itself and say when they are back.

## 4. Serval Mesh (what we take from it)

Serval does phone-to-phone voice with no infrastructure, which is our idea, so
it is a useful reference for multi-hop relay. We should not copy code: its app
is GPL-3.0 and its core is GPL-2.0 ([Serval on GitHub](https://github.com/servalproject/batphone)),
which would force our whole app open under those terms. Its mesh mode also
needs a rooted phone and the project looks old and experimental. We use the
idea, not the code.

## 5. Test plan for Build 14

Same two phones, same spot, 5 minutes each. Write down "dropped links" and
"Link delay" every time.

| Run | Link type | Fast link | Headset |
|---|---|---|---|
| A | Auto | off | none |
| B | Steady | off | none |
| C | Bluetooth only | off | none |
| D | best of A to C | on | none |
| E | best of A to C | off | Bluetooth headset |
| F | best of A to C | off | Wi-Fi on and connected to a router, then Wi-Fi off |

## 6. Noise reduction levels

The wind and noise reducer now has Off, Low, Medium and High. The idea is to
offer the higher levels as a paid feature later ("Rylaide Plus"). That needs
Google Play Billing and conflicts with the earlier "nothing is paid" decision,
so nothing is gated yet and billing is not built. Decide this before Step 16.

## 7. Relay tests

Done on a computer (25 tests, run on every push): message format, duplicate
and wrap-around handling, and whole-group simulations. Results: voice crosses
a line of five riders and stops at the hop limit; a ring plays every packet
exactly once with no loops; a full group of six stays correct with everyone
talking; a second path beats a single path under 20% packet loss; a rider
dropping out cuts off only those behind them, and a ring routes around them.
These prove the logic. They say nothing about the radio.

Still to do on phones (needs 3 phones, or 2 phones plus a friend's):

| Run | Setup | Pass if |
|---|---|---|
| R1 | A and B in range of each other, B and C in range, A and C out of range | A hears C and C hears A, "Riders you can hear" shows C as 2 hops on A |
| R2 | R1, then B walks away | A and C stop hearing each other, and the screen shows it within about 10 s |
| R3 | All three in range of each other | Nobody hears anyone twice, no echo |
| R4 | R1 with a Bluetooth headset on B | Voice still passes through B |

Use the best link type from the Build 14 test. The relay needs the group
mode (the fast 1-to-1 link allows a single connection, so leave it off).
