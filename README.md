System Architecture

BaSaya runs on a two-client, single-database architecture: a Kotlin Android app for students, and a web-based admin panel (in development) for teachers/administrators to manage lesson content. Both clients read from and write to a shared Firebase backend — there is no intermediary API server; Firebase Authentication and Cloud Firestore serve that role directly.

Student Login Flow (Android)
Open App
   │
   ▼
Enter email + password
   │
   ▼
Firebase Authentication verifies credentials
   │
   ▼
On success → session token issued
   │
   ▼
Redirect to Student Dashboard
Student App Flow
Student Dashboard
   │
   ▼
Browse assigned lessons
   │
   ▼
Open a lesson → Lecture / Game / Activity / Quiz
   │
   ▼
Complete content → result written to Firestore
   │
   ▼
Dashboard reflects progress (locked/completed state, scores)

Lecture content can be revisited anytime after completion. Game, Activity, and Quiz content locks once finished, showing the student's final score.

Admin Login Flow (Web)
Open Admin Web Panel
   │
   ▼
Enter credentials
   │
   ▼
Firebase Authentication verifies credentials
   │
   ▼
Access granted to Admin Dashboard

(Status: in development. Role-based access — restricting admin login to authorized staff accounts only — is planned via Firestore Security Rules, enforced server-side rather than checked client-side.)

Admin Content Management Flow
Admin Dashboard
   │
   ▼
Create / Edit / Delete lessons, lectures, games, activities, quizzes
   │
   ▼
Changes saved to Firestore
   │
   ▼
Android app syncs updated content automatically
Data Flow Overview
        ANDROID APP                    WEB ADMIN PANEL
     (student lessons,                (lesson & content
      quizzes, progress)                management)
            │                                │
            └──────────────┬─────────────────┘
                           ▼
                 Firebase Authentication
                    + Cloud Firestore
                            │
                            ▼
              Room (local offline cache,
               Android app only)
