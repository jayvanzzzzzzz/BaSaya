package ph.basaya.app.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import ph.basaya.app.R
import ph.basaya.app.data.auth.StudentOnly
import ph.basaya.app.data.model.User
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CompleteProfileActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "basaya_registration"
        const val KEY_PENDING_GOOGLE_UID = "pending_google_profile_uid"
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var tilFirstName: TextInputLayout
    private lateinit var tilLastName: TextInputLayout
    private lateinit var etFirstName: EditText
    private lateinit var etMiddleName: EditText
    private lateinit var etLastName: EditText
    private lateinit var etEmail: EditText
    private lateinit var btnComplete: MaterialButton
    private lateinit var progress: ProgressBar
    private var profileVerified = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_complete_profile)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        tilFirstName = findViewById(R.id.tilFirstName)
        tilLastName = findViewById(R.id.tilLastName)
        etFirstName = findViewById(R.id.etFirstName)
        etMiddleName = findViewById(R.id.etMiddleName)
        etLastName = findViewById(R.id.etLastName)
        etEmail = findViewById(R.id.etEmail)
        btnComplete = findViewById(R.id.btnCompleteProfile)
        progress = findViewById(R.id.progressCompleteProfile)

        val user = auth.currentUser
        if (user == null) {
            openLogin()
            return
        }

        val nameParts = (user.displayName ?: "").trim().split(Regex("\\s+")).filter(String::isNotBlank)
        etFirstName.setText(nameParts.firstOrNull().orEmpty())
        etLastName.setText(nameParts.drop(1).joinToString(" "))
        etEmail.setText(user.email.orEmpty())

        etFirstName.doAfterTextChanged { tilFirstName.error = null }
        etLastName.doAfterTextChanged { tilLastName.error = null }
        btnComplete.setOnClickListener { saveProfile() }
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { abandonProfileSetup() }
        findViewById<TextView>(R.id.tvSignOut).setOnClickListener { abandonProfileSetup() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = abandonProfileSetup()
        })

        verifyProfile(user.uid)
    }

    private fun verifyProfile(uid: String) {
        setLoading(true)
        db.collection("users").document(uid).get()
            .addOnSuccessListener { profile ->
                if (!profile.exists()) {
                    profileVerified = true
                    setLoading(false)
                } else if (StudentOnly.isStudent(profile)) {
                    openStudentApp(uid)
                } else {
                    Toast.makeText(this, R.string.complete_profile_wrong_account, Toast.LENGTH_LONG).show()
                    abandonProfileSetup()
                }
            }
            .addOnFailureListener { error ->
                Log.e("REGISTER_DEBUG", "Could not verify profile completion state", error)
                Toast.makeText(this, R.string.complete_profile_check_failed, Toast.LENGTH_LONG).show()
                abandonProfileSetup()
            }
    }

    private fun saveProfile() {
        if (!profileVerified) return

        val firstName = etFirstName.text.toString().trim()
        val middleName = etMiddleName.text.toString().trim()
        val lastName = etLastName.text.toString().trim()

        tilFirstName.error =
            if (firstName.isEmpty()) getString(R.string.register_error_first_name) else null
        tilLastName.error =
            if (lastName.isEmpty()) getString(R.string.register_error_last_name) else null

        val invalidField = listOf(
            tilFirstName to etFirstName,
            tilLastName to etLastName
        ).firstOrNull { (layout, _) -> layout.error != null }
        if (invalidField != null) {
            invalidField.second.requestFocus()
            return
        }

        val user = auth.currentUser
        if (user == null) {
            openLogin()
            return
        }

        setLoading(true)
        val profile = User(
            uid = user.uid,
            firstName = firstName,
            middleName = middleName,
            lastName = lastName,
            email = user.email.orEmpty(),
            role = "student",
            createdAt = System.currentTimeMillis()
        )
        db.collection("users").document(user.uid).set(profile)
            .addOnSuccessListener { openStudentApp(user.uid) }
            .addOnFailureListener { error ->
                Log.e("REGISTER_DEBUG", "Failed to save Google student profile", error)
                setLoading(false)
                Toast.makeText(this, R.string.register_save_failed, Toast.LENGTH_LONG).show()
            }
    }

    private fun setLoading(loading: Boolean) {
        btnComplete.isEnabled = !loading
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        btnComplete.text = getString(
            if (loading) R.string.complete_profile_saving else R.string.complete_profile_action
        )
    }

    private fun openStudentApp(uid: String) {
        clearPendingProfile()
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra("uid", uid)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    private fun abandonProfileSetup() {
        auth.signOut()
        clearPendingProfile()
        openLogin()
    }

    private fun clearPendingProfile() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .remove(KEY_PENDING_GOOGLE_UID)
            .apply()
    }

    private fun openLogin() {
        startActivity(
            Intent(this, LogInActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
