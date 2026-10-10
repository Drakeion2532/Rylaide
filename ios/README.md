# Rylaide - iOS

## This folder is source files only, not a full Xcode project yet

Unlike the Android side, an Xcode project has a binary-ish project file
that isn't practical to hand-generate outside Xcode itself. To get started:

1. Open Xcode, create a new iOS App project named `Rylaide`
   (SwiftUI, Swift)
2. Delete the default `ContentView.swift` Xcode generates
3. Drag this entire `Rylaide/` folder (Audio, Mesh, Signaling,
   PathSelector, Maps, UI) into your new Xcode project, checking
   "Copy items if needed"
4. Set `HomeView` as your app's starting view in your `App` struct
5. Add the GoogleWebRTC package via Swift Package Manager:
   `https://github.com/stasel/WebRTC` (a commonly used pre-built distribution)
6. Add your chosen maps SDK (Mapbox or HERE) via Swift Package Manager too

## Required Info.plist entries

Xcode won't let you submit or even run on-device without these - add them
under your target's Info tab, each with a clear rider-facing explanation:

- `NSBluetoothAlwaysUsageDescription`
- `NSMicrophoneUsageDescription`
- `NSLocationWhenInUseUsageDescription`
- `NSLocalNetworkUsageDescription` (required for Multipeer Connectivity)

## What's stubbed vs real

Same situation as the Android side - this compiles conceptually and
matches our architecture, but module bodies are `TODO`. iOS is explicitly
out of scope for sprint 1 (Android-first, per the sprint plan) - come back
to this once the Android mesh audio prototype is proven out.
