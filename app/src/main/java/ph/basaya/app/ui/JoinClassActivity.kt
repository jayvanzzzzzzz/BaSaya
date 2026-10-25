package ph.basaya.app.ui

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import ph.basaya.app.R
import ph.basaya.app.data.auth.AuthHelper
import ph.basaya.app.data.chat.ClassChat
import ph.basaya.app.ui.game.ConfettiView
import ph.basaya.app.ui.pronunciation.PronunciationBookView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class JoinClassActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var authHelper: AuthHelper

    private lateinit var buddy: PronunciationBookView
    private lateinit var btnJoin: TextView
    private lateinit var tilClassCode: TextInputLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_join_class)

        db = FirebaseFirestore.getInstance()
        authHelper = AuthHelper()

        val header = findViewById<View>(R.id.header)
        btnJoin = findViewById(R.id.btnJoin)
        val joinBaseMargin = (btnJoin.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin

        // Violet header extends behind the status bar; the button stays above the nav bar.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.setPadding(header.paddingLeft, bars.top, header.paddingRight, header.paddingBottom)
            val bottom = maxOf(bars.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
            btnJoin.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = joinBaseMargin + bottom }
            insets
        }

        // White status bar icons on the violet header
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        buddy = findViewById(R.id.buddy)
        tilClassCode = findViewById(R.id.tilClassCode)
        val etClassCode = findViewById<TextInputEditText>(R.id.etClassCode)
        val tvFullName = findViewById<TextView>(R.id.tvFullName)
        val tvEmail = findViewById<TextView>(R.id.tvEmail)
        val tvAvatar = findViewById<TextView>(R.id.tvAvatar)

        findViewById<View>(R.id.btnClose).setOnClickListener { finish() }
        announceAsButton(btnJoin)

        // A friendly hello when the screen opens
        buddy.postDelayed({ buddy.setMood(PronunciationBookView.Mood.HAPPY) }, 400)

        loadSignedInAccount(tvFullName, tvEmail, tvAvatar)

        val submit = {
            val code = etClassCode.text?.toString()?.trim()?.uppercase().orEmpty()

            if (code.isEmpty()) {
                showCodeError(getString(R.string.jc_error_empty))
            } else {
                tilClassCode.error = null
                joinClass(code)
            }
        }

        btnJoin.setOnClickListener { pressBounce(btnJoin); submit() }
        etClassCode.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }
        // Clear the error as soon as the child starts fixing the code
        etClassCode.setOnKeyListener { _, _, _ ->
            tilClassCode.error = null
            false
        }
    }

    private fun loadSignedInAccount(tvFullName: TextView, tvEmail: TextView, tvAvatar: TextView) {
        val user = authHelper.getCurrentUser() ?: return
        tvEmail.text = user.email ?: ""

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                val firstName = doc.getString("firstName") ?: ""
                val lastName = doc.getString("lastName") ?: ""
                val fullName = "$firstName $lastName".trim()
                if (fullName.isNotEmpty()) tvFullName.text = fullName
                firstName.firstOrNull()?.let { tvAvatar.text = it.uppercase() }
            }
    }

    private fun joinClass(code: String) {
        val uid = authHelper.getCurrentUser()?.uid
        if (uid == null) {
            Toast.makeText(this, getString(R.string.jc_need_login), Toast.LENGTH_SHORT).show()
            return
        }

        setJoining(true)

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
                            setJoining(false)
                            showCodeError(getString(R.string.jc_error_not_found))
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
                        // "Si … ay sumali sa klase" in the class chat, in the same batch (the rules require it).
                        ClassChat.addJoinNotice(batch, classId, uid, fullName)

                        batch.commit()
                            .addOnSuccessListener {
                                celebrateAndClose(className)
                            }
                            .addOnFailureListener {
                                setJoining(false)
                                buddy.setMood(PronunciationBookView.Mood.CHEER)
                                Toast.makeText(this, getString(R.string.jc_save_failed), Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener {
                        setJoining(false)
                        buddy.setMood(PronunciationBookView.Mood.CHEER)
                        Toast.makeText(this, getString(R.string.jc_connection), Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                setJoining(false)
                buddy.setMood(PronunciationBookView.Mood.CHEER)
                Toast.makeText(this, getString(R.string.jc_connection), Toast.LENGTH_SHORT).show()
            }
    }

    /** Wrong or empty code: the box shakes and the buddy cheers the child on. */
    private fun showCodeError(message: String) {
        tilClassCode.error = message
        buddy.setMood(PronunciationBookView.Mood.CHEER)
        val d = resources.displayMetrics.density
        ObjectAnimator.ofFloat(tilClassCode, View.TRANSLATION_X, 0f, -12f * d, 12f * d, -8f * d, 8f * d, 0f).apply {
            duration = 400
            start()
        }
    }

    /** Joined: buddy jumps, confetti falls, then the screen closes. */
    private fun celebrateAndClose(className: String) {
        btnJoin.isEnabled = true
        btnJoin.isClickable = false
        btnJoin.text = getString(R.string.jc_success, className)
        buddy.setMood(PronunciationBookView.Mood.HAPPY)
        findViewById<ConfettiView>(R.id.confettiView).rain(120)
        Toast.makeText(this, getString(R.string.jc_success, className), Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        btnJoin.postDelayed({ if (!isFinishing) finish() }, 1600)
    }

    private fun setJoining(isJoining: Boolean) {
        btnJoin.isEnabled = !isJoining
        btnJoin.text = if (isJoining) getString(R.string.jc_joining) else getString(R.string.join)
        if (isJoining) buddy.setMood(PronunciationBookView.Mood.THINKING)
    }

    private fun pressBounce(view: View) {
        view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(70)
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(220)
                    .setInterpolator(android.view.animation.OvershootInterpolator(3f)).start()
            }
            .start()
    }

    private fun announceAsButton(view: View) {
        view.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Button::class.java.name
            }
        }
    }
}
