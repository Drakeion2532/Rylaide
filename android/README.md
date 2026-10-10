# Rylaide - Android

## Open this project

1. Open Android Studio
2. Open this `android/` folder as a project (not the whole repo root)
3. Let Gradle sync - it will download the dependencies listed in
   `app/build.gradle.kts` (Nearby Connections, WebRTC)

## What's actually implemented vs stubbed

- `audio/AudioRouteManager.kt` - **real, working logic**. Detects whether a
  wired headset (3.5mm or USB-C), a Bluetooth headset, or nothing at all is
  connected, and picks the best one automatically - wired is preferred
  when present, since it needs no explicit connection step at all.
- `audio/BluetoothAudioManager.kt` - **real, working logic** for the
  Bluetooth SCO connection specifically (used when `AudioRouteManager`
  decides Bluetooth is the best available route).
- `ui/HomeScreen.kt`, `ui/InRideScreen.kt`, `ui/RylaideApp.kt` -
  **real, working UI**, previewable directly in Android Studio (open any of
  these files and use the Split/Design view), and clickable end to end
  when run on a device or emulator.
- `mesh/MeshDiscoveryManager.kt`, `audio/WebRtcAudioEngine.kt` - still
  `TODO`. This is the real remaining work of sprint 1: get two phones
  discovering each other and streaming audio between them.
- Everything else (`pathselector`, `signaling`, `maps`, `data`) stays
  stubbed until later sprints.

## Wired earphone support

`AudioRouteManager` checks for a wired accessory first, Bluetooth second,
phone speaker as a last resort - so a rider with plain wired earphones (no
Bluetooth headset at all) can use the app too. Nothing extra to configure;
whichever is actually plugged in wins automatically.

## Permissions note

Android 12+ requires runtime permission requests for Bluetooth and nearby
Wi-Fi devices, not just manifest entries - you'll need to request these at
runtime in `HomeActivity` before starting mesh discovery, or Nearby
Connections will silently fail to find anyone.
