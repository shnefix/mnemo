# Daily Mnemo quiz (run by a scheduled Claude task)

1. Run `node tools/quiz/fetch.mjs` from the project root. It prints JSON with:
   - `date` and `reviewedNotes` (each: `noteUid`, `deck`, `fields`, `ratings` for today),
   - `previousQuiz` and `previousAnswers` (may be null).
2. If `reviewedNotes` is empty, stop: no quiz today.
3. Write `quiz.json` in the scratchpad with this shape:
   ```json
   {
     "date": "<date from fetch>",
     "intro": "one friendly sentence about what today covered",
     "feedback": "short feedback on previousAnswers' written answers (empty if none)",
     "questions": [
       {"id": "q1", "type": "choice", "prompt": "...", "choices": ["a","b","c","d"], "answer": "b", "explain": "...", "noteUid": "<uid>"},
       {"id": "q2", "type": "short", "prompt": "...", "answer": "...", "accept": ["other correct spellings"], "explain": "...", "noteUid": "<uid>"},
       {"id": "q3", "type": "write", "prompt": "Use X in a sentence about ...", "explain": "what a good answer does", "noteUid": "<uid>"}
     ]
   }
   ```
   Rules:
   - 6–10 questions, focused on notes rated "again"/"hard" first, then the rest.
   - Test understanding, not recall of the card text: new example sentences, gap-fills in a
     fresh context, "which is correct", choosing the right word for a situation. Never copy the
     card's own example sentence.
   - About half `choice`, a few `short` (answers of 1–3 words, list every acceptable variant
     in `accept`), and 1–2 `write`.
   - `explain` is one or two sentences: why the answer is right.
   - `feedback`: for each written answer in `previousAnswers` (`correct: null`), say briefly
     whether it is right and give a corrected version if needed. Warm and concise.
4. Run `node tools/quiz/push.mjs <path to quiz.json>`.
5. Report in one line how many questions were sent.
