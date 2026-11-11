// Reads a household's list from Firestore over REST, as a member of that household.
//
// The add-on signs in to Firebase anonymously once (like a phone) and keeps that identity in KV.
// To read a household it joins it with the household's join key from the add-on address, which
// firebase/firestore.rules checks exactly as for a phone. It only reads; it never writes favorites.

const FIRESTORE = "https://firestore.googleapis.com/v1";
const IDENTITY_KEY = "identity";

export class AccessDenied extends Error {}

export class HouseholdReader {
  /**
   * @param {{apiKey: string, projectId: string, kv: KVNamespace, fetch?: typeof fetch}} options
   */
  constructor({ apiKey, projectId, kv, fetch: fetchImpl }) {
    if (!apiKey) throw new Error("FIREBASE_API_KEY is not set");
    this.apiKey = apiKey;
    this.projectId = projectId;
    this.kv = kv;
    this.fetch = fetchImpl ?? globalThis.fetch.bind(globalThis);
    this.token = null;
  }

  /** The favorites (Firestore documents) and the service order of a household. */
  async read({ householdId, joinKey }) {
    try {
      return await this.readAsMember(householdId);
    } catch (e) {
      if (!(e instanceof AccessDenied)) throw e;
    }
    // Not a member yet (or the identity is new): join with the key, then read again.
    await this.join(householdId, joinKey);
    return this.readAsMember(householdId);
  }

  async readAsMember(householdId) {
    const base = `${this.documents()}/households/${householdId}`;
    const documents = [];
    let pageToken = "";
    do {
      const page = await this.get(`${base}/favorites?pageSize=300${pageToken ? `&pageToken=${encodeURIComponent(pageToken)}` : ""}`);
      documents.push(...(page.documents ?? []));
      pageToken = page.nextPageToken ?? "";
    } while (pageToken);
    const groups = await this.get(`${base}/settings/groups`, { allowMissing: true });
    const order = (groups?.fields?.order?.arrayValue?.values ?? []).map((v) => v.stringValue).filter(Boolean);
    return { documents, order };
  }

  async join(householdId, joinKey) {
    const { uid } = await this.identity();
    const url = `${this.documents()}/households/${householdId}/members?documentId=${encodeURIComponent(uid)}`;
    const response = await this.fetch(url, {
      method: "POST",
      headers: { authorization: `Bearer ${await this.idToken()}`, "content-type": "application/json" },
      body: JSON.stringify({ fields: { key: { stringValue: joinKey } } }),
    });
    // Already a member: fine. A wrong key or an unknown household: the rules refuse.
    if (response.status === 409) return;
    if (response.status === 403 || response.status === 404) throw new AccessDenied("Wrong household or key");
    if (!response.ok) throw new Error(`Joining failed: ${response.status}`);
  }

  async get(url, { allowMissing = false } = {}) {
    const response = await this.fetch(url, { headers: { authorization: `Bearer ${await this.idToken()}` } });
    if (response.status === 404 && allowMissing) return null;
    if (response.status === 403) throw new AccessDenied("Not a member");
    if (!response.ok) throw new Error(`Firestore answered ${response.status}`);
    return response.json();
  }

  documents() {
    return `${FIRESTORE}/projects/${this.projectId}/databases/(default)/documents`;
  }

  /** The stored anonymous identity, created on first use. */
  async identity() {
    const stored = await this.kv.get(IDENTITY_KEY, "json");
    if (stored?.refreshToken && stored?.uid) return stored;
    const response = await this.fetch(`https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${this.apiKey}`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ returnSecureToken: true }),
    });
    if (!response.ok) throw new Error(`Anonymous sign-in failed: ${response.status}`);
    const body = await response.json();
    const identity = { uid: body.localId, refreshToken: body.refreshToken };
    await this.kv.put(IDENTITY_KEY, JSON.stringify(identity));
    this.token = { value: body.idToken, expiresAt: Date.now() + (Number(body.expiresIn) - 60) * 1000 };
    return identity;
  }

  /** A Firebase ID token for the identity, refreshed shortly before it expires (after an hour). */
  async idToken() {
    if (this.token && this.token.expiresAt > Date.now()) return this.token.value;
    const identity = await this.identity();
    if (this.token && this.token.expiresAt > Date.now()) return this.token.value;
    const response = await this.fetch(`https://securetoken.googleapis.com/v1/token?key=${this.apiKey}`, {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ grant_type: "refresh_token", refresh_token: identity.refreshToken }).toString(),
    });
    if (!response.ok) {
      // The identity was removed (or its token revoked): start again with a new one.
      await this.kv.delete(IDENTITY_KEY);
      this.token = null;
      throw new Error(`Token refresh failed: ${response.status}`);
    }
    const body = await response.json();
    this.token = { value: body.id_token, expiresAt: Date.now() + (Number(body.expires_in) - 60) * 1000 };
    return this.token.value;
  }
}
