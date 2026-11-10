## What changed
<!-- Plain language, for someone who doesn't read code. What does the app do differently now, and why? -->

## How to test on the phone
<!-- Install the debug APK: Checks → CI → Artifacts → <app>-<version>-debug.apk.
     Then give exact steps: what to tap, and what you should see. -->
1. Open the app.
2. …
3. You should see …

## Risks
<!-- What could go wrong or behave differently? Anything not covered by automatic tests? "None" is fine if true. -->

## Checklist
- [ ] `./gradlew testDebugUnitTest lintDebug` passes locally, and CI is green
- [ ] Unit tests added or updated for every logic change
- [ ] No tests, lint checks, CI steps or workflows removed or weakened
- [ ] All new text is in `strings.xml`, with translations for every language
- [ ] `docs/`, `PRIVACY.md` and `docs/decisions.md` updated where needed
- [ ] No new permissions, network access, analytics or dependencies (or: asked first, explained below)
- [ ] No secrets, keystores or `keystore.properties` committed
