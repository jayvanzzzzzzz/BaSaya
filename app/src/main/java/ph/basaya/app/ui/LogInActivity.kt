package ph.basaya.app.ui

import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import ph.basaya.app.R
import ph.basaya.app.data.auth.AuthHelper
import ph.basaya.app.data.auth.StudentOnly
import android.animation.ObjectAnimator
import android.util.Log
import android.view.animation.LinearInterpolator
import android.view.View
import android.widget.ProgressBar
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Patterns
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider

class LogInActivity : AppCompatActivity() {

    private lateinit var imgBgBookLeft: ImageView
    private lateinit var imgBgBookRight: ImageView

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout

    private lateinit var btnLogin : AppCompatButton
    private lateinit var tvRegister: TextView
    private lateinit var btnGoogleLogin: View

    private lateinit var imgLoginArrow: ImageView
    private lateinit var progressLogin: ProgressBar

    private var arrowSpinAnimator: ObjectAnimator? = null

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) {
                setLoading(false)
                return@registerForActivityResult
            }

            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken.isNullOrBlank()) {
                    setLoading(false)
                    Toast.makeText(this, R.string.register_google_setup_error, Toast.LENGTH_LONG).show()
                    return@registerForActivityResult
                }
                signInWithGoogleToken(idToken)
            } catch (e: ApiException) {
                Log.e("LOGIN_DEBUG", "Google account selection failed", e)
                setLoading(false)
                Toast.makeText(this, R.string.register_google_failed, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_log_in)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            // Include the IME so the scrollable form shrinks above the keyboard (edge-to-edge).
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val authHelper = AuthHelper()

        imgBgBookLeft = findViewById(R.id.imgBgBookLeft)
        imgBgBookRight = findViewById(R.id.imgBgBookRight)

        val floatingAnim = AnimationUtils.loadAnimation(this, R.anim.floating)

        imgBgBookLeft.startAnimation(floatingAnim)
        imgBgBookRight.startAnimation(floatingAnim)

        btnLogin = findViewById(R.id.btnLogin)
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin)
        imgLoginArrow = findViewById(R.id.imgLoginArrow)
        progressLogin = findViewById(R.id.progressLogin)

        arrowSpinAnimator = ObjectAnimator.ofFloat(imgLoginArrow, View.ROTATION, 0f, 360f).apply {
            duration = 800
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
        }

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        tilEmail = findViewById(R.id.tilEmail)
        tilPassword = findViewById(R.id.tilPassword)

        // Clear a field's error as soon as the user edits it
        etEmail.doAfterTextChanged { tilEmail.error = null }
        etPassword.doAfterTextChanged { tilPassword.error = null }

        // "Done" on the keyboard submits the form
        etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (btnLogin.isEnabled) btnLogin.performClick()
                true
            } else {
                false
            }
        }

        btnLogin.setOnClickListener {

            val email = etEmail.text.toString()
            val password = etPassword.text.toString()

            tilEmail.error = if (email.isBlank()) getString(R.string.login_error_email_required) else null
            tilPassword.error = if (password.isBlank()) getString(R.string.login_error_password_required) else null

            if (email.isBlank()) {
                etEmail.requestFocus()
                return@setOnClickListener
            }
            if (password.isBlank()) {
                etPassword.requestFocus()
                return@setOnClickListener
            }

            setLoading(true)

            authHelper.login(email, password) { success, result ->
                if (success) {
                    val uid = result ?: run {
                        setLoading(false)
                        return@login
                    }

                    // Only students get in: teachers, admins and any other role use the website.
                    FirebaseFirestore.getInstance().collection("users").document(uid).get()
                        .addOnSuccessListener { doc ->
                            setLoading(false)
                            if (!StudentOnly.isStudent(doc)) {
                                authHelper.logout()
                                showStudentsOnlyDialog()
                                return@addOnSuccessListener
                            }

                            Toast.makeText(this, "Login Success", Toast.LENGTH_SHORT).show()

                            val intent = Intent(this@LogInActivity, MainActivity::class.java)
                            intent.putExtra("uid", uid)
                            startActivity(intent)
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Log.d("LOGIN_DEBUG", "Role check failed: ${e.message}")
                            setLoading(false)
                            authHelper.logout()
                            Toast.makeText(
                                this,
                                R.string.login_error_role_check_failed,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                } else {
                    setLoading(false)
                    Log.d("LOGIN_DEBUG", "Login failed: $result")

                    tilPassword.error = getString(R.string.login_error_invalid_credentials)
                }
            }
        }

        tvRegister = findViewById(R.id.tvRegister)

        tvRegister.paintFlags = tvRegister.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        tvRegister.setOnClickListener {
            val intent = Intent(this@LogInActivity, RegisterActivity::class.java)
            startActivity(intent)
        }

        btnGoogleLogin.setOnClickListener { startGoogleSignIn() }

        findViewById<View>(R.id.btnForgotPassword).setOnClickListener {
            showForgotPasswordDialog(authHelper)
        }

        // A saved login that turned out not to be a student's (MainActivity signed it out).
        if (savedInstanceState == null && intent.getBooleanExtra(StudentOnly.EXTRA_STUDENTS_ONLY, false)) {
            showStudentsOnlyDialog()
        }
    }

    /**
     * Asks for the email and has Firebase send the reset link. The answer is the same
     * whether or not the email has an account, so nobody can use it to check who is registered.
     */
    private fun showForgotPasswordDialog(authHelper: AuthHelper) {
        if (isFinishing || isDestroyed) return
        val content = layoutInflater.inflate(R.layout.dialog_forgot_password, null)
        val tilForgotEmail = content.findViewById<TextInputLayout>(R.id.tilForgotEmail)
        val etForgotEmail = content.findViewById<EditText>(R.id.etForgotEmail)
        etForgotEmail.setText(etEmail.text.toString().trim())
        etForgotEmail.doAfterTextChanged { tilForgotEmail.error = null }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.forgot_title)
            .setView(content)
            .setNegativeButton(R.string.forgot_cancel, null)
            // Wired below so a bad email keeps the dialog open.
            .setPositiveButton(R.string.forgot_send, null)
            .create()

        dialog.setOnShowListener {
            val send = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val submit: () -> Unit = submit@{
                val email = etForgotEmail.text.toString().trim()
                tilForgotEmail.error = when {
                    email.isEmpty() -> getString(R.string.forgot_error_email_required)
                    !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> getString(R.string.forgot_error_email_invalid)
                    else -> null
                }
                if (tilForgotEmail.error != null) return@submit

                send.isEnabled = false
                send.setText(R.string.forgot_sending)
                authHelper.sendPasswordReset(email) { error ->
                    if (isFinishing || isDestroyed) return@sendPasswordReset
                    send.isEnabled = true
                    send.setText(R.string.forgot_send)
                    // No account for this email: answer the same as a sent link.
                    if (error == null || (error as? FirebaseAuthException)?.errorCode == "ERROR_USER_NOT_FOUND") {
                        dialog.dismiss()
                        showResetSentDialog(email)
                        return@sendPasswordReset
                    }
                    Log.d("LOGIN_DEBUG", "Password reset failed: ${error.message}")
                    tilForgotEmail.error = getString(
                        when {
                            error is FirebaseNetworkException -> R.string.forgot_error_network
                            error is FirebaseTooManyRequestsException -> R.string.forgot_error_too_many
                            (error as? FirebaseAuthException)?.errorCode == "ERROR_INVALID_EMAIL" ->
                                R.string.forgot_error_email_invalid
                            else -> R.string.forgot_error_generic
                        }
                    )
                }
            }
            send.setOnClickListener { submit() }
            etForgotEmail.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND && send.isEnabled) {
                    submit()
                    true
                } else {
                    false
                }
            }
        }
        dialog.show()
    }

    private fun showResetSentDialog(email: String) {
        if (isFinishing || isDestroyed) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.forgot_sent_title)
            .setMessage(getString(R.string.forgot_sent_message, email))
            .setPositiveButton(R.string.forgot_sent_ok, null)
            .show()
    }

    private fun showStudentsOnlyDialog() {
        if (isFinishing || isDestroyed) return
        etPassword.text?.clear()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.login_students_only_title)
            .setMessage(R.string.login_students_only_message)
            .setPositiveButton(R.string.login_students_only_ok, null)
            .show()
    }

    private fun setLoading(isLoading: Boolean) {
        btnLogin.isEnabled = !isLoading
        btnGoogleLogin.isEnabled = !isLoading

        imgLoginArrow.visibility = if (isLoading) View.GONE else View.VISIBLE
        progressLogin.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun startGoogleSignIn() {
        val clientIdResource = resources.getIdentifier("default_web_client_id", "string", packageName)
        val webClientId = clientIdResource.takeIf { it != 0 }?.let(::getString)
        if (webClientId.isNullOrBlank()) {
            Toast.makeText(this, R.string.register_google_setup_error, Toast.LENGTH_LONG).show()
            return
        }

        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        val googleSignInClient = GoogleSignIn.getClient(this, options)
        setLoading(true)
        googleSignInClient.signOut().addOnCompleteListener(this) { task ->
            if (!task.isSuccessful) {
                Log.e("LOGIN_DEBUG", "Could not reset Google account selection", task.exception)
                setLoading(false)
                Toast.makeText(this, R.string.register_google_failed, Toast.LENGTH_SHORT).show()
                return@addOnCompleteListener
            }
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    private fun signInWithGoogleToken(idToken: String) {
        FirebaseAuth.getInstance()
            .signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
            .addOnCompleteListener(this) { task ->
                if (!task.isSuccessful) {
                    Log.e("LOGIN_DEBUG", "Firebase Google sign-in failed", task.exception)
                    setLoading(false)
                    val errorCode = (task.exception as? FirebaseAuthException)?.errorCode.orEmpty()
                    val message = when {
                        errorCode.contains("OPERATION_NOT_ALLOWED") ->
                            R.string.login_google_disabled
                        errorCode.contains("ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL") ->
                            R.string.register_google_email_exists
                        else -> R.string.register_google_failed
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    return@addOnCompleteListener
                }

                val user = task.result.user
                if (user == null) {
                    setLoading(false)
                    Toast.makeText(this, R.string.register_google_failed, Toast.LENGTH_SHORT).show()
                    return@addOnCompleteListener
                }

                getSharedPreferences(CompleteProfileActivity.PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(CompleteProfileActivity.KEY_PENDING_GOOGLE_UID, user.uid)
                    .apply()

                FirebaseFirestore.getInstance().collection("users").document(user.uid).get()
                    .addOnSuccessListener { profile ->
                        if (!profile.exists()) {
                            setLoading(false)
                            startActivity(Intent(this, CompleteProfileActivity::class.java))
                            finish()
                            return@addOnSuccessListener
                        }

                        clearPendingGoogleProfile()
                        setLoading(false)
                        if (!StudentOnly.isStudent(profile)) {
                            FirebaseAuth.getInstance().signOut()
                            Toast.makeText(
                                this,
                                R.string.register_google_wrong_portal,
                                Toast.LENGTH_LONG
                            ).show()
                            return@addOnSuccessListener
                        }

                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .putExtra("uid", user.uid)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        )
                        finish()
                    }
                    .addOnFailureListener { error ->
                        Log.e("LOGIN_DEBUG", "Failed to verify Google account profile", error)
                        FirebaseAuth.getInstance().signOut()
                        clearPendingGoogleProfile()
                        setLoading(false)
                        Toast.makeText(
                            this,
                            R.string.register_google_profile_check_failed,
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
    }

    private fun clearPendingGoogleProfile() {
        getSharedPreferences(CompleteProfileActivity.PREFS_NAME, MODE_PRIVATE)
            .edit()
            .remove(CompleteProfileActivity.KEY_PENDING_GOOGLE_UID)
            .apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        arrowSpinAnimator?.cancel()
    }

}