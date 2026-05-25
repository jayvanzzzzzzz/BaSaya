package com.example.basaya.data.auth

class AuthHelper {

    private val auth = com.google.firebase.auth.FirebaseAuth.getInstance()

    // Register user
    fun register(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, auth.currentUser?.uid)
                } else {
                    onResult(false, task.exception?.message)
                }
            }
    }

    // Login user
    fun login(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, auth.currentUser?.uid)
                } else {
                    onResult(false, task.exception?.message)
                }
            }
    }

    // Get current user
    fun getCurrentUser() = auth.currentUser

    // Logout
    fun logout() {
        auth.signOut()
    }

    // Check if logged in
    fun isLoggedIn(): Boolean {
        return auth.currentUser != null
    }
}