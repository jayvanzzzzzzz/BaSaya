package com.example.basaya.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.basaya.R
import com.example.basaya.data.auth.AuthHelper
import com.example.basaya.data.model.User
import com.example.basaya.data.repository.UserRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var imgBgBookLeft: ImageView
    private lateinit var imgBgBookRight: ImageView

    private lateinit var btnRegister: MaterialButton
    private lateinit var imgRegisterArrow: ImageView
    private lateinit var progressRegister: ProgressBar

    //userInfos
    private lateinit var tilFirstName: TextInputLayout
    private lateinit var tilLastName: TextInputLayout
    private lateinit var etFirstName: EditText
    private lateinit var etMiddleName: EditText
    private lateinit var etLastName: EditText

    //login credentials
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText

    //helper
    private lateinit var authHelper: AuthHelper

    //firebase
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Firebase
        db = FirebaseFirestore.getInstance()

        //helper
        authHelper = AuthHelper()

        // Views
        tilFirstName = findViewById(R.id.tilFirstName)
        tilLastName = findViewById(R.id.tilLastName)
        tilEmail = findViewById(R.id.tilEmail)
        tilPassword = findViewById(R.id.tilPassword)
        etFirstName = findViewById(R.id.etFirstName)
        etMiddleName = findViewById(R.id.etMiddleName)
        etLastName = findViewById(R.id.etLastName)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)

        btnRegister = findViewById(R.id.btnRegister)
        imgRegisterArrow = findViewById(R.id.imgRegisterArrow)
        progressRegister = findViewById(R.id.progressRegister)

        imgBgBookLeft = findViewById(R.id.imgBgBookLeft)
        imgBgBookRight = findViewById(R.id.imgBgBookRight)

        val floatingAnim = AnimationUtils.loadAnimation(this, R.anim.floating)
        imgBgBookLeft.startAnimation(floatingAnim)
        imgBgBookRight.startAnimation(floatingAnim)

        // Clear a field's error as soon as the user edits it
        etFirstName.doAfterTextChanged { tilFirstName.error = null }
        etLastName.doAfterTextChanged { tilLastName.error = null }
        etEmail.doAfterTextChanged { tilEmail.error = null }
        etPassword.doAfterTextChanged { tilPassword.error = null }

        // "Done" on the keyboard submits the form
        etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (btnRegister.isEnabled) btnRegister.performClick()
                true
            } else {
                false
            }
        }

        btnRegister.setOnClickListener { submit() }

        // Login is already underneath us in the back stack; just return to it.
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvBackToLogin).setOnClickListener { finish() }
    }

    private fun submit() {
        val firstName = etFirstName.text.toString().trim()
        val middleName = etMiddleName.text.toString().trim()
        val lastName = etLastName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        // Validation — show every problem at once, focus the first one
        tilFirstName.error =
            if (firstName.isEmpty()) getString(R.string.register_error_first_name) else null
        tilLastName.error =
            if (lastName.isEmpty()) getString(R.string.register_error_last_name) else null
        tilEmail.error = when {
            email.isEmpty() -> getString(R.string.login_error_email_required)
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> getString(R.string.register_error_email_invalid)
            else -> null
        }
        tilPassword.error = when {
            password.isEmpty() -> getString(R.string.login_error_password_required)
            password.length < 8 -> getString(R.string.register_error_password_short)
            else -> null
        }

        val firstInvalid = listOf(
            tilFirstName to etFirstName,
            tilLastName to etLastName,
            tilEmail to etEmail,
            tilPassword to etPassword
        ).firstOrNull { (til, _) -> til.error != null }

        if (firstInvalid != null) {
            firstInvalid.second.requestFocus()
            return
        }

        setLoading(true)

        // Create Firebase Auth account
        authHelper.register(email, password) { success, result ->

            if (!success) {
                Log.e("REGISTER_DEBUG", "Auth registration failed: $result")
                setLoading(false)
                Toast.makeText(
                    this,
                    result ?: getString(R.string.register_failed),
                    Toast.LENGTH_SHORT
                ).show()
                return@register
            }

            val uid = result

            if (uid.isNullOrEmpty()) {
                Log.e("REGISTER_DEBUG", "Auth registration succeeded but returned no uid")
                setLoading(false)
                Toast.makeText(this, R.string.register_failed, Toast.LENGTH_SHORT).show()
                return@register
            }

            // User data for Firestore
            val userData = User(
                uid = uid,
                firstName = firstName,
                middleName = middleName,
                lastName = lastName,
                email = email,
                role = "student",
                createdAt = System.currentTimeMillis()
            )

            //register to firebase
            db.collection("users")
                .document(uid)
                .set(userData)
                .addOnSuccessListener {

                    // assign default lessons to this new user
                    val userRepository = UserRepository()
                    lifecycleScope.launch {
                        try {
                            userRepository.assignRandomLesson(uid)
                        } catch (e: Exception) {
                            Log.e("REGISTER_DEBUG", "Failed to assign random lesson", e)
                        }

                        setLoading(false)

                        Toast.makeText(
                            this@RegisterActivity,
                            R.string.register_success,
                            Toast.LENGTH_SHORT
                        ).show()

                        startActivity(
                            Intent(this@RegisterActivity, LogInActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        )
                        finish()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("REGISTER_DEBUG", "Failed to save user document", e)
                    // Roll back the half-created Auth account so the same email can be retried.
                    authHelper.getCurrentUser()?.delete()
                        ?.addOnFailureListener { err ->
                            Log.e("REGISTER_DEBUG", "Failed to roll back auth user $uid", err)
                        }
                    setLoading(false)
                    Toast.makeText(this, R.string.register_save_failed, Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        btnRegister.isEnabled = !isLoading

        imgRegisterArrow.visibility = if (isLoading) View.GONE else View.VISIBLE
        progressRegister.visibility = if (isLoading) View.VISIBLE else View.GONE
    }
}
