package com.example.basaya.ui.fragment

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.basaya.R
import com.example.basaya.adapter.LessonAdapter
import com.example.basaya.data.auth.AuthHelper
import com.example.basaya.data.entity.LessonEntity
import com.example.basaya.data.repository.LessonRepository
import com.example.basaya.ui.DashboardActivity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import com.google.android.material.button.MaterialButton


class HomeFragment : Fragment() {

    private var dotsAnimatorSet: AnimatorSet? = null
    private var refreshAnimator: ObjectAnimator? = null
    private var isRefreshing = false

    private var allLessons: List<LessonEntity> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // pulse animation for loading lessons
        val dot1 = view.findViewById<View>(R.id.dot1)
        val dot2 = view.findViewById<View>(R.id.dot2)
        val dot3 = view.findViewById<View>(R.id.dot3)

        startDotsAnimation(dot1, dot2, dot3)

        val authHelper = AuthHelper()

        val uid = authHelper.getCurrentUser()?.uid

        if (uid != null) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener { doc ->
                    val firstName = doc.getString("firstName") ?: ""
                    val lastName = doc.getString("lastName") ?: ""

                }
                .addOnFailureListener { e ->
                    Log.e("HomeFragment", "Failed to fetch user", e)
                }
        }

        val handleRemoveDownloadClick: (LessonEntity, (Boolean) -> Unit) -> Unit = { lesson, onResult ->
            lifecycleScope.launch {
                val repo = LessonRepository(requireContext())
                val success = repo.removeDownload(lesson.id)

                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        if (success) "Naalis ang download." else "Hindi na-alis ang download. Subukan muli.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                onResult(success)
            }
        }

        // Launches the actual download work; shared by both adapter instances below
        // so the pre-fetch adapter (empty list) and the post-fetch adapter stay in sync.
        val handleDownloadClick: (LessonEntity, (Boolean) -> Unit) -> Unit = { lesson, onResult ->
            lifecycleScope.launch {
                val repo = LessonRepository(requireContext())
                val success = repo.downloadLesson(lesson.id)

                if (isAdded && !success) {
                    Toast.makeText(
                        requireContext(),
                        "Hindi na-download. Siguraduhing may internet at subukan muli.",
                        Toast.LENGTH_SHORT
                    ).show()
                }else{
                    Toast.makeText(
                        requireContext(),
                        "Matagumpay na na-download ang aralin.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                onResult(success)
            }
        }

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerLessons)
        var adapter = LessonAdapter(
            emptyList(),
            onItemClick = { lesson ->
                openLessonIfAvailable(lesson)
            },
            onDownloadClick = handleDownloadClick,
            onRemoveDownloadClick = handleRemoveDownloadClick
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        val tvEmptyLessons = view.findViewById<TextView>(R.id.tvEmptyLessons)
        val loadingContainer = view.findViewById<LinearLayout>(R.id.loadingContainer)
        val tvLoadingText = view.findViewById<TextView>(R.id.tvLoadingText)
        val btnRefresh = view.findViewById<ImageButton>(R.id.btnRefresh)
        val etSearch = view.findViewById<EditText>(R.id.etSearch)

        // pulled the fetch logic into a local function so both initial load
        // and the refresh button can call the same code
        fun fetchLessons(isManualRefresh: Boolean) {
            if (uid == null) {
                Log.e("HomeFragment", "No logged-in user — cannot load lessons")
                return
            }
            if (isRefreshing) return
            isRefreshing = true

            if (isManualRefresh) {
                startRefreshSpin(btnRefresh)
            }

            lifecycleScope.launch {
                val repo = LessonRepository(requireContext())
                repo.syncLessons(uid)
                val lessons = repo.getLessons(uid)

                allLessons = lessons

                if (!isAdded) return@launch

                loadingContainer.visibility = View.GONE
                dotsAnimatorSet?.cancel()

                if (lessons.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    tvEmptyLessons.visibility = View.VISIBLE
                } else {
                    recyclerView.visibility = View.VISIBLE
                    tvEmptyLessons.visibility = View.GONE

                    adapter = LessonAdapter(
                        lessons,
                        onItemClick = { lesson ->
                            openLessonIfAvailable(lesson)
                        },
                        onDownloadClick = handleDownloadClick,
                        onRemoveDownloadClick = handleRemoveDownloadClick
                    )
                    recyclerView.adapter = adapter
                }

                if (isManualRefresh) {
                    stopRefreshSpin(btnRefresh)
                }
                isRefreshing = false
            }
        }

        btnRefresh.setOnClickListener {
            fetchLessons(isManualRefresh = true)
        }

        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s?.toString()?.trim().orEmpty()

                val filtered = if (query.isEmpty()) {
                    allLessons
                } else {
                    allLessons.filter { it.title.contains(query, ignoreCase = true) }
                }

                if (filtered.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    tvEmptyLessons.visibility = View.VISIBLE
                } else {
                    recyclerView.visibility = View.VISIBLE
                    tvEmptyLessons.visibility = View.GONE
                }

                adapter.updateList(filtered, query)
            }
        })

        //  guard added — uid must be non-null to call syncLessons/getLessons
        if (uid != null) {
            fetchLessons(isManualRefresh = false)
        } else {
            Log.e("HomeFragment", "No logged-in user — cannot load lessons")
        }

        return view
    }


    private fun startDotsAnimation(dot1: View, dot2: View, dot3: View) {
        val dots = listOf(dot1, dot2, dot3)
        val staggerDelay = 150L // ms between each dot starting

        val animators = dots.mapIndexed { index, dot ->
            createDotPulse(dot).apply {
                startDelay = index * staggerDelay
            }
        }

        dotsAnimatorSet = AnimatorSet().apply {
            playTogether(animators)
            start()
        }
    }

    private fun createDotPulse(dot: View): AnimatorSet {
        val scaleUpX = ObjectAnimator.ofFloat(dot, View.SCALE_X, 1f, 1.4f, 1f)
        val scaleUpY = ObjectAnimator.ofFloat(dot, View.SCALE_Y, 1f, 1.4f, 1f)
        val alpha = ObjectAnimator.ofFloat(dot, View.ALPHA, 0.4f, 1f, 0.4f)

        return AnimatorSet().apply {
            playTogether(scaleUpX, scaleUpY, alpha)
            duration = 900
            interpolator = AccelerateDecelerateInterpolator()
            // loop this specific dot's pulse forever
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (dot.isAttachedToWindow) {
                        animation.start()
                    }
                }
            })
        }
    }

    private fun startRefreshSpin(btnRefresh: ImageButton) {
        btnRefresh.isEnabled = false
        refreshAnimator?.cancel()
        refreshAnimator = ObjectAnimator.ofFloat(btnRefresh, View.ROTATION, 0f, 360f).apply {
            duration = 700
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun stopRefreshSpin(btnRefresh: ImageButton) {
        refreshAnimator?.let { anim ->
            val currentAngle = btnRefresh.rotation % 360f
            anim.cancel()
            ObjectAnimator.ofFloat(
                btnRefresh, View.ROTATION,
                currentAngle, currentAngle + (360f - currentAngle % 360f)
            ).apply {
                duration = 300
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }
        btnRefresh.isEnabled = true
    }

    private fun hasInternetConnection(): Boolean {
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE)
                    as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities =
            connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun openLessonIfAvailable(lesson: LessonEntity) {
        if (!isAdded || requireActivity().isFinishing) return

        if (lesson.isDownloaded || hasInternetConnection()) {
            startActivity(
                Intent(requireContext(), DashboardActivity::class.java).apply {
                    putExtra("LESSON_ID", lesson.id)
                }
            )
        } else {
            showOfflineLessonDialog(lesson)
        }
    }

    private fun showOfflineLessonDialog(lesson: LessonEntity) {
        val dialog = Dialog(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_offline_lesson, null)

        dialog.setContentView(dialogView)

        dialogView.findViewById<TextView>(R.id.tvOfflineMessage).text =
            "Hindi pa na-download ang “${lesson.title}”. Kumonekta sa internet at pindutin ang Download."

        dialogView.findViewById<MaterialButton>(R.id.btnOfflineOkay)
            .setOnClickListener { dialog.dismiss() }

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.88).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        dialog.show()

        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dotsAnimatorSet?.cancel()
        refreshAnimator?.cancel()
    }
}