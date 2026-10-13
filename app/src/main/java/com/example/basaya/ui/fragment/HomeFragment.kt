package com.example.basaya.ui.fragment

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
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
import com.example.basaya.data.notifications.ClassNotifications
import com.example.basaya.data.repository.LessonRepository
import com.example.basaya.data.schedule.LessonSchedule
import com.example.basaya.data.schedule.LessonSchedule.Availability
import com.example.basaya.ui.DashboardActivity
import android.widget.ImageView
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import com.example.basaya.ui.JoinClassActivity
import com.google.android.material.button.MaterialButton


class HomeFragment : Fragment() {

    private var dotsAnimatorSet: AnimatorSet? = null
    private var refreshAnimator: ObjectAnimator? = null
    private var isRefreshing = false

    private var allLessons: List<LessonEntity> = emptyList()

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmptyLessons: TextView
    private lateinit var emptyStateScroll: View
    private lateinit var loadingContainer: LinearLayout
    private lateinit var etSearch: EditText
    private lateinit var btnRefresh: ImageButton
    private lateinit var tvClassName: TextView

    private val authHelper = AuthHelper()
    private var currentClassId: String? = null

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    // The three distinct reasons the lesson list can be empty — each maps
    // to its own icon, tint color, and message via showEmptyState() below.
    private enum class EmptyLessonsState {
        NOT_IN_CLASS,
        NO_LESSONS_IN_CLASS
    }

    private val joinClassLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                checkClassMembershipAndLoad()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_home, container, false)

        val dot1 = view.findViewById<View>(R.id.dot1)
        val dot2 = view.findViewById<View>(R.id.dot2)
        val dot3 = view.findViewById<View>(R.id.dot3)

        startDotsAnimation(dot1, dot2, dot3)

        val btnJoinClass = view.findViewById<ImageButton>(R.id.btnJoinClass)
        btnJoinClass.setOnClickListener {
            if(hasInternetConnection())showJoinClassSheet()
            else Toast.makeText(requireContext(),
                getString(R.string.internet_connection_required),
                Toast.LENGTH_SHORT).show()
        }

        recyclerView = view.findViewById(R.id.recyclerLessons)
        tvEmptyLessons = view.findViewById(R.id.tvEmptyLessons)
        emptyStateScroll = view.findViewById(R.id.emptyStateScroll)
        loadingContainer = view.findViewById(R.id.loadingContainer)
        etSearch = view.findViewById(R.id.etSearch)
        btnRefresh = view.findViewById(R.id.btnRefresh)
        tvClassName = view.findViewById(R.id.tvClassName)

        var adapter = LessonAdapter(
            emptyList(),
            onItemClick = { lesson -> openLessonIfAvailable(lesson) },
            onDownloadClick = { lesson, onResult -> handleDownload(lesson, onResult) },
            onRemoveDownloadClick = { lesson, onResult -> handleRemoveDownload(lesson, onResult) }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        this.adapter = adapter

        btnRefresh.setOnClickListener {
            if (currentClassId == null) {
                Toast.makeText(requireContext(), getString(R.string.toast_join_class_first), Toast.LENGTH_SHORT).show()
            } else {
                fetchLessons(isManualRefresh = true)
            }
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

                    // Only shows "no results" if there WERE lessons to search through —
                    // otherwise the class-empty state from fetchLessons() already
                    // covers it and shouldn't be overwritten.
                    if (allLessons.isNotEmpty()) {
                        tvEmptyLessons.text = getString(R.string.empty_no_search_results, query)

                        val icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_empty_search)
                        icon?.setBounds(0, 0, icon.intrinsicWidth, icon.intrinsicHeight)
                        tvEmptyLessons.setCompoundDrawables(null, icon, null, null)

                        setEmptyVisible(true)
                    }
                } else {
                    recyclerView.visibility = View.VISIBLE
                    setEmptyVisible(false)
                }

                this@HomeFragment.adapter.updateList(filtered, query)
            }
        })

        checkClassMembershipAndLoad()

        return view
    }

    private lateinit var adapter: LessonAdapter

    /** The empty message and its scroll area are always shown and hidden together. */
    private fun setEmptyVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        tvEmptyLessons.visibility = visibility
        emptyStateScroll.visibility = visibility
    }

    private fun showEmptyState(state: EmptyLessonsState) {
        recyclerView.visibility = View.GONE
        setEmptyVisible(true)

        val (iconRes, message) = when (state) {
            EmptyLessonsState.NOT_IN_CLASS -> Pair(
                R.drawable.ic_empty_join_class,
                getString(R.string.empty_not_in_class)
            )
            EmptyLessonsState.NO_LESSONS_IN_CLASS -> Pair(
                R.drawable.ic_empty_lessons,
                getString(R.string.empty_no_lessons)
            )
        }

        tvEmptyLessons.text = message

        val icon = ContextCompat.getDrawable(requireContext(), iconRes)
        icon?.setBounds(0, 0, icon.intrinsicWidth, icon.intrinsicHeight)
        tvEmptyLessons.setCompoundDrawables(null, icon, null, null)
    }

    private fun checkClassMembershipAndLoad() {
        val uid = authHelper.getCurrentUser()?.uid
        if (uid == null) {
            Log.e("HomeFragment", "No logged-in user — cannot check class membership")
            return
        }

        loadingContainer.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        setEmptyVisible(false)

        firestore
            .collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (!isAdded) return@addOnSuccessListener

                val classId = doc.getString("classId")
                val className = doc.getString("className")
                currentClassId = classId
                // "New lesson" pushes come from this class only.
                ClassNotifications.syncTopic(requireContext(), classId)

                if (classId.isNullOrBlank()) {
                    tvClassName.text = ""
                    tvClassName.visibility = View.GONE
                    loadingContainer.visibility = View.GONE
                    dotsAnimatorSet?.cancel()
                    showEmptyState(EmptyLessonsState.NOT_IN_CLASS)
                } else {
                    tvClassName.text = className.orEmpty()
                    tvClassName.visibility =
                        if (className.isNullOrBlank()) View.GONE else View.VISIBLE
                    fetchLessons(isManualRefresh = false)
                }
            }
            .addOnFailureListener { e ->
                Log.e("HomeFragment", "Failed to check class membership", e)
                if (!isAdded) return@addOnFailureListener
                loadingContainer.visibility = View.GONE
                tvClassName.visibility = View.GONE
                dotsAnimatorSet?.cancel()
                tvEmptyLessons.text = getString(R.string.error_checking_class_membership)
                setEmptyVisible(true)
                recyclerView.visibility = View.GONE
            }
    }

    private fun handleDownload(lesson: LessonEntity, onResult: (Boolean) -> Unit) {
        if (!LessonSchedule.canOpen(requireContext(), lesson)) {
            Toast.makeText(requireContext(), getString(R.string.schedule_download_locked), Toast.LENGTH_SHORT).show()
            onResult(false)
            return
        }
        lifecycleScope.launch {
            val repo = LessonRepository(requireContext())
            val success = repo.downloadLesson(lesson.id)

            if (isAdded) {
                Toast.makeText(
                    requireContext(),
                    if (success) getString(R.string.toast_download_success)
                    else getString(R.string.toast_download_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
            onResult(success)
        }
    }

    private fun handleRemoveDownload(lesson: LessonEntity, onResult: (Boolean) -> Unit) {
        lifecycleScope.launch {
            val repo = LessonRepository(requireContext())
            val success = repo.removeDownload(lesson.id)

            if (isAdded) {
                Toast.makeText(
                    requireContext(),
                    if (success) getString(R.string.toast_remove_download_success)
                    else getString(R.string.toast_remove_download_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
            onResult(success)
        }
    }

    private fun fetchLessons(isManualRefresh: Boolean) {
        val uid = authHelper.getCurrentUser()?.uid
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
                showEmptyState(EmptyLessonsState.NO_LESSONS_IN_CLASS)
            } else {
                recyclerView.visibility = View.VISIBLE
                setEmptyVisible(false)
                adapter.updateList(lessons, "")
                applyHighlight()
            }

            if (isManualRefresh) {
                stopRefreshSpin(btnRefresh)
            }
            isRefreshing = false
        }
    }

    private fun startDotsAnimation(dot1: View, dot2: View, dot3: View) {
        val dots = listOf(dot1, dot2, dot3)
        val staggerDelay = 150L

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

        if (!LessonSchedule.canOpen(requireContext(), lesson)) {
            showScheduleLockedDialog(lesson)
            return
        }

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
            getString(R.string.offline_lesson_message, lesson.title)

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
    }

    /** Before the open date, or after the deadline of an unfinished lesson. */
    private fun showScheduleLockedDialog(lesson: LessonEntity) {
        val opens = LessonSchedule.format(lesson.openAt)
        val (title, message) = when (LessonSchedule.availability(requireContext(), lesson)) {
            Availability.NEEDS_INTERNET ->
                getString(R.string.schedule_not_open_title) to
                    getString(R.string.schedule_needs_internet_msg, lesson.title, opens)
            Availability.CLOSED ->
                getString(R.string.schedule_closed_title) to
                    getString(R.string.schedule_closed_msg, lesson.title, LessonSchedule.format(lesson.dueAt))
            else ->
                getString(R.string.schedule_not_open_title) to
                    getString(R.string.schedule_not_open_msg, lesson.title, opens)
        }

        val dialog = Dialog(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_lesson_status, null)
        dialogView.findViewById<ImageView>(R.id.ivDialogIcon).setImageResource(R.drawable.ic_lock_small)
        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = title
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = message
        dialogView.findViewById<MaterialButton>(R.id.btnDialogPrimary).apply {
            text = "OK"
            setOnClickListener { dialog.dismiss() }
        }

        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // Lessons to highlight once the list is loaded (from a tapped notification).
    private var pendingHighlight: List<String>? = null

    /** From a tapped notification: scroll to the lesson(s) and make them glow for a moment. */
    fun highlightLessons(lessonIds: List<String>) {
        pendingHighlight = lessonIds
        if (view == null) return // Applied after the first load.
        if (allLessons.none { it.id in lessonIds }) {
            // A brand-new lesson that isn't in the list yet: refresh first.
            if (currentClassId != null) fetchLessons(isManualRefresh = false)
        } else {
            applyHighlight()
        }
    }

    private fun applyHighlight() {
        val ids = pendingHighlight ?: return
        val position = allLessons.indexOfFirst { it.id in ids }
        if (position < 0) return
        pendingHighlight = null

        // The lesson may be hidden by a search.
        if (etSearch.text.isNotEmpty()) etSearch.setText("")
        val offset = (24 * resources.displayMetrics.density).toInt()
        (recyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, offset)
        adapter.highlight(ids)
    }

    override fun onResume() {
        super.onResume()
        // Re-check open dates and deadlines that may have passed while away.
        if (::adapter.isInitialized) adapter.notifyDataSetChanged()
    }

    private fun showJoinClassSheet() {
        if (!isAdded) return

        val sheet = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_join_class, null)
        sheet.setContentView(sheetView)

        sheetView.findViewById<View>(R.id.btnEnterClass).setOnClickListener {
            sheet.dismiss()
            joinClassLauncher.launch(Intent(requireContext(), JoinClassActivity::class.java))
        }

        sheet.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dotsAnimatorSet?.cancel()
        refreshAnimator?.cancel()
    }
}