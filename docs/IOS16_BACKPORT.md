# Tower iOS 16 compatibility branch

This document describes the compatibility work on `WhoJave/tower:ios16-compat`, based on the existing fork's `main` branch. It does not claim an Xcode build or a working IPA.

## Changes so far

- Lowered every app/test `IPHONEOS_DEPLOYMENT_TARGET` from 17.0 to 16.0.
- Replaced iOS 17 Observation (`@Observable`, type-based environment injection and `@Bindable`) with `ObservableObject`, 59 `@Published` stored properties, `@StateObject` and `@EnvironmentObject`.
- Added `Tower/Design/TowerIOS16Compat.swift`: two-value `onChange` modifier and optional sensory-feedback fallback. iOS 17 keeps native haptic feedback; iOS 16 currently omits haptic effects.
- Replaced iOS 17-only symbol content transitions with opacity and numerical text transitions using the iOS 16-compatible overload.
- Updated source-oriented regression tests to recognize the compatibility wrappers.

## Build verification (must run on a Mac with a recent Xcode)

From the repository root:

```sh
git checkout ios16-compat
export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
xcodebuild -version

# Availability/type-check and build against the newest SDK, with iOS 16 deployment target.
xcodebuild -project Tower.xcodeproj -scheme Tower -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath .derived-data-ios16 CODE_SIGNING_ALLOWED=NO build

# Run tests on an installed iOS 16 simulator runtime, if one is available.
xcrun simctl list devices available
xcodebuild -project Tower.xcodeproj -scheme Tower -configuration Debug \
  -destination 'platform=iOS Simulator,name=<iOS 16 simulator name>' \
  -derivedDataPath .derived-data-ios16 CODE_SIGNING_ALLOWED=NO test
```

For a physical iOS 16.7.x iPhone, select the device in Xcode, provide your own development Team and unique Bundle ID if needed, then build/run and test the following: launch/onboarding, importing and refreshing subscriptions, filtering, rule schemes, profile generation, QR/file sharing, background/foreground persistence, LAN import and optional iCloud sync. Do not remove the original installed app unless you have backed up its data.

## Known caveats

1. **Not verified by Xcode or device tests** in the environment where this branch was prepared. A GitHub commit is not proof of a successful iOS 16 build.
2. `ObservableObject` invalidates more views than Observation's fine-grained tracking; profile with 1,000/5,000 nodes, particularly on older devices.
3. On iOS 16, native swipe rows are always kept alive because geometry-based scroll viewport bounds need iOS 17; check scrolling performance on older iPhones.
4. The backported two-value `onChange` uses a stateful modifier and can differ from iOS 17's timing on view identity changes. Validate sheet presentation and background transitions.
5. This branch is based on the already-existing fork's main commit, not the newer upstream main. Syncing upstream later may require conflict resolution and repeating availability checks.
6. To install an iOS 16-compatible app, lowering the deployment target is not enough; an Xcode build, code signing and device launch are still required.
