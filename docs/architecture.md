# Architecture

## This app
TV Favorites saves shows and movies from any streaming service and opens them on an Android TV.

- **Phone** (management): share a link from a streaming app (Share › TV Favorites) or paste one;
  the app reads the page's title, description and picture, adds the last aired episode from
  TVmaze, and saves it. The list can be reordered (drag handles), grouped by service, edited and
  deleted. Settings: pairing with the TV (scan its QR code), group by service and the service
  order (also the TV's row order), per-service app settings, appearance.
- **TV** (works on its own): one row "All" and one per service; OK opens the favorite in the
  service's app, a long press opens Open / Move / Edit / Delete. The app keeps the Android TV home
  screen rows ("All", and per service when the user adds them) and the air dates up to date
  itself, also while closed (a job about every hour). It shows the QR code a phone scans.
- One APK for both: `TvFavoritesApp.isTv` picks the TV or the phone screens.

## Structure
```
app/src/main/kotlin/to/wim/tvfavorites/
  TvFavoritesApp.kt     Shared objects (HTTP client, settings, repository, Firebase), isTv.
  model/                Favorite, ServiceOverride, Library (kotlinx.serialization).
  logic/                Plain Kotlin, unit tested:
    SharedText            The link and a title hint in shared text; tracking parameters removed.
    PageMetadata          Open Graph / Twitter / <title> parsing; Titles cleans site names.
    Lookup                TVmaze search, regional match and last aired episode; DraftBuilder.
    Airing                When to refresh air dates; date and time in the device's time zone.
    Services              ServiceCatalog: known services, their hosts and TV apps.
    DeepLinks             Which TV app opens a service and in which order links are tried.
    Ordering              Sorting, moving (whole list or within a service), grouping.
    Pairing               Household ids and the QR code's content.
    ChannelPlan           The home screen rows and their tiles; finding rows again, updating tiles.
    LongPress             A short or a long press of the remote's OK button.
  data/
    LibraryStore          LocalLibraryStore (JSON file) and the interface.
    CloudLibraryStore     Firestore: households/<id>/{members,favorites,settings}.
    LibraryRepository     The list for the screens: local until paired, then the cloud; pairing.
    DetailsFetcher        Reads shared pages (OkHttp) and TVmaze.
    SettingsStore         DataStore: backed_up_settings and device_state.
  launch/               Launcher (intents, following DeepLinks), OpenFavoriteActivity (home screen tiles).
  tv/                   HomeChannels (TvContract rows), HomeChannelsJob, InitializeProgramsReceiver.
  ui/                   MainActivity, phone/ screens, tv/ screens, theme/ (shared design kit).
firebase/               Firestore rules and config (deployed with the Firebase CLI).
nuvio-addon/            The Nuvio add-on: a Cloudflare Worker (plain JavaScript, `node --test`).
```

## Sync
- A **household** is a TV's list. The TV creates it at its first start (`ensureHousehold`) and
  shows `tvfavorites://pair?h=<id>&k=<joinKey>` as a QR code. A phone joins by writing
  `members/<its uid>` with the key; `firebase/firestore.rules` checks the key and only lets members
  read or write. Devices sign in to Firebase anonymously.
- Joining copies the phone's favorites (and service settings, service order) into the TV's list,
  without duplicate links. Disconnecting keeps a copy on the phone.
- Firestore keeps a copy on the device, so the list works offline and catches up later. Writes
  are not awaited by the screens.
- The Firebase API key comes from the `FIREBASE_API_KEY` repository secret (CI) or
  `-PfirebaseApiKey` (local). Without it `Connection.Unavailable`: the app works on one device.

## Reading a shared link
`DetailsFetcher` reads the shared page as a link-preview bot first (sites fill in their preview
tags for those and usually let them past cookie walls), then as a browser. `ConsentGate`
recognises cookie and privacy walls on any site, by their address (`consent.`, `/privacy-gate`,
`cookiewall`, …) or by a title about privacy or cookies without a picture. The app never accepts
consent itself: it ignores the wall, tries the page the wall would return to (any link in its
query parameters), and never stores the wall's address (`ConsentGate.chooseLink` keeps the page's
own `al:android:url`/`og:url`, or the shared link). Titles lose the site's name and a season part
("Seizoen 2"); a link's path gives a last-resort title ("mijn-restaurant~<id>" → "Mijn Restaurant").

**Pictures** are the service's own, from the shared page: every `og:image` (with its size), Twitter
card and schema.org image; a landscape one is preferred, as the phone's thumbnails, the TV's cards
and the home screen tiles are all 16:9. No other picture source is used.

**Group order.** `Library.groupOrder` (shared: `settings/groups` in Firestore) is the user's order
of the services: the phone's groups and the TV's rows after "All" (and the home screen rows).
`Ordering.groups` puts services in that order first, the others by their first favorite.

## Opening a favorite on the TV
`DeepLinks.findApp`: the app picked in Settings › Streaming apps, else a known package, else an
installed TV app whose name matches the service. `DeepLinks.plan` then tries, in order: the
user's link pattern, the link rewritten for the service's app (Netflix, YouTube), the shared link,
and finally the app's start screen (with a message that the show itself couldn't be opened).

**VTM GO** opens a show from `vtmgo://www.vtmgo.be/vtmgo/<show>~<id>` (the link its pages declare
for apps); its https link only opens the app's start screen.

**OK and long press on the TV.** The cards handle OK themselves (`LongPressDetector`): a short
press opens, holding OK opens the actions once (while held, or on release for remotes that don't
repeat keys). The actions dialog ignores an OK it didn't see pressed, so letting go after a long
press doesn't press its first button ("Open").

## Home screen rows
Written like the rows of apps that do show up on the home screen (Nuvio's "Continue watching"):
the TV provider permissions in the manifest, off the main thread and one sync at a time, each
row on its own (a failing row doesn't stop the others) and its id saved as soon as it exists.
Rows are found again by the id they were written with (`<package>/<key>`), so lost ids never
lead to a second row; leftover duplicates are removed. Tiles are read without a selection (some
TVs refuse one), updated in place and only rewritten when they changed. Only "All" is offered to
the home screen by itself; Settings › Home screen rows offers every row, "All" included.

## Nuvio add-on
An addition to the app's own TV screens and home screen rows, for people who browse in Nuvio.
`nuvio-addon/` is a Cloudflare Worker that speaks the Stremio add-on protocol (Nuvio's add-ons):
`/<household id>~<join key>/manifest.json`, then `catalog`, `meta` and `stream` per favorite.

- **Reading the list:** the Worker has one anonymous Firebase identity (kept in its KV store) and
  joins a household with the join key from the address, checked by `firestore.rules` as for a
  phone. It only reads favorites and `settings/groups`; it writes nothing else.
- **Catalogs:** "TV Favorites" (all) and one per service in the user's order, landscape tiles,
  ids `tvfav:<favorite id>`, type `movie` (Nuvio then lists the "streams" at once).
- **Opening:** the first stream is `tvfavorites://open?…`, the home screen tiles' link, so TV
  Favorites on the TV opens it with its full plan (`DeepLinks.plan`). `OpenFavoriteActivity` is
  `BROWSABLE` for this, as Nuvio opens external links like a browser. The second stream is the
  shared link itself, for TVs without TV Favorites.
- **Address:** `NuvioAddon.address` (TV: Settings › Nuvio add-on, with a QR code; phone: Settings › Nuvio add-on, with Copy and Share).
  The service names are copied in `nuvio-addon/src/services.json`; `NuvioAddonTest` checks them.
- **Deploy:** `.github/workflows/nuvio-addon.yml` on changes in `nuvio-addon/` (needs the
  `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` secrets; passes `FIREBASE_API_KEY` on).

## Air dates
`DetailsFetcher` searches TVmaze by the show's title (`/search/shows`); `TvMaze.bestMatch` takes an
exact name, for regional services one from that country or language
(`StreamingService.countries`/`languages`), and gives up rather than guess. The show's previous
episode (`/shows/<id>?embed=previousepisode`) gives the exact moment (`airstamp`), stored as ISO 8601
and shown in the device's time zone. `Airing.isDue`: daily for found shows, weekly for ones not
found yet (which also fills in older favorites). Refreshed by the phone and by the TV (on start and
in the hourly job). Movies get no date: TVmaze only knows series.

## Conventions
- **Single `app` module.** Logic in plain Kotlin under `logic/`, tested on the JVM.
- **UI state**: screens collect the repository's `StateFlow`s; navigation is a small back stack
  (`PhoneApp`) or an enum (`TvApp`).
- **TV text colour**: `TvApp` sets the light text colour of the dark theme for both TV Material and
  Material 3 (`LocalContentColor`); without it TV Material's text is black.
- **TV screens** use TV Material (`androidx.tv.material3`) for focusable cards, buttons and list
  items, and Material 3 for text fields; colours come from the same theme.
- **Strings** in resources only, in all six languages.
- **Icons** are vector drawables in `res/drawable` (no icon library).

## Backup
Android backs up app data to the user's Google account (if they turned backup on) and copies it
to a new phone during set-up. The rules are an **allow list**:
`res/xml/data_extraction_rules.xml` (Android 12+) and `res/xml/backup_rules.xml` (Android 8-11),
both listing only `datastore/backed_up_settings.preferences_pb`. `BackupRulesTest` checks all three.

- **Backed up** (`backed_up_settings`): theme, dynamic color, group by service.
- **Not backed up** (`device_state` and files): the household id and join key,
  the home screen row ids, the local favorites file and Firestore's cache. Paired devices get their
  list from the cloud again.
- To test on a phone: change a setting, Settings › search "Backup" › **Back up now**, uninstall,
  install the same APK again: the setting comes back.
