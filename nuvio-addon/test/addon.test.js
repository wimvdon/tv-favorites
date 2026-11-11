import { test } from "node:test";
import assert from "node:assert/strict";
import { airedLine, catalog, favoriteFromDocument, manifest, meta, nameOf, openLink, parseConfig, streams } from "../src/addon.js";

const doc = (id, fields) => ({
  name: `projects/tv-favorites/databases/(default)/documents/households/h/favorites/${id}`,
  fields: Object.fromEntries(Object.entries(fields).map(([k, v]) => [k, typeof v === "number" ? { integerValue: String(v) } : { stringValue: v }])),
});

const items = [
  doc("1", { title: "Dark", url: "https://www.netflix.com/title/1", serviceId: "netflix", position: 1, description: "Time travel" }),
  doc("2", { title: "Een Echte Job", url: "https://www.vtmgo.be/vtmgo/een-echte-job~3438", serviceId: "vtmgo", position: 0, imageUrl: "https://i/2.jpg", lastAired: "2026-11-03T19:30:00Z", lastEpisode: "S6E4" }),
  doc("3", { title: "No link" }),
].map(favoriteFromDocument).filter(Boolean);

test("the address carries the household and its key", () => {
  assert.deepEqual(parseConfig("ABCDEFGHIJKLMNOPQRSTUVWX~abcdefghijklmnopqrstuvwxyz012345"), {
    householdId: "ABCDEFGHIJKLMNOPQRSTUVWX",
    joinKey: "abcdefghijklmnopqrstuvwxyz012345",
  });
  assert.equal(parseConfig("short~key"), null);
  assert.equal(parseConfig("ABCDEFGHIJKLMNOPQRSTUVWX~abc/../def0123456789012345"), null);
  assert.equal(parseConfig(undefined), null);
});

test("favorites are read as the app writes them, without broken ones", () => {
  assert.equal(items.length, 2);
  assert.deepEqual(items[1], {
    id: "2", title: "Een Echte Job", url: "https://www.vtmgo.be/vtmgo/een-echte-job~3438", serviceId: "vtmgo",
    description: "", imageUrl: "https://i/2.jpg", position: 0, addedAt: 0, lastAired: "2026-11-03T19:30:00Z", lastEpisode: "S6E4",
  });
});

test("the manifest has All and one catalog per service, in the user's order", () => {
  assert.deepEqual(manifest(items).catalogs.map((c) => c.id), ["all", "s-vtmgo", "s-netflix"]);
  assert.deepEqual(manifest(items, ["netflix"]).catalogs.map((c) => c.id), ["all", "s-netflix", "s-vtmgo"]);
  assert.equal(manifest(items).catalogs[1].name, "TV Favorites · VTM GO");
  assert.deepEqual(manifest(items).idPrefixes, ["tvfav:"]);
});

test("catalogs list landscape tiles in the user's order", () => {
  const all = catalog("all", items);
  assert.deepEqual(all.metas.map((m) => m.id), ["tvfav:2", "tvfav:1"]);
  assert.equal(all.metas[0].posterShape, "landscape");
  assert.equal(all.metas[0].poster, "https://i/2.jpg");
  assert.equal(all.metas[0].releaseInfo, "2026-11-03 · S6E4");
  assert.deepEqual(catalog("s-netflix", items).metas.map((m) => m.name), ["Dark"]);
  assert.deepEqual(catalog("s-gone", items).metas, []);
  assert.equal(catalog("other", items), null);
});

test("meta and streams for one favorite", () => {
  assert.equal(meta("tvfav:1", items).meta.name, "Dark");
  assert.equal(meta("tvfav%3A1", items).meta.name, "Dark");
  assert.equal(meta("tvfav:9", items), null);
  assert.equal(meta("tt123", items), null);
  const s = streams("tvfav:2", items).streams;
  assert.equal(s[0].externalUrl, openLink(items[1]));
  assert.equal(s.length, 1);
  assert.equal(s[0].title, "VTM GO");
  assert.deepEqual(streams("tvfav:9", items).streams, []);
});

test("the open link is the one TV Favorites' home screen tiles use", () => {
  const link = new URL(openLink(items[1]));
  assert.equal(link.protocol, "tvfavorites:");
  assert.equal(link.host, "open");
  assert.equal(link.searchParams.get("url"), "https://www.vtmgo.be/vtmgo/een-echte-job~3438");
  assert.equal(link.searchParams.get("service"), "vtmgo");
  assert.equal(link.searchParams.get("id"), "2");
});

test("names and aired lines", () => {
  assert.equal(nameOf("vrtmax"), "VRT MAX");
  assert.equal(nameOf("example.org"), "example.org");
  assert.equal(airedLine({ lastAired: "", lastEpisode: "" }), "");
  assert.equal(airedLine({ lastAired: "2026-01-02", lastEpisode: "" }), "2026-01-02");
});
