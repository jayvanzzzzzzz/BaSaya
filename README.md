-- USER LOGIN FLOW (Android App) --
  User opens Android App
          ↓
  Login (email + password)
          ↓
  Backend checks database
          ↓
  Validate credentials + role = "user"
          ↓
  Return success token
          ↓
  Redirect → User Dashboard

-- USER APP FLOW (Student / Member) --
  User Dashboard
      ↓
  View lessons / services
      ↓
  Select lesson or service
      ↓
  View details
      ↓
  Submit action (quiz / booking / etc.)
      ↓
  Send request to backend
      ↓
  Database stores result
      ↓
  App updates UI (history/status)

-- ADMIN LOGIN FLOW (Web System) --
  Admin opens Web Login Page
          ↓
  Enter credentials
          ↓
  Backend verifies user
          ↓
  Check role = "admin"
          ↓
  IF admin → allow access
  ELSE → deny access / redirect
          ↓
  Open Admin Dashboard

-- ADMIN WEB FLOW (Content Management) --
  Admin Dashboard
      ↓
  Manage Lessons / Services
      ↓
  Add / Edit / Delete content
      ↓
  Save changes to database
      ↓
  Database updates in real time
      ↓
  Android app reflects updates instantly

--  BACKEND / DATABASE FLOW (Core System) --
Android App + Web Admin
            ↓
     API / Firebase
            ↓
     Database (Cloud)
            ↓
     Returns Data

-- ROLE SECURITY FLOW --
  Login Request
      ↓
  Check credentials
      ↓
  Check role
      ├── user → Android App access only
      └── admin → Web access only
      ↓
  Reject unauthorized access

-- SIMPLE OVERALL SYSTEM VIEW --

            🌐 WEB ADMIN
         (Manage Lessons/Data)
                    │
                    ▼
        ☁️ DATABASE / BACKEND
     (Firebase / API Server)
                    ▲
                    │
📱 ANDROID APP (USERS)
 (View Lessons / Submit / Learn)


-- SUMMARY --
The system follows a role-based architecture where the Android application is used by end-users to access lessons and features,
while a web-based admin panel is used to manage and update system content. Both platforms communicate through a centralized cloud database.
