# Testing

## Automatic (CI, on every pull request and push to `main`)

| Check | Command | Catches |
|---|---|---|
| Release script tests | `python3 -m unittest discover -s scripts/tests` | Version maths, keystore-secret decoding |
| Unit tests | `./gradlew testDebugUnitTest` | Logic bugs in plain-Kotlin classes (`app/src/test/`) |
| Lint | `./gradlew lintDebug` | Android API misuse, missing translations, accessibility, hard-coded text, … (errors fail the build) |
| Debug build | `./gradlew assembleDebug` | Compile errors; produces the APK to test on the phone |
| Release build | `./gradlew assembleRelease` | Problems caused by R8 minification / resource shrinking |

Reports (test results, lint HTML) are attached to every CI run as the `reports` artifact, also
when the run fails.

Run all of it locally before pushing:
```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
python3 -m unittest discover -s scripts/tests
```

### Writing unit tests
- Every logic change gets a test. Put logic in plain Kotlin classes (no Android types) and test
  them with JUnit in `app/src/test/kotlin/`, mirroring the package.
- What is covered: shared text and link cleaning (`SharedTextTest`), page tags and title cleaning
  (`PageMetadataTest`), TVmaze parsing and regional matching, building a new favorite, landscape pictures (`LookupTest`),
  air date choice, refresh timing and time zones (`AiringTest`), service detection, finding the TV
  app and the order of links tried (`DeepLinksTest`), reordering, grouping and the group order (`OrderingTest`),
  pairing codes (`PairingTest`), cookie and privacy walls (`ConsentGateTest`), home screen rows, finding them again and updating tiles in place (`ChannelPlanTest`), short and long presses of OK (`LongPressTest`), the local list file and
  stored channel ids (`LocalLibraryStoreTest`), backup rules (`BackupRulesTest`).
- Not covered by unit tests (needs a device): Firestore sync, the QR scanner, starting other apps,
  the TV home screen. Check those by hand (below).
- Test names describe behaviour in words: `` fun `tap stops at the maximum`() ``.
- No device tests (instrumented/UI tests) are set up: they need an emulator, which makes CI slow and
  flaky. Add them only if a decision in `decisions.md` says so.

## Manual (on the phone)

What automation can't check: how it looks and feels on a real device. For each pull request (before or after it is merged: merging only waits for green CI),
install the debug APK (see README, "Testing a pull request") and follow the steps in the pull request
description. In addition, check every now and then (always before a release):

- [ ] The app starts, and the changed screen looks right.
- [ ] **Dark mode**: switch the phone's dark theme on/off. Text stays readable.
- [ ] **Rotation** (if auto-rotate is on): the screen still works and keeps its state (e.g. a half-edited favorite).
- [ ] **Share** a show from VRT MAX, VTM GO, GoPlay and Netflix: title, picture and service are right.
- [ ] **TV**: pair with the phone, open a favorite of each service, move one with ◀ ▶, check the
      home screen row "All" and add a service row.
- [ ] **Language**: Settings → System → Languages → App languages → this app → Nederlands.
      All text is translated.
- [ ] **Large text**: Settings → Display → Font size at maximum. Nothing is cut off.
- [ ] **Back** gesture/button behaves as expected.
- [ ] After a release: install the release APK over the previous release. It updates without
      losing data.
- [ ] **Backup** (once the app stores settings): change a setting, Settings → search "Backup" →
      Back up now, uninstall, install the same APK again. The setting is back.

## Reading a crash from a release build
Release builds are minified, so stack traces show short names like `a.b.c`. Download
`<app>-<version>-mapping.txt` from the matching GitHub Release and decode the trace with R8's
`retrace` (Android SDK `cmdline-tools/latest/bin/retrace`):
```sh
retrace <app>-<version>-mapping.txt stacktrace.txt
```
Or ask Claude Code to do it: give it the stack trace and the version.
