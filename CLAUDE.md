# Rules for Claude Code sessions in this repository

The owner is **not a developer and does not review code**. CI, tests, lint and these rules
are the safety net. Follow them every time.

## Workflow
1. **One focused change per branch and pull request.** Start a new branch from an up-to-date
   `main` (`git fetch origin main && git checkout -b <branch> origin/main`). Never reuse an old
   or merged branch for a new change.
2. **Before finishing, run and fix everything:**
   ```
   ./gradlew testDebugUnitTest lintDebug
   ```
   CI builds the debug and release APKs on every push, so don't build them here unless a step below asks for it.
   If you touched `scripts/` or the workflows, also run `python3 -m unittest discover -s scripts/tests`.
   Then push and make sure CI on the pull request is green.
3. **Write the pull request description in plain language** (fill in `.github/pull_request_template.md`):
   what changed and why, in words a non-developer understands, and **exactly what to tap on the
   phone to test it** (step by step, with what they should see). The owner tests by installing
   the debug APK from the pull request's CI run (Checks → CI → Artifacts).
4. **Testing on the phone does not block merging.** The owner doesn't review code, so green CI is
   what a pull request needs to be merged. The owner tries the change afterwards with the test steps
   from the description (the debug APK stays on the pull request's CI run, and every push to
   `main` builds a new one) and always before a release. A problem found then is fixed in a new
   pull request.

## Never do these without asking first (and saying why)
- Delete, skip, `@Ignore`, or weaken tests, lint checks, CI steps or workflows to make something
  pass. Don't add a lint baseline or disable a lint check. **Never remove the release workflow.**
- Add Android permissions, network access, analytics, crash reporting, ads or tracking.
- Add new dependencies (libraries or Gradle plugins). Prefer what is already in
  `gradle/libs.versions.toml`. Use **DataStore** if the app needs to store data.
- Change the `applicationId` or the signing setup of an app that has already been released
  (phones will refuse to update it).
- Change `LICENSE` or add licence headers (the owner decides how the code may be used).

## Never commit
Secrets, keystores (`*.jks`, `*.keystore`), `keystore.properties`, passwords or API keys.

## Code rules
- Kotlin + Jetpack Compose + Material 3, single `app` module, mainstream libraries only.
- **Every logic change gets new or updated unit tests** (`app/src/test/`). Keep logic in plain
  Kotlin classes without Android types (no `Context`, `View`, Compose) so it runs on the JVM.
  Composables only display state and forward clicks.
- **All user-visible text goes in `app/src/main/res/values/strings.xml`**, never hard-coded.
  Add the translation to **every** `values-xx/strings.xml` (currently: `nl`, `fr`, `de`, `es`, `it`),
  using the shared wording in `DESIGN.md`.
- **Stored data and backup:** settings the user would want on a new phone go in the DataStore
  `backed_up_settings` (Android backs it up); sign-in tokens, keys and device-only state go in
  another file (never backed up). See `docs/architecture.md`, "Backup".
- Dependency versions live only in `gradle/libs.versions.toml`.
- Versions are automatic (git tags / release workflow). Don't edit `versionName`/`versionCode`.

## Keep docs in sync
- Behaviour change → update `docs/` and, if it touches data or permissions, `PRIVACY.md`.
- Notable decision (new library, architecture choice, minSdk change, …) → add a dated entry to
  `docs/decisions.md`.
- Structure and conventions: `docs/architecture.md`. What is tested and how: `docs/testing.md`.
- Look and feel: `DESIGN.md` is the **master copy** of the design language shared by the apps
  built from this template (Tracker for Simkl, Time Tracker for Notion, Snoozer, RSS Reader,
  Noter for Notion). After changing it, copy every section except "This app" unchanged into
  `DESIGN.md` of every app.

## Releasing (only when the owner asks)
Actions → Release → Run workflow on `main` → choose `patch`, `minor` or `major`.
See README "Making a release".
