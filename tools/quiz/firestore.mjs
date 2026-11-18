// Firestore access for the daily-quiz task, using the computer's `firebase login`
// (the project owner's account; the phone app's security rules don't apply to it).
import { readFileSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";

export const PROJECT = process.env.MNEMO_FIREBASE_PROJECT || "mnemo-6d826";
const BASE = `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/(default)/documents`;
// The Firebase CLI's public OAuth client (firebase-tools lib/api.js), used to refresh its login.
const CLIENT_ID = process.env.FIREBASE_CLIENT_ID || "563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com";
const CLIENT_SECRET = process.env.FIREBASE_CLIENT_SECRET || "j9iVZfS8kkCEFUPaAeJV0sAi";

let token;
async function accessToken() {
  if (token) return token;
  const store = JSON.parse(readFileSync(join(homedir(), ".config/configstore/firebase-tools.json"), "utf8"));
  const refresh = store?.tokens?.refresh_token;
  if (!refresh) throw new Error("Not logged in: run `npx firebase-tools login`");
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "refresh_token", refresh_token: refresh, client_id: CLIENT_ID, client_secret: CLIENT_SECRET }),
  });
  if (!res.ok) throw new Error(`Token refresh failed: ${res.status} ${await res.text()}`);
  token = (await res.json()).access_token;
  return token;
}

async function call(method, path, body) {
  const res = await fetch(`${BASE}/${path}`, {
    method,
    headers: { authorization: `Bearer ${await accessToken()}`, "content-type": "application/json" },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`${method} ${path}: ${res.status} ${await res.text()}`);
  return res.json();
}

const plain = (v) =>
  v.stringValue ?? (v.integerValue !== undefined ? Number(v.integerValue) : v.doubleValue ?? v.booleanValue ?? v.timestampValue ?? null);

export async function getDoc(path) {
  const d = await call("GET", path);
  if (!d) return null;
  return Object.fromEntries(Object.entries(d.fields || {}).map(([k, v]) => [k, plain(v)]));
}

export async function listDocs(path) {
  const d = await call("GET", path);
  return (d?.documents || []).map((doc) => ({
    id: doc.name.split("/").pop(),
    ...Object.fromEntries(Object.entries(doc.fields || {}).map(([k, v]) => [k, plain(v)])),
  }));
}

export async function setDoc(path, fields) {
  const enc = (v) => (typeof v === "number" ? { integerValue: String(Math.round(v)) } : { stringValue: String(v) });
  return call("PATCH", path, { fields: Object.fromEntries(Object.entries(fields).map(([k, v]) => [k, enc(v)])) });
}

/** The account the phones sync to (the most recently active one). */
export async function userId() {
  if (process.env.MNEMO_UID) return process.env.MNEMO_UID;
  const users = await listDocs("users");
  if (!users.length) throw new Error("No synced account yet: sign in on a phone (Settings → Account & sync)");
  users.sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0));
  return users[0].id;
}

export function localDate(ms) {
  const d = new Date(ms);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
