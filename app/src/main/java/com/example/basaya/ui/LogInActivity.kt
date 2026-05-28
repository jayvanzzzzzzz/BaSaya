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
import com.google.firebase.auth.FirebaseAuth

class LogInActivity : AppCompatActivity() {

    private lateinit var imgBgBookLeft: ImageView
    private lateinit var imgBgBookRight: ImageView

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText

    private lateinit var btnLogin : AppCompatButton
    private lateinit var tvRegister: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_log_in)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
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


        btnLogin.setOnClickListener {

            etEmail = findViewById(R.id.etEmail)
            etPassword = findViewById(R.id.etPassword)

            val email = etEmail.text.toString()
            val password = etPassword.text.toString()

            authHelper.login(email, password) { success, result ->
                if (success) {
                    val uid = result

                    Toast.makeText(this, "Login Success", Toast.LENGTH_SHORT).show()

                    val intent = Intent(this@LogInActivity, MainActivity::class.java)
                    intent.putExtra("uid", uid)
                    startActivity(intent)
                    finish()

                } else {
                    Toast.makeText(this, "Login Failed: $result", Toast.LENGTH_SHORT).show()
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
}