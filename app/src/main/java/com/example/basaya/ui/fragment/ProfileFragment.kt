package com.example.basaya.ui.fragment

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.basaya.R
import com.example.basaya.data.auth.AuthHelper
import com.example.basaya.ui.LogInActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileFragment : Fragment() {

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        val tvAvatarInitial = view.findViewById<TextView>(R.id.tvAvatarInitial)
        val tvProfileName = view.findViewById<TextView>(R.id.tvProfileName)
        val tvProfileEmail = view.findViewById<TextView>(R.id.tvProfileEmail)
        val tvProgressSummary = view.findViewById<TextView>(R.id.tvProgressSummary)
        val switchSound = view.findViewById<SwitchCompat>(R.id.switchSound)
        val rowLanguage = view.findViewById<LinearLayout>(R.id.rowLanguage)
        val tvLanguageValue = view.findViewById<TextView>(R.id.tvLanguageValue)
        val btnLogout = view.findViewById<MaterialButton>(R.id.btnLogout)
        val tvAppVersion = view.findViewById<TextView>(R.id.tvAppVersion)

        val prefs = requireContext().getSharedPreferences("basaya_prefs", Context.MODE_PRIVATE)

        loadUserInfo(tvAvatarInitial, tvProfileName, tvProfileEmail)
        loadProgressSummary(tvProgressSummary)
        setupSoundToggle(switchSound, prefs)
        setupAppVersion(tvAppVersion)

        // Language: static for now since only Filipino UI copy exists today
        rowLanguage.setOnClickListener {
            android.widget.Toast.makeText(
                requireContext(),
                "Filipino lang ang suportado sa ngayon.",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }

        btnLogout.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Mag-logout")
                .setMessage("Sigurado ka bang gusto mong mag-logout?")
                .setPositiveButton("Oo") { _, _ ->
                    FirebaseAuth.getInstance().signOut()
                    val intent = android.content.Intent(requireContext(), LogInActivity::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    requireActivity().finish()
                }
                .setNegativeButton("Hindi", null)
                .show()
        }

        return view
    }

    private fun loadUserInfo(
        tvAvatarInitial: TextView,
        tvProfileName: TextView,
        tvProfileEmail: TextView
    ) {
        val authHelper = AuthHelper()
        val uid = authHelper.getCurrentUser()?.uid ?: return
        val email = FirebaseAuth.getInstance().currentUser?.email ?: ""

        tvProfileEmail.text = email

        firestore.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->
                val firstName = doc.getString("firstName") ?: ""
                val lastName = doc.getString("lastName") ?: ""
                val fullName = "$firstName $lastName".trim()

                tvProfileName.text = if (fullName.isNotBlank()) fullName else "Estudyante"
                tvAvatarInitial.text = firstName.firstOrNull()?.uppercase() ?: "B"
            }
            .addOnFailureListener { e ->
                Log.e("ProfileFragment", "Failed to fetch user info", e)
                tvProfileName.text = "Estudyante"
            }
    }

    private fun loadProgressSummary(tvProgressSummary: TextView) {
        val uid = AuthHelper().getCurrentUser()?.uid ?: return

        lifecycleScope.launch {
            try {
                val snapshot = firestore
                    .collection("users")
                    .document(uid)
                    .collection("assignedLessons")
                    .get()
                    .await()

                val totalAssigned = snapshot.documents.size
                val totalFinished = snapshot.documents.count { doc ->
                    val lecture = doc.getBoolean("lectureFinished") ?: false
                    val game = doc.getBoolean("gameFinished") ?: false
                    val activity = doc.getBoolean("activityFinished") ?: false
                    val quiz = doc.getBoolean("quizFinished") ?: false
                    lecture && game && activity && quiz
                }

                tvProgressSummary.text = "$totalFinished sa $totalAssigned aralin ang natapos"
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Failed to load progress summary", e)
                tvProgressSummary.text = "Hindi ma-load ang progreso"
            }
        }
    }

    private fun setupSoundToggle(switchSound: SwitchCompat, prefs: SharedPreferences) {
        switchSound.isChecked = prefs.getBoolean("sound_enabled", true)

        switchSound.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("sound_enabled", isChecked).apply()
        }
    }

    private fun setupAppVersion(tvAppVersion: TextView) {
        try {
            val packageInfo = requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0)
            tvAppVersion.text = "BaSaya v${packageInfo.versionName}"
        } catch (e: Exception) {
            tvAppVersion.text = "BaSaya"
        }
    }
}