#!/usr/bin/env node
// Uploads a quiz JSON file for the phones: users/{uid}/quiz/{date} and quiz/latest.
// Usage: node tools/quiz/push.mjs quiz.json
import { readFileSync } from "node:fs";
import { setDoc, userId } from "./firestore.mjs";

const quiz = JSON.parse(readFileSync(process.argv[2], "utf8"));
if (!/^\d{4}-\d{2}-\d{2}$/.test(quiz.date)) throw new Error("quiz.date must be YYYY-MM-DD");
if (!Array.isArray(quiz.questions) || !quiz.questions.length) throw new Error("quiz.questions is empty");
for (const q of quiz.questions) {
  if (!q.id || !q.prompt || !["choice", "short", "write"].includes(q.type)) throw new Error(`Bad question: ${JSON.stringify(q)}`);
  if (q.type === "choice" && (!q.choices?.includes(q.answer))) throw new Error(`Choice question ${q.id}: answer must be one of the choices`);
  if (q.type === "short" && !q.answer) throw new Error(`Short question ${q.id} needs an answer`);
}
const uid = await userId();
const json = JSON.stringify(quiz);
await setDoc(`users/${uid}/quiz/${quiz.date}`, { json, updatedAt: Date.now() });
await setDoc(`users/${uid}/quiz/latest`, { json, updatedAt: Date.now() });
console.log(`Quiz for ${quiz.date} (${quiz.questions.length} questions) sent to the phones.`);
