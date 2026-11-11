# Privacy policy

_Last updated: 2026-11-11 (Nuvio add-on)_

TV Favorites keeps a list of shows and movies you saved, on your phone and the TV it is paired
with. It has no account, no analytics, no advertising and no crash reporting.

## What is stored, and where

- **Your favorites** (title, description, picture links, the link you shared, the streaming
  service, the last aired episode), **per-service settings** (which TV app opens a service, a link
  pattern) and **the order of the services**:
  - before pairing: in a file on your phone only;
  - once a phone and a TV are paired: in Google Firebase (Cloud Firestore, region `europe-west1`,
    Belgium), in a "household" that only the paired devices can read or change. Each device signs
    in to Firebase anonymously; no name, e-mail address or Google account is involved.
- **On each device only**: which household it belongs to and the ids of the TV's home screen rows.
- **Backed up by Android** (to your Google account, if you turned that on, and when moving to a new
  phone): only your appearance settings and whether the list is grouped by service.

## Permissions

- **Internet and network state**: to read shared pages, TVmaze and the shared list.
- **TV home screen rows** (`READ_EPG_DATA`, `WRITE_EPG_DATA`, Android TV only): to write the app's
  own rows and tiles on the home screen. The app reads and changes only its own rows, never the TV
  guide or other apps' rows.

Anyone who scans the TV's pairing code can see and change the list. Scan it only with your own
phones.

## Which services the app contacts

- **The page you share** (for example `vrt.be` or `netflix.com`), once, to read its title,
  description and picture, as a browser would.
- **TVmaze** (`api.tvmaze.com`, no account): the show's title, to find the show, and then its
  TVmaze id, to find when the latest episode aired. Both the phone and the TV ask.
- **Google Firebase**, for the anonymous sign-in and the shared list.
- **Picture hosts** of the streaming services, to show their pictures.
- **The Nuvio add-on** (`tv-favorites-nuvio.wim-8af.workers.dev`, Cloudflare), only if you add
  its address in Nuvio. Nuvio then asks the add-on for your list; the add-on reads it from
  Firebase as a member of your household (it joins with the key in the address) and answers with
  the titles, descriptions, pictures and links. It keeps nothing but its own anonymous sign-in.
  Anyone with the address can see the list, as with the TV's pairing code. The add-on and Nuvio
  are optional; the apps work without them.

## Removing your data

- On the phone: Settings › Disconnect from TV, then uninstall the app.
- On the TV: uninstall the app. A household nobody uses can be removed by the developer on request.

Questions: open an issue in this repository.
