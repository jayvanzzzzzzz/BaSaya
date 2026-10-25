# BaSaya

BaSaya is a Filipino learning system for elementary students. Students learn through lessons with a lecture (Lektura), a crossword game (Laro), a practice activity (Gawain), pronunciation practice (Bigkas) and a quiz (Pagsusulit). Teachers follow each student's progress skill by skill.

This repo is the **student Android app** and the **basaya-stt** Cloudflare Worker. The websites (admin, teacher and student portals) and the notify-api server are in a separate project, `BaSaya-web`. Its `whole-function-flow.md` explains the whole system from start to finish.

## The system

| Part | Who uses it | What it does |
|---|---|---|
| **Android app** (this repo) | Students | Lessons, offline downloads, push notifications, class chat, dictionary |
| **Student website** (`BaSaya-web/student/`) | Students without Android (iPhone, laptop) | The same lessons and progress as the app, online only |
| **Teacher website** (`BaSaya-web/teacher/`) | Teachers | Classes, lesson schedules and deadlines, reports, chat, dictionary words |
| **Admin website** (`BaSaya-web/admin/`) | Admin | Lessons and their content, teacher and student accounts, classes |
| **Firebase** | All | Logins (Auth), the database (Firestore + security rules), push (FCM) |
| **notify-api** (Vercel) | Websites | Push notifications, remove/delete jobs, password and contact emails |
| **basaya-tts** Worker (Cloudflare) | Admin | Makes lecture audio with ElevenLabs and saves it to R2 |
| **basaya-stt** Worker (Cloudflare, `cloudflare/basaya-stt`) | App + student site | Checks the student's pronunciation with Whisper (Workers AI) |

```
   Android app        Student site       Teacher site        Admin site
        │                  │                  │                  │
        └──────────────────┴────────┬─────────┴──────────────────┘
                                    ▼
                 Firebase: Auth · Firestore (+ rules) · FCM
                                    │
        notify-api (Vercel) · basaya-tts + basaya-stt (Cloudflare) · R2 audio
```

The app and the websites talk to Firebase directly, and the Firestore rules decide what each user may read or write. The small servers exist only for jobs that need a secret or admin power. The app and the student site read and write the **same Firestore fields**, so a student can switch between them and the teacher's reports stay the same.

## Logging in

Students sign in with email and password (Firebase Authentication) and then join their teacher's class with a class code. The app only accepts accounts with `role: "student"`. Teachers and admins use the websites.

## A lesson

```
Mga Aralin (Home) ──► open a lesson ──► lesson path
                                          ├── 1. Lektura    (read, listen, read-along)
                                          ├── 2. Laro       (crossword)       → Talasalitaan
                                          ├── 3. Gawain     (3 lives a page)  → Pagkilala sa salita
                                          ├── 4. Bigkas     (speak each word) → Pagbigkas
                                          └── 5. Pagsusulit (quiz)            → Comprehension
```

- **Lektura** can be reopened any time. It has auto-play (it reads on and turns the pages) and a read-along highlight that follows the spoken word.
- **Laro, Gawain, Bigkas and Pagsusulit** are scored. Once finished, a part shows its score and can't be redone. A part left halfway resumes where the student stopped.
- **Schedules:** a teacher can set when a lesson opens and when it is due. The Firestore rules check the server's time, so changing the phone's date doesn't help.
- **Practice lessons:** a teacher can add a lesson as *Practice*. It saves nothing, can be redone as often as the student likes, and is left out of reports.
- **Skipped parts:** the admin can skip the game or the Gawain for a lesson. That stop is removed from the path (see *Skipped games* below).
- **Home filters:** *Tapos na*, *Nasa progreso*, *Hindi pa nasisimulan*.

## Offline learning

A downloaded lesson is saved in the app's local Room database (lecture, crossword levels, Gawain, Bigkas words, quiz and lecture audio), so it opens without internet.

## Package name

The code lives in the package `ph.basaya.app` (`namespace` in `app/build.gradle.kts`). The **application id** is still `com.example.basaya`, because Firebase (`google-services.json`) and phones that already have the app know it by that id. Changing it means registering the new id in the Firebase console and replacing `google-services.json`.

## Diksiyonaryo (Tagalog dictionary)

A Tagalog dictionary that explains words **in Filipino**, the same as the website's `#/diksiyonaryo` (see the website's README for how the word list is built). Students open it from the **book button** in the top bar, next to the bell.

```
app/src/main/assets/dictionary/tl-seed.json      ──┐   ~1,600 Tagalog Wiktionary words,
  (bundled in the app, works offline)               │   read once with Gson
                                                    ├──► Dictionary.search() ──► DictionaryActivity
Firestore dictionary/{key}                         ──┘   teacher words (added on the website's
  (online only; skipped when offline)                    teacher portal) replace or hide them
```

- **Wiktionary words**: `assets/dictionary/tl-seed.json` is the **same file** as the website's `data/dictionary/tl-seed.json` (CC BY-SA 4.0, see `assets/dictionary/LICENSE.md`). It's bundled, so these words work offline and cost no Firestore reads. When the website rebuilds the file (`node tools/build-dictionary.mjs`), copy it here too and release a new app version.
- **Teacher words**: Firestore `dictionary/{key}`, where `key` is the word in lowercase without accents (bahay). A teacher's word replaces the Wiktionary word with the same key, and `hidden: true` hides it. Teachers manage these on the website. Students only read them, and the Firestore rules allow reads for anyone signed in.
- **Search** (`data/dictionary/Dictionary.kt`): words starting with what was typed, after a 250 ms pause. Accent marks don't matter (báhay = bahay), and the exact match comes first. Not found? `DictionaryWords.guessRoots()` strips Tagalog affixes (kumakain → kain, mag-aral → aral) and offers those words.
- **Screen** (`ui/dictionary/DictionaryActivity.kt`, `res/layout/activity_dictionary.xml`): search field, A–Y letters (Abakada), a word list, and a word card with part of speech, meanings, examples, synonyms you can tap, and a "Dinagdag ng guro" tag. Back from a word returns to the list.
- **Keep in sync with the website**: `DictionaryWords.kt` must match the website's `js/dictionaryWords.js`, so both find the same Firestore doc ids.

## Student Progress Monitoring (Paano kinukwenta ang progreso)

BaSaya monitors each student on five skills, one per lesson activity. Skills are never averaged into a single lesson grade: a student can be strong in Talasalitaan and weak in Pagbigkas, and one number would hide that.

The calculation lives in `app/src/main/java/ph/basaya/app/data/progress/SkillScoring.kt` (pure Kotlin, unit-tested in `SkillScoringTest.kt`). The websites use the same rules (`BaSaya-web/js/scoring.js`), so teachers and students see the same numbers.

### 1. One score per skill per lesson: first-try accuracy

Getting an item right on the **first try** is the clearest sign of mastery; needing several tries means the child is still learning. Each activity saves `correct` and `attempts`, and the lesson score is `correct ÷ attempts × 100`.

| Skill | Activity | correct | attempts | items (weight) |
|---|---|---|---|---|
| Lektura (`pagUnawa`) | Lektura | not scored — lectures read | — | — |
| Talasalitaan | Crossword game | words found | words found | words found |
| Pagkilala sa salita | Gawain (practice) | pages right on the first check | pages | pages |
| Pagbigkas | Pronunciation | words right on the first scored try | words | words |
| Comprehension (`pagsusulit`) | Quiz (Pagsusulit) | correct answers | questions | questions |

Notes:
- **Lektura:** lectures have no questions, so this row shows lectures read ("2/5", "Tapos na") until a comprehension check saves results in the same format.
- **Talasalitaan:** there are no wrong answers. Each word found counts once, and wrong swipes are not counted or saved. A finished crossword is always 100%; only a missed deadline lowers it. A lesson's levels are played separately, so the counts add up level by level. Older records that counted wrong swipes are recounted this way when read.
- **Pagkilala sa salita:** a page answered wrong and then fixed with *Subukan Muli* does not earn the point (it is counted in `retries`). `activityScore` uses the same rule.
- **Pagbigkas:** "no speech heard" never counts as a try. Words missed at least once are saved in `hardWords` for the teacher.
- A lesson the student hasn't done yet is **no data, not 0%**, until its deadline passes.
- **Unfinished at the deadline = the rest is wrong:** once a lesson's deadline passes, each scored part left unfinished (game, Gawain, Bigkas, quiz) is scored over **all** its items (`LessonItems.kt`: crossword words, Gawain pages, Bigkas words, quiz questions). What the student got right so far counts, and every item they didn't reach is wrong. For example, 3 of 5 quiz questions answered with 2 right gives 2/5, and an untouched part gives 0%. "So far" comes from the saved place (`resume.activity.score`, `resume.pronunciation.firstTryCorrect`, `resume.quiz.score`) and, for the crossword, from the levels played. The result is dated at the deadline. Skipped parts, lessons without a deadline, and lessons that closed before the student joined don't count. See `SkillScoring.withMissed`; the web uses the same rule (`js/scoring.js`).
- **Quiz resume:** an answer is final once *Susunod* is pressed and is saved in `resume.quiz` (`{ done, total, score, answered: [question index], missed: [question text] }`), so reopening the quiz continues with the questions not answered yet.
- **Comprehension (Pagsusulit):** the quiz also saves `missedQuestions` (the text of each question answered wrong). No report shows it at the moment.

Saved per lesson in Firestore:

```
users/{uid}/assignedLessons/{lessonId}
  skills:
    pagbigkas: { correct: 7, attempts: 10, items: 10, completedAt, eventuallyCorrect: 9, hardWords: ["mataba", "bahay"] }
    talasalitaan: { correct: 12, attempts: 12, items: 12, levels: 3, completedAt }
    pagkilala:  { correct: 2, attempts: 3, items: 3, retries: 1, completedAt }
    pagsusulit: { correct: 8, attempts: 10, items: 10, completedAt, missedQuestions: ["Ano ang …?", "Sino si …?"] }
```

Quizzes taken before skill tracking still count, because `quizScore / quizTotal` already meant the same thing. Older game, Gawain and pronunciation scores used different rules and are left out.

### 2. Combining many lessons: recency-weighted average

For each skill, lessons are put in the order the student finished them, and combined with a weighted average in which **recent lessons count more**, so a student who struggled early but has improved is shown as improving.

```
skill score = Σ (lesson score × weight) ÷ Σ weight

weight = items × 0.85^(lessons since this one)
         latest lesson × 1.00, the one before × 0.85, then × 0.72, × 0.61 …
```

Bigger lessons (more words or questions) also count more than small ones.

**Worked example (Pagbigkas, 4 lessons, lesson 4 is the newest):**

| Lesson | First-try score | Words | Recency | Weight |
|---|---|---|---|---|
| 1 | 40% | 10 | 0.61 | 6.1 |
| 2 | 60% | 8 | 0.72 | 5.8 |
| 3 | 75% | 10 | 0.85 | 8.5 |
| 4 | 90% | 12 | 1.00 | 12.0 |

(40×6.1 + 60×5.8 + 75×8.5 + 90×12) ÷ 32.4 = **71% → Umuunlad, trend ↑**. A plain average would give 66%.

### 3. Levels

| Skill score | Level | Meaning |
|---|---|---|
| 85–100% | Mahusay | Mastered |
| 70–84% | Umuunlad | Getting there |
| 50–69% | Nagsisimula | Needs more practice |
| below 50% | Kailangan ng tulong | Teacher should step in |

### 4. Confidence, trend and alerts

- **Minimum evidence:** a level is shown only after **2 lessons or 10 items** in that skill. Before that the level is *Kulang pa ang datos* (not enough data), so one bad day doesn't label a child.
- **Trend:** with 3 or more lessons, the average of the last 2 lessons is compared with the 2 before them. More than 10 points higher is ↑, more than 10 lower is ↓, otherwise steady.
- **Needs help:** a skill is flagged when the **last 2 lessons were both below 60%**.

### 5. Picture of a student: five separate skills

There is **no combined score** for a student. Each skill keeps its own score, level, trend and alert, so a teacher can see exactly where a student is having a hard time:

| Lektura | Talasalitaan | Pagkilala | Pagbigkas | Comprehension |
|---|---|---|---|---|
| Tapos na (2/2) | 79% Umuunlad ↑ | 92% Mahusay | **45% Kailangan ng tulong ↓** | 88% Mahusay |

Here the student is fine overall but clearly needs help with pronunciation. An average of these numbers would have hidden that.

Lesson **completion** ("8/10 aralin natapos") is shown on its own as well, because unfinished work is a different problem from low scores.

After every finished activity the app recalculates the summary and saves it on the user document, so dashboards read one small document per student:

```
users/{uid}
  skillSummary:
    needsHelp: ["pagbigkas"]        # skills flagged for the teacher
    lessonsAssigned: 10
    updatedAt
    skills:
      pagbigkas: { score: 71, level: "UMUUNLAD", trend: "UP", lessons: 4, items: 40, needsHelp: false, lessonScores: [40, 60, 75, 90] }
      ...
```

The student's **Progreso** screen computes the same summary from the lesson records and shows each skill's score, level and trend.

### 6. Picture of a class (teacher website)

Computed by the teacher dashboard from the students' `skillSummary`:

- **Student grid:** one row per student, one column per skill (score + level colour + trend). Each cell is independent, so the teacher sees at a glance who struggles with which skill.
- **Per skill:** class average of the skill scores, plus how many students are at each level (e.g. "Pagbigkas: 71% average, 6 Mahusay, 12 Umuunlad, 5 Nagsisimula, 2 Kailangan ng tulong"). This shows which skill to reteach.
- **Per lesson:** average lesson score per skill, read from `assignedLessons/{lessonId}.skills`. A lesson far below the others usually means the lesson is too hard or unclear.
- **Alert list:** students whose `skillSummary.needsHelp` is not empty, or with a ↓ trend.

### Skipped games

The admin can tick **"Skip game for this lesson"** on the admin website, which saves `lessons/{lessonId}/game/status = { completion: "skipped" }` and no game levels. A skipped game is treated as **not part of the lesson**:

- **Lesson screen:** the Laro stop is removed from the path and the remaining stops are numbered 1–4.
- **Lesson completion:** the game counts as done, so the lesson can still show *Tapos na*. Lesson progress is out of 3 parts instead of 4, and the skipped game never makes a lesson look "started".
- **Talasalitaan monitoring:** the lesson simply has no Talasalitaan result. It is never 0%, and it doesn't affect the score, trend or needs-help alert, which only look at lessons that had a game. If every assigned lesson skips the game, the Progreso card shows *Walang laro*.
- **Teacher dashboard:** show "—" for that student/lesson instead of a score, and leave the lesson out of the class's Talasalitaan averages.

The app stores the flag per lesson in Room (`lessons.gameSkipped`, database v11) so it also works offline.

### Settings

All thresholds are constants in `SkillScoring.kt`:

| Constant | Value | Meaning |
|---|---|---|
| `RECENCY_DECAY` | 0.85 | Each older lesson counts 15% less |
| `MIN_LESSONS` / `MIN_ITEMS` | 2 / 10 | Evidence needed before a level is shown |
| `TREND_THRESHOLD` | 10 points | Change needed to count as ↑ or ↓ |
| `HELP_THRESHOLD` | 60% | Two lessons in a row below this flag the skill |
| Level cut-offs | 85 / 70 / 50 | Mahusay / Umuunlad / Nagsisimula |

## Main technologies

| Part | Built with |
|---|---|
| Android app | Kotlin, Android Views, Coroutines, Room, Firebase Auth / Firestore / Cloud Messaging, Gson |
| Websites | Plain HTML, CSS and JavaScript modules (no build step), Firebase Web SDK |
| Servers | notify-api: Node.js on Vercel (Firebase Admin SDK, Gmail SMTP). Cloudflare Workers: ElevenLabs TTS + R2, Workers AI Whisper STT |

## Project goal

BaSaya gives students an accessible, playful way to build Filipino reading, vocabulary and pronunciation skills, and gives teachers a clear picture of each child's strengths and weaknesses, skill by skill.
