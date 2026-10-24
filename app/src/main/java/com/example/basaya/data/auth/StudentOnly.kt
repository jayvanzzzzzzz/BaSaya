package com.example.basaya.data.auth

import com.google.firebase.firestore.DocumentSnapshot

/**
 * This app is for students only. Teachers and admins share the same Firebase project but use
 * the website, and any other role (blank, mistyped, unknown) is turned away too.
 */
object StudentOnly {

    /** On LogInActivity's intent: show the "students only" pop-up (after a saved login was signed out). */
    const val EXTRA_STUDENTS_ONLY = "studentsOnly"

    fun isStudent(doc: DocumentSnapshot): Boolean =
        doc.exists() && doc.getString("role")?.trim()?.lowercase() == "student"
}
