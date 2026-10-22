BaSaya is a Filipino learning application designed to help students learn through interactive lessons and activities.

The system has two main parts:

Student Android App — where students access their assigned lessons, lectures, games, activities, and quizzes.
Admin Web Panel — where teachers and administrators can manage lesson content. (Currently in development.)

Both clients use the same Firebase backend, allowing lesson content and student progress to stay synchronized.

 System Architecture

BaSaya uses a two-client, single-database architecture.

The Android app and web admin panel communicate directly with Firebase. There is currently no separate backend/API server between the clients and Firebase.

             ┌─────────────────────┐
             │   Student Android   │
             │        App          │
             └──────────┬──────────┘
                        │
                        │
                        ▼
              ┌───────────────────┐
              │      Firebase     │
              │                   │
              │ Authentication    │
              │ Cloud Firestore   │
              └─────────┬─────────┘
                        │
                        │
                        ▼
             ┌─────────────────────┐
             │    Admin Web Panel  │
             │   (In Development) │
             └─────────────────────┘

The Android app also uses Room as a local cache so downloaded lesson content can be accessed offline.

 Student Login

Students sign in using their email and password.

Open BaSaya
     │
     ▼
Enter email + password
     │
     ▼
Firebase Authentication
     │
     ▼
Credentials verified
     │
     ▼
Student Dashboard

Firebase Authentication handles the login and maintains the student's authenticated session.

Student Learning Flow

After logging in, students can see the lessons assigned to them.

Student Dashboard
       │
       ▼
Browse Assigned Lessons
       │
       ▼
Open a Lesson
       │
       ├── Lektura
       ├── Game
       ├── Activity
       └── Quiz
              │
              ▼
        Complete Content
              │
              ▼
      Save Result / Progress
              │
              ▼
      Dashboard Updates

Student progress is saved to Firestore, allowing the dashboard to reflect the student's current progress, completed content, scores, and locked/unlocked states.

Content Rules
Lektura can be revisited even after completion.
Game, Activity, and Quiz become locked after completion.
Completed Game, Activity, and Quiz content displays the student's final score.
Offline Learning

BaSaya supports downloading lessons for offline use.

When a student downloads a lesson, the app preloads its content into the Android app's local Room database.

Download Lesson
      │
      ▼
┌─────────────────────┐
│ Lektura             │
│ Game / Crossword    │
│ Activity            │
│ Quiz                │
└──────────┬──────────┘
           │
           ▼
     Room Database
           │
           ▼
    Available Offline

This allows students to access downloaded lesson content even when they don't have an internet connection.

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

The calculation lives in `app/src/main/java/com/example/basaya/data/progress/SkillScoring.kt` (pure Kotlin, unit-tested in `SkillScoringTest.kt`). The admin web panel should use the same rules so teachers and students see the same numbers.

### 1. One score per skill per lesson: first-try accuracy

Getting an item right on the **first try** is the clearest sign of mastery; needing several tries means the child is still learning. Each activity saves `correct` and `attempts`, and the lesson score is `correct ÷ attempts × 100`.

| Skill | Activity | correct | attempts | items (weight) |
|---|---|---|---|---|
| Lektura (`pagUnawa`) | Lektura | not scored — lectures read | — | — |
| Talasalitaan | Crossword game | words found | words found + wrong swipes | words found |
| Pagkilala sa salita | Gawain (practice) | pages right on the first check | pages | pages |
| Pagbigkas | Pronunciation | words right on the first scored try | words | words |
| Comprehension (`pagsusulit`) | Quiz (Pagsusulit) | correct answers | questions | questions |

Notes:
- **Lektura:** lectures have no questions, so this row shows lectures read ("2/5", "Tapos na") until a comprehension check saves results in the same format.
- **Talasalitaan:** a wrong swipe is a 2+ letter word that isn't in the level. Re-swiping a word already found and single-letter taps don't count. A lesson's levels are played separately, so the counts add up level by level.
- **Pagkilala sa salita:** a page answered wrong and then fixed with *Subukan Muli* does not earn the point (it is counted in `retries`). `activityScore` uses the same rule.
- **Pagbigkas:** "no speech heard" never counts as a try. Words missed at least once are saved in `hardWords` for the teacher.
- A lesson the student hasn't done yet is **no data, not 0%**, until its deadline passes.
- **Unfinished at the deadline = the rest is wrong:** once a lesson's deadline passes, each scored part left unfinished (game, Gawain, Bigkas, quiz) is scored over **all** its items (`LessonItems.kt`: crossword words, Gawain pages, Bigkas words, quiz questions). What the student got right so far counts, and every item they didn't reach is wrong. For example, 3 of 5 quiz questions answered with 2 right gives 2/5, and an untouched part gives 0%. "So far" comes from the saved place (`resume.activity.score`, `resume.pronunciation.firstTryCorrect`, `resume.quiz.score`) and, for the crossword, from the levels played. The result is dated at the deadline. Skipped parts, lessons without a deadline, and lessons that closed before the student joined don't count. See `SkillScoring.withMissed`; the web uses the same rule (`js/scoring.js`).
- **Quiz resume:** an answer is final once *Susunod* is pressed and is saved in `resume.quiz` (`{ done, total, score, answered: [question index], missed: [question text] }`), so reopening the quiz continues with the questions not answered yet.
- **Comprehension (Pagsusulit):** the quiz also saves `missedQuestions` (the text of each question answered wrong) for the teacher's "What to re-teach" item analysis in the web Class Report.

Saved per lesson in Firestore:

```
users/{uid}/assignedLessons/{lessonId}
  skills:
    pagbigkas: { correct: 7, attempts: 10, items: 10, completedAt, eventuallyCorrect: 9, hardWords: ["mataba", "bahay"] }
    talasalitaan: { correct: 12, attempts: 15, items: 12, wrongTries: 3, levels: 3, completedAt }
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

### 6. Picture of a class (admin web panel)

Computed by the teacher dashboard from the students' `skillSummary`:

- **Student grid:** one row per student, one column per skill (score + level colour + trend). Each cell is independent, so the teacher sees at a glance who struggles with which skill.
- **Per skill:** class average of the skill scores, plus how many students are at each level (e.g. "Pagbigkas: 71% average, 6 Mahusay, 12 Umuunlad, 5 Nagsisimula, 2 Kailangan ng tulong"). This shows which skill to reteach.
- **Per lesson:** average lesson score per skill, read from `assignedLessons/{lessonId}.skills`. A lesson far below the others usually means the lesson is too hard or unclear.
- **Alert list:** students whose `skillSummary.needsHelp` is not empty, or with a ↓ trend.

### Skipped games

A teacher can tick **"Skip game for this lesson"** in the admin panel, which saves `lessons/{lessonId}/game/status = { completion: "skipped" }` and no game levels. A skipped game is treated as **not part of the lesson**:

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

Admin Web Panel

The admin panel is currently in development.

The planned workflow is:

Admin Login
     │
     ▼
Admin Dashboard
     │
     ├── Create Lessons
     ├── Edit Lessons
     ├── Delete Lessons
     ├── Manage Lektura
     ├── Manage Games
     ├── Manage Activities
     └── Manage Quizzes

Changes made through the admin panel are saved to Firestore, allowing the Android application to synchronize updated lesson content.

Admin access is intended to be restricted to authorized staff through Firestore Security Rules rather than relying only on client-side checks.

 Overall Data Flow

At a high level, the system works like this:

       STUDENT APP                    ADMIN PANEL
            │                             │
            │                             │
            └──────────────┬──────────────┘
                           │
                           ▼
                 Firebase Backend
                 ┌─────────────────┐
                 │ Authentication  │
                 │ Cloud Firestore │
                 └────────┬────────┘
                          │
                          ▼
                    Student Data
                    & Lesson Data
                          │
                          ▼
                    Room Database
                  (Android Offline
                       Cache)

In simple terms: Firebase acts as the shared backend for BaSaya, while Room provides local storage for downloaded content on the Android app.

 Main Technologies
Student Android App
Kotlin
Android
Firebase Authentication
Cloud Firestore
Room Database
Coroutines
Admin Web Panel
Web-based
Firebase Authentication
Cloud Firestore
Currently in development
 Project Goal

BaSaya aims to provide students with an accessible and interactive way to learn Filipino through a combination of lectures, games, activities, and quizzes, while also giving teachers and administrators a way to manage educational content.
