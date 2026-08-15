package com.example.basaya.ui

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.widget.TextView
import android.widget.Toast
import com.example.basaya.R
import com.example.basaya.data.auth.AuthHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class JoinClassActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var authHelper: AuthHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_join_class)

        db = FirebaseFirestore.getInstance()
        authHelper = AuthHelper()

        val statusBarBg = findViewById<View>(R.id.statusBarBg)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            statusBarBg.layoutParams.height = statusBarHeight
            statusBarBg.requestLayout()
            insets
        }

        window.statusBarColor = ContextCompat.getColor(this, R.color.white)

        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true

        val btnClose = findViewById<View>(R.id.btnClose)
        val btnJoin = findViewById<MaterialButton>(R.id.btnJoin)
        val tilClassCode = findViewById<TextInputLayout>(R.id.tilClassCode)
        val etClassCode = findViewById<TextInputEditText>(R.id.etClassCode)
        val tvFullName = findViewById<TextView>(R.id.tvFullName)
        val tvEmail = findViewById<TextView>(R.id.tvEmail)

        btnClose.setOnClickListener { finish() }

        loadSignedInAccount(tvFullName, tvEmail)

        btnJoin.setOnClickListener {
            val code = etClassCode.text?.toString()?.trim()?.uppercase().orEmpty()

            if (code.isEmpty()) {
                tilClassCode.error = "Ilagay ang class code"
                return@setOnClickListener
            }
            tilClassCode.error = null

            joinClass(code, btnJoin, tilClassCode)
        }
    }

    private fun loadSignedInAccount(tvFullName: TextView, tvEmail: TextView) {
        val user = authHelper.getCurrentUser() ?: return
        tvEmail.text = user.email ?: ""

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                val firstName = doc.getString("firstName") ?: ""
                val lastName = doc.getString("lastName") ?: ""
                val fullName = "$firstName $lastName".trim()
                if (fullName.isNotEmpty()) tvFullName.text = fullName
            }
    }

    private fun joinClass(code: String, btnJoin: MaterialButton, tilClassCode: TextInputLayout) {
        val uid = authHelper.getCurrentUser()?.uid
        if (uid == null) {
            Toast.makeText(this, "Kailangan mong mag-log in muli.", Toast.LENGTH_SHORT).show()
            return
        }

        setJoining(true, btnJoin)

        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                val firstName = userDoc.getString("firstName") ?: ""
                val lastName = userDoc.getString("lastName") ?: ""

                val fullName = "$lastName, $firstName"

                db.collection("classes")
                    .whereEqualTo("classCode", code)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        if (snapshot.isEmpty) {
                            setJoining(false, btnJoin)
                            tilClassCode.error = "Walang klaseng natagpuan sa code na ito"
                            return@addOnSuccessListener
                        }

                        val classDoc = snapshot.documents[0]
                        val classId = classDoc.id
                        val className = classDoc.getString("className") ?: ""

                        val studentRef = db.collection("classes").document(classId)
                            .collection("students").document(uid)
                        val userRef = db.collection("users").document(uid)

                        val batch = db.batch()
                        batch.set(studentRef, mapOf(
                            "fullName" to fullName,
                            "joinedAt" to FieldValue.serverTimestamp()
                        ))
                        batch.update(userRef, mapOf(
                            "classId" to classId,
                            "className" to className
                        ))

                        batch.commit()
                            .addOnSuccessListener {
                                setJoining(false, btnJoin)
                                Toast.makeText(this, "Matagumpay kang sumali sa $className", Toast.LENGTH_SHORT).show()
                                setResult(RESULT_OK)
                                finish()
                            }
                            .addOnFailureListener {
                                setJoining(false, btnJoin)
                                Toast.makeText(this, "Hindi na-save. Subukan muli.", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener {
                        setJoining(false, btnJoin)
                        Toast.makeText(this, "May problema sa koneksyon. Subukan muli.", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                setJoining(false, btnJoin)
                Toast.makeText(this, "May problema sa koneksyon. Subukan muli.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setJoining(isJoining: Boolean, btnJoin: MaterialButton) {
        btnJoin.isEnabled = !isJoining
        btnJoin.text = if (isJoining) "Sumasali..." else getString(R.string.join)
    }
}