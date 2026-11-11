# Decision log

Newest first. Add an entry for every notable choice: what was decided, why, and what was rejected.

## 2026-11-01: Claude Code sessions don't build the APK before pushing

Sessions ran `./gradlew testDebugUnitTest lintDebug assembleDebug` before every push, and CI then
runs the same and more. The APK built in the session was never used: the owner installs the one
from CI. Sessions now run only the unit tests and lint, which catch nearly every problem and
compile the app's code and resources. CI still builds the debug and release APKs, so a packaging
problem still turns the pull request red. Rejected: no local checks at all (every test or lint
failure would then cost a CI round trip).

## 2026-10-17: Design review across the apps: one way to reorder, readable widths, shared wording

A Material 3 review of every app built from this template found the same things done in different ways. Lists in the user's own order used four controls (drag handles, a long press, chevrons and arrows), even within one app; navigation bars mixed filled and outlined icons differently; most lists stretched across tablets although `DESIGN.md` asked for about 640dp; English said "Log out" next to "Sign in" and "Dismiss" where other apps said "Close"; and one app removed a workspace (with its presets) without asking. New shared rules in `DESIGN.md`: "Reordering" (a drag handle at the end of the row, `Reorder.kt`; a long press only for grids of cards), filled selected and outlined unselected navigation icons, `Modifier.readableWidth()` (`Layout.kt`), and wording for Close, Done, Sign out, Reorder and Move up / Move down. The two new kit files are copied into every app (Snoozer, without lists, takes only `Layout.kt`). The template now ships the same six languages as the apps. Rejected: up and down arrows for short lists (easier to build, but a second way to do the same thing) and a long press on list rows (hidden: nothing on screen says the row can move).

## 2026-10-17: Help text only where the screen doesn't speak for itself

The owner found the apps over-explanatory. A scan of every hint, summary and help paragraph removed text that restated a label, described standard controls (drag handles, tapping a row) or explained the app's design, and shortened the rest to what the screen can't show: consequences, where data goes, unfamiliar terms. `DESIGN.md` ("Wording") now says so, and the Dynamic color supporting text is one shared phrase without the app's colour name. Rejected: removing all help text (consequences and privacy notes are information, not decoration) and tooltips or info buttons instead (hidden text people rarely open, and more to translate).

## 2026-10-16: One empty state for every app, setup as a full-screen state

A second Material 3 audit found empty screens drawn three different ways: an icon in a tinted circle with a title (Tracker for Simkl), a title and text whose icon didn't show (RSS Reader), and a grey icon with one line of text (Noter for Notion). Apps that can't work until something is set up showed it differently too: Time Tracker for Notion put a small banner above a disabled timer, Noter filled the screen, Snoozer did both. Material 3 asks an empty state to say what it means and offer the next useful step, so the shared kit gained `ui/theme/EmptyState.kt` and `DESIGN.md` a section "Empty and setup states": one centred layout for empty, no-result, failed and setup-needed screens, with setup-needed replacing the screen's content. The same audit added two smaller rules: button labels name the action in as few words as possible ("Gezien", not "Markeren als gezien"), and rows that scroll sideways keep one height. Rejected: illustrations per screen (more to draw and translate for each app, and Material 3 warns against decoration that hides the next step) and keeping the banner for setup (easy to miss, and it leaves a screen full of controls that do nothing).

## 2026-10-16: Settings are one list; top-level navigation is a docked bar

An audit of the apps against Material 3 found two structural problems. Settings were a stack of collapsed cards, so even a single switch took two taps to find and every section was boxed in a card, which Material 3 lists as an anti-pattern. Settings are now one list grouped by section headers, with `ListItem` rows (switches apply at once) and only setup instructions folded away (`SettingsKit.kt`: `SettingsSection`, `SwitchSetting`, `SettingItem`, `ExpandableSetting`, `SettingsBlock`). Some apps put their top-level sections in a floating pill bar; Material 3 Expressive reserves floating toolbars for actions on the current screen, and keeps the navigation bar docked for destinations, so the shared rules now require the docked `NavigationBar`. `DESIGN.md` gained a "Navigation and structure" section. Rejected: a settings home page that links to sub-pages (more taps for apps with a handful of settings) and keeping the collapsible cards with everything expanded (still a card per section).

## 2026-10-15: Android's backup copies settings only, from an allow list

Users expect their settings back after a reset or on a new phone, and Android does that for free with its own backup (Google account) and phone-to-phone transfer. The template now lists exactly one file, the DataStore `backed_up_settings`, in `data_extraction_rules.xml`; everything else stays on the phone. An allow list means a file added later (a sign-in token, device state) is never backed up by accident, and `BackupRulesTest` fails if the rules drift. Rules for what goes where are in `docs/architecture.md`, "Backup". Rejected: backing up everything with exclusions (one forgotten exclusion leaks a token into the backup), switching backup off (users lose their settings) and our own backup to Google Drive (needs network access, sign-in and more code for what Android already does).

## 2026-10-15: Phone testing doesn't block merging

The owner can't review code and can't always test a pull request on the device right away, so pull requests waited for a manual test that adds little over CI. Green CI (unit tests, lint, builds) is now enough to merge; the owner tests afterwards with the steps from the pull request and always before a release, and problems are fixed in a new pull request. Merging to `main` does not reach users: releases stay manual. Rejected: keeping the phone test as a merge gate (pull requests pile up and go stale).

## 2026-10-15: Code is "all rights reserved" by default

Without a `LICENSE` file the legal default is already all rights reserved, but that isn't visible and is easy to misread for a public repository. The template now ships an explicit all-rights-reserved `LICENSE`, so every app starts strict and the owner opts in to sharing. Switching to a permissive licence (MIT or Apache-2.0) is a one-file swap, described in the README. Rejected: a commented-out alternative inside `LICENSE` (GitHub and licence scanners read the whole file, so it would be misdetected) and starting with MIT (opening up later is easy, taking it back for code already shared is not).

## 2026-10-14: Shared design rules have their master copy in the template

`DESIGN.md` was kept identical in the three sibling apps by hand, with no single place to start a change, and the copies had drifted (Snoozer was missing the Loading section; the Tracker for Simkl icon row was out of date in two apps). The android-app template (`wimvdon/android-app`) now holds the master copy; each app keeps a full copy so Claude sessions can read the rules without opening another repository, and only its "This app" section is its own. Rejected: keeping the file only in the template (sessions in an app would have to fetch it every time) and a shared library for the design kit (a new dependency and more setup than three small apps need). The design kit files (`ui/theme/`) stay in the apps.

## 2026-10-13: Template baseline

- **Language/UI:** Kotlin + Jetpack Compose + Material 3, single `app` module. This is Google's
  recommended, mainstream stack with the most documentation and examples.
- **Tool versions (latest stable on this date):** Gradle 9.8.1, Android Gradle Plugin 9.4.1
  (uses its built-in Kotlin support, so no separate `kotlin-android` plugin), Kotlin 2.4.21
  (Compose compiler plugin), Compose BOM 2026.09.00. Dependabot keeps these current.
- **minSdk 31 (Android 12).** The owner installs apps on their own, recent phone, so older
  Android versions aren't worth the extra code and testing. Android 12 brings Material You dynamic
  colour (no fallback code path), the modern splash screen and per-app language settings. It still
  covers the large majority of active devices. Lower it only if someone with an older phone needs
  the app.
- **compileSdk / targetSdk 37 (Android 17)**, the latest stable API level. The minor SDK release
  37.2 exists but isn't needed by anything yet.
- **Java 17 bytecode, JDK 21 to build.** JDK 21 is what Claude Code cloud sessions provide, so local
  checks match CI exactly.
- **Version numbers:** release `X.Y.Z` → `versionCode = X*10000 + Y*100 + Z`, passed in by the release
  workflow. All other builds use `git describe --tags --long` (e.g. `1.2.0-3-gabc1234`), falling back to
  `0.0.0-dev`, so a dev build can never be mistaken for a release. Version logic lives in
  `scripts/release_version.py` (unit tested) and `app/build.gradle.kts`.
- **Debug builds use `applicationIdSuffix = ".debug"`** so a pull request's APK installs next to the
  released app instead of conflicting with it.
- **Releases are GitHub Releases with a signed APK**, published by `gh release create` (preinstalled
  on runners) instead of a third-party action, which means one fewer dependency with write access.
  The R8 mapping file is attached to each release.
- **Signing secrets** are base64 and decoded by a tolerant, tested script (`scripts/decode_keystore.py`)
  that never prints the secret. The key password is checked with a small Java program
  (`scripts/VerifySigningKey.java`), because `keytool` ignores the key password of PKCS12 keystores and
  the build would otherwise fail later with an unclear error.
- **Caching:** `gradle/actions/setup-gradle` with `cache-provider: basic` (MIT licensed). The default
  "enhanced" provider is closed source under separate terms of use.
- **CI artifacts:** the debug APK is uploaded unzipped (`archive: false`) so it can be installed on a
  phone straight from the download.
- **Languages:** English (default), Dutch, French, German, Spanish and Italian, the languages every app built from this template ships. `generateLocaleConfig`
  makes the app appear under Android's per-app language settings.
- **Persistence:** DataStore when needed (version in the catalog, not added as a dependency yet). No
  network, analytics or crash-reporting libraries.
- **No instrumented/UI tests** yet: they need an emulator in CI (slow, flaky). Logic is kept in plain
  Kotlin and unit tested instead. Manual device checks are listed in `docs/testing.md`.
- **No lint baseline:** the template has zero lint issues.
- **Play Store upload** is prepared but commented out in `release.yml` until a Play Console app and
  service account exist.

## 2026-11-10: TV Favorites from the template

- **minSdk 26 (Android 8.0)** instead of 31: many Android TVs still run Android 9 to 11. Dynamic
  color stays an option on Android 12+. `backup_rules.xml` covers backup on Android 8-11.
- **One APK for phone and TV** (`leanback` and `touchscreen` not required), screens chosen at
  run time. Keeps one module, one release and one Firebase setup.
- **Sync with Firebase (Auth anonymous + Cloud Firestore)**, owner's choice: a TV shows a QR code,
  phones join with it. No server code to run; Firestore's offline copy keeps the app usable without
  network. Project `tv-favorites`, region `europe-west1`. Configured in code (`FirebaseOptions`)
  instead of the `google-services` plugin and `google-services.json`, so no API key is committed;
  the key is the `FIREBASE_API_KEY` secret. Rules in `firebase/firestore.rules`.
- **New libraries** (approved by the owner): TV Material (`androidx.tv:tv-material`), Firebase BoM
  (auth, firestore), OkHttp and kotlinx.serialization (page and API reads, local file), Coil
  (posters), ZXing core (QR code on the TV), Google code scanner (`play-services-code-scanner`: no
  camera permission needed), lifecycle-runtime-compose, DataStore.
- **Details from the shared page first** (Open Graph tags; a link-preview user agent as second
  try), then **TMDB** with the user's own token for posters and air dates, **TVmaze** (no key) for
  the exact air time. Only an exact title match fills in TMDB data, so a wrong poster is never added.
- **The TV works on its own**, owner's choice: the TMDB token is shared through the household, and
  the TV refreshes air dates and home screen rows itself (JobScheduler, about hourly).
- **Opening on the TV** tries several links and then the app's start screen; the TV app is found
  by package or by name, so renamed packages keep working. Per-service overrides for the rest.
- **Home screen rows** with the platform `TvContract` API (no `tvprovider` library). Only "All" is
  shown by itself; per-service rows need the user's approval, which Android asks per row.
- **Icons as vector drawables** (no `material-icons-extended`, to avoid a large dependency).

## 2026-11-10: Cookie and privacy walls

Shared links of some sites (first seen with VTM GO, DPG Media's privacy gate) land on a consent
wall for apps without a consent cookie. Handled for every site alike, not per service: recognise
the wall, ask as a link-preview bot first, follow the wall's own return link, and keep the page's
declared link. The app never accepts consent on the user's behalf.

## 2026-11-10: The service's own pictures first

The owner saw a wrong TMDB poster on a VRT MAX show whose page has its own pictures. Pictures now
come from the shared page first and TMDB only fills in when the page has none; TMDB matching for
regional services prefers that country's and language's title.

## 2026-11-10: Landscape pictures only, TVmaze instead of TMDB, own group order

Owner's choice. Streaming pages almost only offer landscape preview pictures, so the app uses
those everywhere (phone thumbnails, TV cards, home screen tiles) and no longer has posters. TMDB is
removed: no token to set up, no wrong posters; air dates come from TVmaze alone (search by title,
regional match, no key). Cost: shows TVmaze doesn't know, and movies, get no air date. The
service order is the user's own and shared, so the phone's groups and the TV's rows follow it.

## 2026-11-10: Home screen rows like Nuvio, VTM GO's own link scheme

The owner saw no rows on the TV's home screen, while Nuvio's "Continue watching" row does show.
The rows are now written the way Nuvio writes its row: the TV provider permissions
(`READ_EPG_DATA`, `WRITE_EPG_DATA`; owner's request, listed in PRIVACY.md), off the main thread,
row by row, found again by the id they were written with, tiles read without a selection and
updated in place. Still the platform `TvContract` API, no `tvprovider` library. VTM GO opens a show
through `vtmgo://` (what its pages declare for apps, November 2026), as its https links only
opened the app's start screen; the https link stays as the fallback.

## 2026-11-11: A Nuvio add-on, next to the app's own TV screens

Owner's request: the favorites also as rows in Nuvio. Nothing like it existed (Stremio/Nuvio
catalog add-ons list Trakt or library items and play video files; none opens official apps on a
show). Built as the add-on protocol Nuvio speaks (Stremio's), on a Cloudflare Worker like the
owner's other services, in plain JavaScript without packages (tested with `node --test`). It reads
the list as a household member (joining with the key in its address), so `firestore.rules` stay
as they are and no server key can read every household. Each favorite opens through TV
Favorites' own `tvfavorites://open` link (made `BROWSABLE`), as Nuvio opens one link only and
TV Favorites knows each service's app and fallbacks. Deployed from GitHub Actions; the
Cloudflare MCP server can't deploy Workers.

