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
       ├── Lecture
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
Lecture can be revisited even after completion.
Game, Activity, and Quiz become locked after completion.
Completed Game, Activity, and Quiz content displays the student's final score.
Offline Learning

BaSaya supports downloading lessons for offline use.

When a student downloads a lesson, the app preloads its content into the Android app's local Room database.

Download Lesson
      │
      ▼
┌─────────────────────┐
│ Lecture             │
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
     ├── Manage Lectures
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
