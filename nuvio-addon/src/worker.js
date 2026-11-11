// The TV Favorites add-on for Nuvio (and Stremio), as a Cloudflare Worker.
//
//   /<household id>~<join key>/manifest.json
//   /<household id>~<join key>/catalog/movie/<catalog id>[/<extra>].json
//   /<household id>~<join key>/meta/movie/tvfav:<favorite id>.json
//   /<household id>~<join key>/stream/movie/tvfav:<favorite id>.json

import { catalog, favoriteFromDocument, manifest, meta, parseConfig, streams } from "./addon.js";
import { AccessDenied, HouseholdReader } from "./firebase.js";

const CORS = {
  "access-control-allow-origin": "*",
  "access-control-allow-headers": "*",
  "access-control-allow-methods": "GET, OPTIONS",
};

let reader = null;

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return new Response(null, { headers: CORS });
    if (request.method !== "GET") return json({ error: "Method not allowed" }, 405);
    const url = new URL(request.url);
    const parts = url.pathname.split("/").filter(Boolean);
    if (parts.length === 0) return home();

    const config = parseConfig(parts[0]);
    if (!config) return json({ error: "Unknown add-on address" }, 404);
    const target = route(parts.slice(1));
    if (!target) return json({ error: "Not found" }, 404);

    reader ??= new HouseholdReader({ apiKey: env.FIREBASE_API_KEY, projectId: env.FIREBASE_PROJECT_ID, kv: env.IDENTITY });
    let list;
    try {
      const { documents, order } = await reader.read(config);
      list = { items: documents.map(favoriteFromDocument).filter(Boolean), order };
    } catch (e) {
      if (e instanceof AccessDenied) return json({ error: "This add-on address no longer gives access to a list" }, 403);
      console.error(e);
      return json({ error: "The list could not be read" }, 502);
    }

    const body =
      target.resource === "manifest" ? manifest(list.items, list.order)
      : target.resource === "catalog" ? (target.laterPage ? { metas: [] } : catalog(target.id, list.items, list.order))
      : target.resource === "meta" ? meta(target.id, list.items)
      : streams(target.id, list.items);
    return body ? json(body, 200, 60) : json({ error: "Not found" }, 404);
  },
};

/** manifest.json, or <resource>/<type>/<id>[/<extra>].json. */
export function route(parts) {
  if (parts.length === 1 && parts[0] === "manifest.json") return { resource: "manifest" };
  const [resource, type, id] = parts;
  if (!["catalog", "meta", "stream"].includes(resource) || !type || !id || parts.length > 4) return null;
  if (!parts[parts.length - 1].endsWith(".json")) return null;
  // Extra arguments ("skip=100.json") come after the id. The whole list fits on one page, so a
  // request for a later page gets nothing (else the list would repeat).
  const extra = parts.length === 4 ? new URLSearchParams(decodeURIComponent(parts[3].slice(0, -".json".length))) : new URLSearchParams();
  return {
    resource,
    id: decodeURIComponent(parts.length === 4 ? id : id.slice(0, -".json".length)),
    laterPage: Number(extra.get("skip") ?? 0) > 0,
  };
}

function json(body, status = 200, maxAge = 0) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...CORS,
      "content-type": "application/json; charset=utf-8",
      "cache-control": maxAge > 0 ? `max-age=${maxAge}` : "no-store",
    },
  });
}

function home() {
  const html = `<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<title>TV Favorites for Nuvio</title>
<body style="font-family:system-ui;max-width:40em;margin:3em auto;padding:0 1em;line-height:1.5">
<h1>TV Favorites for Nuvio</h1>
<p>This add-on shows your TV Favorites as rows in Nuvio and opens them in the streaming service's own app.</p>
<p>Your own add-on address is in the TV Favorites app: <b>Settings › Nuvio add-on</b>. Add it in Nuvio under add-ons.</p>
</body></html>`;
  return new Response(html, { headers: { ...CORS, "content-type": "text/html; charset=utf-8" } });
}
