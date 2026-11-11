# TV Favorites for Nuvio

A small add-on that shows your TV Favorites as rows in [Nuvio](https://nuvio.tv) (it also works
in Stremio) and opens them in the streaming service's own app. It's an addition: TV Favorites'
own TV screens and home screen rows stay as they are.

## Using it
1. Pair the TV and the phone (the add-on reads the shared list).
2. Get your address: on the TV, **TV Favorites › Settings › Nuvio add-on** (scan the code with
   your phone to copy it), or on the phone, **Settings › Nuvio add-on** (share or copy).
3. In Nuvio, add it under add-ons. Rows "TV Favorites" and "TV Favorites · <service>" appear.
4. Choose a show, then **TV Favorites**: TV Favorites on the TV opens it in the service's app.
   **Link** opens the shared link directly (for a TV without TV Favorites).

Anyone with the address can see the list, like with the TV's pairing code.

## How it works
A Cloudflare Worker (`src/worker.js`) answering the Stremio add-on protocol:

| Path | Answer |
|---|---|
| `/<household>~<key>/manifest.json` | The add-on, with a catalog "All" and one per service |
| `/<household>~<key>/catalog/movie/<all or s-service>.json` | The favorites, in your order |
| `/<household>~<key>/meta/movie/tvfav:<id>.json` | One favorite |
| `/<household>~<key>/stream/movie/tvfav:<id>.json` | How to open it (`tvfavorites://open…`, the link) |

It signs in to Firebase anonymously once (kept in KV) and joins the household with the key, as a
phone does (`firebase/firestore.rules`). It only reads. `src/addon.js` builds the answers,
`src/firebase.js` reads Firestore over REST, `src/services.json` has the service names (checked
against the app by `NuvioAddonTest`).

## Developing
```sh
node --test
```
```sh
npx wrangler@4.160.0 dev
```
`wrangler dev` needs `FIREBASE_API_KEY` in `.dev.vars` (never commit it).

## Deploying
Automatic: `.github/workflows/nuvio-addon.yml` tests and deploys on every change in this folder
on `main`. It needs two repository secrets (Settings › Secrets and variables › Actions):
- `CLOUDFLARE_API_TOKEN`: a Cloudflare API token from the template **Edit Cloudflare Workers**.
- `CLOUDFLARE_ACCOUNT_ID`: the account id (Cloudflare dashboard › Workers & Pages, right side).

`FIREBASE_API_KEY` (already there for the app) is passed on to the Worker as a secret. The KV
namespace for the add-on's identity is created by the first deploy (Wrangler's automatic
provisioning, so `wrangler.toml` has no id) and reused after that. Nothing to create by hand.
