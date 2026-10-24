package com.example.basaya.ui

import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.basaya.R
import com.example.basaya.data.auth.AuthHelper
import com.example.basaya.data.auth.StudentOnly
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
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException

class LogInActivity : AppCompatActivity() {

    private lateinit var imgBgBookLeft: ImageView
    private lateinit var imgBgBookRight: ImageView

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout

    private lateinit var btnLogin : AppCompatButton
    private lateinit var tvRegister: TextView

    private lateinit var imgLoginArrow: ImageView
    private lateinit var progressLogin: ProgressBar

    private var arrowSpinAnimator: ObjectAnimator? = null

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

        imgLoginArrow.visibility = if (isLoading) View.GONE else View.VISIBLE
        progressLogin.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        arrowSpinAnimator?.cancel()
    }

}