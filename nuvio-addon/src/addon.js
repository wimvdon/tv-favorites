// The add-on's answers (Stremio add-on protocol, which Nuvio speaks), built from the
// household's list. Plain functions without network access, so they are tested on their own.

import serviceNames from "./services.json" with { type: "json" };

export const ID_PREFIX = "tvfav:";
export const ALL = "all";
const SERVICE_PREFIX = "s-";
/**
 * The content type of every catalog and favorite. Nuvio adds a catalog's type to its row name
 * ("All - Favorites"), so the old "movie" made every row say "Movie". Nuvio and Stremio pass an
 * unknown type through as is, and the routes accept any type, so older installs keep working.
 */
export const TYPE = "favorites";

/** The household in the add-on address: "<household id>~<join key>", as in the TV's QR code. */
export function parseConfig(segment) {
  const match = /^([A-Za-z0-9]{20,64})~([A-Za-z0-9]{20,64})$/.exec(segment ?? "");
  return match ? { householdId: match[1], joinKey: match[2] } : null;
}

/** The service's name, or the link's host for services the app doesn't know (as in the app). */
export function nameOf(serviceId) {
  return serviceNames[serviceId] ?? serviceId;
}

/** A Firestore REST value as plain JavaScript. */
export function fromValue(value) {
  if (value == null) return null;
  if ("stringValue" in value) return value.stringValue;
  if ("integerValue" in value) return Number(value.integerValue);
  if ("doubleValue" in value) return value.doubleValue;
  if ("booleanValue" in value) return value.booleanValue;
  if ("nullValue" in value) return null;
  if ("arrayValue" in value) return (value.arrayValue.values ?? []).map(fromValue);
  if ("mapValue" in value) return fromFields(value.mapValue.fields ?? {});
  return null;
}

function fromFields(fields) {
  return Object.fromEntries(Object.entries(fields).map(([k, v]) => [k, fromValue(v)]));
}

/** A favorite from its Firestore document (as CloudLibraryStore writes it), or null without title or link. */
export function favoriteFromDocument(doc) {
  const f = fromFields(doc?.fields ?? {});
  if (typeof f.title !== "string" || typeof f.url !== "string") return null;
  return {
    id: String(doc.name ?? "").split("/").pop(),
    title: f.title,
    url: f.url,
    serviceId: f.serviceId || "other",
    description: f.description ?? "",
    imageUrl: f.imageUrl || f.posterUrl || "",
    position: Number(f.position ?? 0),
    addedAt: Number(f.addedAt ?? 0),
    lastAired: f.lastAired ?? "",
    lastEpisode: f.lastEpisode ?? "",
  };
}

/** The user's order: by position, then oldest first, then id (Ordering.sorted in the app). */
export function sorted(items) {
  return [...items].sort((a, b) => a.position - b.position || a.addedAt - b.addedAt || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
}

/** One group per service: the user's order first, then by their first favorite (Ordering.groups). */
export function groups(items, order = []) {
  const natural = [];
  const byService = new Map();
  for (const item of sorted(items)) {
    if (!byService.has(item.serviceId)) {
      const group = { serviceId: item.serviceId, name: nameOf(item.serviceId), items: [] };
      byService.set(item.serviceId, group);
      natural.push(group);
    }
    byService.get(item.serviceId).items.push(item);
  }
  const rank = new Map(order.map((id, i) => [id, i]));
  return natural
    .map((group, index) => ({ group, index }))
    .sort((a, b) => (rank.get(a.group.serviceId) ?? Infinity) - (rank.get(b.group.serviceId) ?? Infinity) || a.index - b.index)
    .map(({ group }) => group);
}

/**
 * The add-on's description of itself: a catalog "All" and one per service, in the user's order.
 * Nuvio shows them as "All - Favorites" and "Netflix - Favorites".
 */
export function manifest(items, order = []) {
  return {
    id: "to.wim.tvfavorites",
    version: "1.1.0",
    name: "TV Favorites",
    description: "Your TV Favorites, opened in the streaming service's own app.",
    resources: ["catalog", "meta", "stream"],
    types: [TYPE],
    idPrefixes: [ID_PREFIX],
    catalogs: [
      { type: TYPE, id: ALL, name: "All" },
      ...groups(items, order).map((g) => ({ type: TYPE, id: SERVICE_PREFIX + g.serviceId, name: g.name })),
    ],
    behaviorHints: { configurable: false, configurationRequired: false },
  };
}

/** The favorites of catalog [catalogId] ("all" or "s-<service id>"), or null for an unknown catalog. */
export function catalog(catalogId, items, order = []) {
  if (catalogId === ALL) return { metas: sorted(items).map(preview) };
  if (!catalogId.startsWith(SERVICE_PREFIX)) return null;
  const serviceId = catalogId.slice(SERVICE_PREFIX.length);
  const group = groups(items, order).find((g) => g.serviceId === serviceId);
  return { metas: (group?.items ?? []).map(preview) };
}

/** "3 Nov 2026 · S2E5" style line, in English (the add-on doesn't know the TV's language). */
export function airedLine(item) {
  const date = /^\d{4}-\d{2}-\d{2}/.test(item.lastAired) ? item.lastAired.slice(0, 10) : "";
  return [date, item.lastEpisode].filter(Boolean).join(" · ");
}

function preview(item) {
  return {
    id: ID_PREFIX + item.id,
    type: TYPE,
    name: item.title,
    // The services' own pictures are landscape (16:9).
    poster: item.imageUrl || undefined,
    posterShape: "landscape",
    background: item.imageUrl || undefined,
    description: item.description || undefined,
    releaseInfo: airedLine(item) || undefined,
  };
}

/** The details of one favorite, or null when it isn't in the list. */
export function meta(id, items) {
  const item = find(id, items);
  if (!item) return null;
  return { meta: { ...preview(item), genres: [nameOf(item.serviceId)], website: item.url } };
}

/**
 * How to watch one favorite: through TV Favorites on the TV, which knows each service's app and
 * link (and falls back to the app's start screen). One stream only: the shared link on its own
 * was only for a TV without TV Favorites, and the owner didn't want it in the list.
 */
export function streams(id, items) {
  const item = find(id, items);
  if (!item) return { streams: [] };
  const service = nameOf(item.serviceId);
  return {
    streams: [
      { name: "TV Favorites", title: service, description: service, externalUrl: openLink(item) },
    ],
  };
}

/** The link the TV Favorites app opens a favorite from (ChannelPlan.openUri in the app). */
export function openLink(item) {
  const q = new URLSearchParams({ id: item.id, service: item.serviceId, url: item.url });
  return `tvfavorites://open?${q.toString()}`;
}

function find(id, items) {
  const raw = decodeURIComponent(id ?? "");
  if (!raw.startsWith(ID_PREFIX)) return null;
  const key = raw.slice(ID_PREFIX.length);
  return items.find((i) => i.id === key) ?? null;
}
