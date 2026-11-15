package ph.basaya.app.ui

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.TextView
import ph.basaya.app.ui.feedback.StudioToast as Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import ph.basaya.app.R
import ph.basaya.app.data.auth.AuthHelper
import ph.basaya.app.data.membership.ClassMembership
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Source
import ph.basaya.app.ui.pronunciation.PronunciationBookView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.FirebaseFirestore

class JoinClassActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var authHelper: AuthHelper

    private lateinit var buddy: PronunciationBookView
    private lateinit var btnJoin: TextView
    private lateinit var tilClassCode: TextInputLayout
    private var profileListener: ListenerRegistration? = null
    private var pending = false
    private var submitting = false

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

        btnJoin.setText(R.string.jc_request_send)
        findViewById<TextView>(R.id.tvInstruction).setText(R.string.jc_request_instruction)

        val submit = submit@{
            if (submitting) return@submit
            if (pending) { refreshStatus(); return@submit }
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

    override fun onStart() {
        super.onStart()
        val uid = authHelper.getCurrentUser()?.uid ?: return
        profileListener = db.collection("users").document(uid).addSnapshotListener { profile, error ->
            if (profile != null && error == null && !isFinishing) showMembership(profile)
        }
    }

    override fun onStop() {
        profileListener?.remove()
        profileListener = null
        super.onStop()
    }

    private fun showMembership(profile: DocumentSnapshot) {
        if (!profile.getString("classId").isNullOrBlank()) {
            setResult(RESULT_OK)
            Toast.makeText(this, R.string.jc_request_approved, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        pending = profile.getString("joinRequest.status") == "pending"
        val className = profile.getString("joinRequest.className").orEmpty()
        findViewById<View>(R.id.codeCard).visibility = if (pending) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.tvInstruction).text = when {
            pending -> getString(R.string.jc_request_pending, className)
            profile.getString("joinRequest.status") == "declined" -> getString(R.string.jc_request_declined, className)
            else -> getString(R.string.jc_request_instruction)
        }
        if (!submitting) btnJoin.setText(if (pending) R.string.jc_request_check else R.string.jc_request_send)
    }

    private fun refreshStatus() {
        val uid = authHelper.getCurrentUser()?.uid ?: return
        setJoining(true)
        db.collection("users").document(uid).get(Source.SERVER)
            .addOnSuccessListener { if (!isFinishing) { setJoining(false); showMembership(it) } }
            .addOnFailureListener { if (!isFinishing) { setJoining(false); Toast.makeText(this, R.string.jc_connection, Toast.LENGTH_SHORT).show() } }
    }

    private fun joinClass(code: String) {
        if (authHelper.getCurrentUser() == null) {
            Toast.makeText(this, R.string.jc_need_login, Toast.LENGTH_SHORT).show()
            return
        }
        setJoining(true)
        lifecycleScope.launch {
            try {
                val className = ClassMembership.requestJoin(code)
                pending = true
                findViewById<View>(R.id.codeCard).visibility = View.GONE
                findViewById<TextView>(R.id.tvInstruction).text = getString(R.string.jc_request_pending, className)
                buddy.setMood(PronunciationBookView.Mood.HAPPY)
                setResult(RESULT_OK)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showCodeError(error.message ?: getString(R.string.jc_connection))
            } finally {
                setJoining(false)
            }
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

    private fun setJoining(isJoining: Boolean) {
        submitting = isJoining
        btnJoin.isEnabled = !isJoining
        btnJoin.setText(if (isJoining) R.string.jc_request_sending else if (pending) R.string.jc_request_check else R.string.jc_request_send)
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
