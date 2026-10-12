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
import android.animation.ObjectAnimator
import android.util.Log
import android.view.animation.LinearInterpolator
import android.view.View
import android.widget.ProgressBar
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputLayout

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
                setLoading(false)

                if (success) {
                    val uid = result ?: return@login

                    Toast.makeText(this, "Login Success", Toast.LENGTH_SHORT).show()

                    val intent = Intent(this@LogInActivity, MainActivity::class.java)
                    intent.putExtra("uid", uid)
                    startActivity(intent)
                    finish()
                } else {
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