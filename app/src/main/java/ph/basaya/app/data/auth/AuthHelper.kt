package ph.basaya.app.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Wait for Firebase's initial auth state before deciding whether to show login. */
internal suspend fun FirebaseAuth.awaitInitialUser(): FirebaseUser? =
    suspendCancellableCoroutine { continuation ->
        val listener = object : FirebaseAuth.AuthStateListener {
            override fun onAuthStateChanged(auth: FirebaseAuth) {
                auth.removeAuthStateListener(this)
                if (continuation.isActive) continuation.resume(auth.currentUser)
            }
        }
        addAuthStateListener(listener)
        continuation.invokeOnCancellation { removeAuthStateListener(listener) }
    }

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

    // Email a password reset link. The callback gets null on success, else the error.
    fun sendPasswordReset(email: String, onResult: (Exception?) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task -> onResult(task.exception) }
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
