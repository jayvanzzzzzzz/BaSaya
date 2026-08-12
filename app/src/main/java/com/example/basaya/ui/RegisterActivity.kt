package com.example.basaya.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.basaya.R
import com.example.basaya.data.auth.AuthHelper
import com.example.basaya.data.repository.UserRepository
import com.example.basaya.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.Calendar

class RegisterActivity : AppCompatActivity() {

    private lateinit var imgBgBookLeft: ImageView
    private lateinit var imgBgBookRight: ImageView

    private lateinit var btnRegister: AppCompatButton
    private lateinit var progressRegister: ProgressBar
    private var originalButtonText: CharSequence = ""

    private lateinit var tvBackToLogin: TextView

    //login credentials
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText

    //userInfos
    private lateinit var etFirstName: EditText
    private lateinit var etMiddleName: EditText
    private lateinit var etLastName: EditText
    private lateinit var etDOB: EditText
    private lateinit var spinnerGender: Spinner
    private lateinit var etUsername: EditText
    private lateinit var etConfirmPassword: EditText

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

        // Views
        etFirstName = findViewById(R.id.etFirstName)
        etMiddleName = findViewById(R.id.etMiddleName)
        etLastName = findViewById(R.id.etLastName)
        etDOB = findViewById(R.id.etDOB)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)

        spinnerGender = findViewById(R.id.spinnerGender)
        etEmail = findViewById(R.id.etEmail)

        //helper
        authHelper = AuthHelper()

        btnRegister = findViewById(R.id.btnRegister)
        progressRegister = findViewById(R.id.progressRegister)
        originalButtonText = btnRegister.text

        btnRegister.setOnClickListener {

            val firstName = etFirstName.text.toString().trim()
            val middleName = etMiddleName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            val dob = etDOB.text.toString().trim()
            val gender = spinnerGender.selectedItem.toString()
            val email = etEmail.text.toString().trim()
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            // Validation
            if (
                firstName.isEmpty() ||
                lastName.isEmpty() ||
                dob.isEmpty() ||
                email.isEmpty() ||
                username.isEmpty() ||
                password.isEmpty() ||
                confirmPassword.isEmpty()
            ) {

                Toast.makeText(
                    this,
                    "Please fill all fields",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            if (password.length < 8) {
                Toast.makeText(
                    this,
                    "Password must be at least 8 characters",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(
                    this,
                    "Passwords do not match",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            setLoading(true)

            val normalizedUsername = username.trim().lowercase()

            //check if username exist
            db.collection("usernames")
                .document(normalizedUsername)
                .get()
                .addOnSuccessListener { document ->

                    if (document.exists()) {
                        setLoading(false)
                        Toast.makeText(
                            this,
                            "Username already exists",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@addOnSuccessListener
                    }

                    // Create Firebase Auth account
                    authHelper.register(email, password) { success, result ->

                        if (!success) {
                            setLoading(false)
                            Toast.makeText(
                                this,
                                result ?: "Registration Failed",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@register
                        }

                        val uid = result

                        if (uid.isNullOrEmpty()) {
                            setLoading(false)
                            Toast.makeText(this, "Registration Failed", Toast.LENGTH_SHORT).show()
                            return@register
                        }

                        // User data for Firestore
                        val userData = User(
                            uid = uid,
                            firstName = firstName,
                            middleName = middleName,
                            lastName = lastName,
                            dob = dob,
                            gender = gender,
                            email = email,
                            username = username,
                            role = "student",
                            createdAt = System.currentTimeMillis()
                        )

                        //register to firebase
                        db.collection("users")
                            .document(uid)
                            .set(userData)
                            .addOnSuccessListener {

                                // reserve the username only after user doc is confirmed saved
                                db.collection("usernames")
                                    .document(normalizedUsername)
                                    .set(mapOf("uid" to uid))

                                // 👇 assign default lessons to this new user
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
                                        "Registration Successful!",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    startActivity(
                                        Intent(
                                            this@RegisterActivity,
                                            LogInActivity::class.java
                                        )
                                    )

                                    finish()
                                }
                            }
                            .addOnFailureListener {
                                setLoading(false)
                                Toast.makeText(
                                    this,
                                    "Failed to save user data",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }

                }
                .addOnFailureListener {
                    setLoading(false)
                    Toast.makeText(this, "Something went wrong. Try again.", Toast.LENGTH_SHORT).show()
                }
        }

        imgBgBookLeft = findViewById(R.id.imgBgBookLeft)
        imgBgBookRight = findViewById(R.id.imgBgBookRight)

        val floatingAnim = AnimationUtils.loadAnimation(this, R.anim.floating)

        imgBgBookLeft.startAnimation(floatingAnim)
        imgBgBookRight.startAnimation(floatingAnim)

        tvBackToLogin = findViewById(R.id.tvBackToLogin)

        tvBackToLogin.paintFlags = tvBackToLogin.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        tvBackToLogin.setOnClickListener {
            val intent = Intent(this@RegisterActivity, LogInActivity::class.java)
            startActivity(intent)
            finish()
        }

        etDOB.setOnClickListener {
            showDatePicker()
        }

        val genders = arrayOf("Male", "Female")

        val adapter = object : ArrayAdapter<String>(
            this,
            R.layout.spinner_item_white,
            genders
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = layoutInflater.inflate(R.layout.spinner_item_white, parent, false)
                val text = view.findViewById<TextView>(R.id.text1)
                text.text = genders[position]
                return view
            }

            override fun getDropDownView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                val view = layoutInflater.inflate(R.layout.spinner_dropdown_white, parent, false)
                val text = view.findViewById<TextView>(R.id.text1)
                text.text = genders[position]
                return view
            }
        }

        spinnerGender.adapter = adapter

    }

    private fun setLoading(isLoading: Boolean) {
        btnRegister.isEnabled = !isLoading

        if (isLoading) {
            btnRegister.text = ""
            progressRegister.visibility = View.VISIBLE
        } else {
            btnRegister.text = originalButtonText
            progressRegister.visibility = View.GONE
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePicker = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                val date = "${selectedMonth + 1}/$selectedDay/$selectedYear"
                etDOB.setText(date)
            },
            year,
            month,
            day
        )

        datePicker.show()
    }
}