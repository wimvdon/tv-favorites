import { test } from "node:test";
import assert from "node:assert/strict";
import { route } from "../src/worker.js";
import { AccessDenied, HouseholdReader } from "../src/firebase.js";

test("routes", () => {
  assert.deepEqual(route(["manifest.json"]), { resource: "manifest" });
  assert.deepEqual(route(["catalog", "movie", "all.json"]), { resource: "catalog", id: "all", laterPage: false });
  assert.deepEqual(route(["catalog", "movie", "all", "skip=100.json"]), { resource: "catalog", id: "all", laterPage: true });
  assert.deepEqual(route(["stream", "movie", "tvfav%3Aabc.json"]), { resource: "stream", id: "tvfav:abc", laterPage: false });
  // Installs from before the "favorites" type still ask for "movie".
  assert.deepEqual(route(["catalog", "favorites", "all.json"]), { resource: "catalog", id: "all", laterPage: false });
  assert.equal(route(["catalog", "movie", "all"]), null);
  assert.equal(route(["other", "movie", "x.json"]), null);
  assert.equal(route([]), null);
});

/** A fake Firebase: one household "H" with key "K"; members can list its favorites. */
function fakeFirebase() {
  const members = new Set();
  const calls = [];
  const kvStore = new Map();
  const kv = {
    get: async (k, type) => (kvStore.has(k) ? (type === "json" ? JSON.parse(kvStore.get(k)) : kvStore.get(k)) : null),
    put: async (k, v) => kvStore.set(k, v),
    delete: async (k) => kvStore.delete(k),
  };
  const reply = (status, body) => new Response(body === undefined ? null : JSON.stringify(body), { status });
  const fetch = async (url, init = {}) => {
    calls.push(`${init.method ?? "GET"} ${url.replace(/\?key=.*/, "")}`);
    if (url.includes("accounts:signUp")) return reply(200, { localId: "worker", idToken: "id1", refreshToken: "r1", expiresIn: "3600" });
    if (url.includes("securetoken")) return reply(200, { id_token: "id2", refresh_token: "r1", expires_in: "3600" });
    const authorized = init.headers?.authorization?.startsWith("Bearer id");
    if (!authorized) return reply(401, {});
    if (init.method === "POST" && url.includes("/members?documentId=worker")) {
      const key = JSON.parse(init.body).fields.key.stringValue;
      if (!url.includes("/households/H/") || key !== "K") return reply(403, {});
      if (members.has("worker")) return reply(409, {});
      members.add("worker");
      return reply(200, {});
    }
    if (!members.has("worker") || !url.includes("/households/H/")) return reply(403, {});
    if (url.includes("/favorites")) {
      return url.includes("pageToken")
        ? reply(200, { documents: [{ name: "x/f2", fields: {} }] })
        : reply(200, { documents: [{ name: "x/f1", fields: {} }], nextPageToken: "p2" });
    }
    if (url.endsWith("/settings/groups")) return reply(200, { fields: { order: { arrayValue: { values: [{ stringValue: "vtmgo" }] } } } });
    return reply(404, {});
  };
  return { fetch, kv, calls, kvStore };
}

test("the reader signs in once, joins with the key, and reads every page", async () => {
  const fb = fakeFirebase();
  const reader = new HouseholdReader({ apiKey: "k", projectId: "tv-favorites", kv: fb.kv, fetch: fb.fetch });
  const result = await reader.read({ householdId: "H", joinKey: "K" });
  assert.deepEqual(result.documents.map((d) => d.name), ["x/f1", "x/f2"]);
  assert.deepEqual(result.order, ["vtmgo"]);
  assert.equal(fb.calls.filter((c) => c.includes("signUp")).length, 1);
  assert.equal(fb.calls.filter((c) => c.startsWith("POST") && c.includes("/members")).length, 1);
  assert.deepEqual(JSON.parse(fb.kvStore.get("identity")), { uid: "worker", refreshToken: "r1" });

  // Already a member: no second join.
  await reader.read({ householdId: "H", joinKey: "K" });
  assert.equal(fb.calls.filter((c) => c.startsWith("POST") && c.includes("/members")).length, 1);
});

test("a wrong key gives no access", async () => {
  const fb = fakeFirebase();
  const reader = new HouseholdReader({ apiKey: "k", projectId: "tv-favorites", kv: fb.kv, fetch: fb.fetch });
  await assert.rejects(reader.read({ householdId: "H", joinKey: "wrong" }), AccessDenied);
  await assert.rejects(reader.read({ householdId: "Other", joinKey: "K" }), AccessDenied);
});

test("a stored identity is reused after a restart", async () => {
  const fb = fakeFirebase();
  await fb.kv.put("identity", JSON.stringify({ uid: "worker", refreshToken: "r1" }));
  const reader = new HouseholdReader({ apiKey: "k", projectId: "tv-favorites", kv: fb.kv, fetch: fb.fetch });
  await reader.read({ householdId: "H", joinKey: "K" });
  assert.equal(fb.calls.filter((c) => c.includes("signUp")).length, 0);
  assert.equal(fb.calls.filter((c) => c.includes("securetoken")).length, 1);
});

test("without an API key the reader refuses to start", () => {
  assert.throws(() => new HouseholdReader({ apiKey: "", projectId: "p", kv: {} }));
});
