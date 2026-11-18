#!/usr/bin/env node
// Prints what the quiz task needs as JSON: the notes reviewed on a day (default today),
// plus the previous quiz and its answers so written answers can get feedback.
// Usage: node tools/quiz/fetch.mjs [YYYY-MM-DD]
import { gunzipSync } from "node:zlib";
import { getDoc, localDate, userId } from "./firestore.mjs";

const day = process.argv[2] || localDate(Date.now());
const uid = await userId();
const meta = await getDoc(`users/${uid}/sync/meta`);
if (!meta) throw new Error("Nothing synced yet");
let b64 = "";
for (let i = 0; i < meta.chunks; i++) b64 += (await getDoc(`users/${uid}/sync/chunk_${i}`)).data;
const backup = JSON.parse(gunzipSync(Buffer.from(b64, "base64")).toString("utf8"));

const decks = new Map(backup.decks.map((d) => [d.id, d]));
const deckPath = (id) => {
  const parts = [];
  for (let d = decks.get(id); d; d = decks.get(d.parentId)) parts.unshift(d.name);
  return parts.join("::");
};
const cards = new Map(backup.cards.map((c) => [c.id, c]));
const notes = new Map(backup.notes.map((n) => [n.id, n]));
const RATING = { 1: "again", 2: "hard", 3: "good", 4: "easy" };

const byNote = new Map();
for (const log of backup.reviewLogs) {
  if (localDate(log.reviewedAt) !== day) continue;
  const card = cards.get(log.cardId);
  const note = card && notes.get(card.noteId);
  if (!note) continue;
  const entry = byNote.get(note.id) || { noteUid: note.uid, deck: deckPath(note.deckId), fields: note.fields, tags: note.tags, ratings: [] };
  entry.ratings.push(RATING[log.rating] || String(log.rating));
  byNote.set(note.id, entry);
}

const previous = await getDoc(`users/${uid}/quiz/latest`);
const prevQuiz = previous?.json ? JSON.parse(previous.json) : null;
const prevAnswers = prevQuiz ? await getDoc(`users/${uid}/answers/${prevQuiz.date}`) : null;

console.log(
  JSON.stringify(
    {
      date: day,
      reviewedNotes: [...byNote.values()],
      previousQuiz: prevQuiz,
      previousAnswers: prevAnswers?.json ? JSON.parse(prevAnswers.json) : null,
    },
    null,
    2,
  ),
);
