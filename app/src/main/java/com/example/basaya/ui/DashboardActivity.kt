package com.example.basaya.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import android.widget.TextView
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.example.basaya.R
import com.example.basaya.data.cache.CompletionStateCache
import com.example.basaya.data.repository.LectureRepository
import com.example.basaya.ui.activity.PracticeActivity
import com.example.basaya.ui.pronunciation.PronunciationActivity
import com.example.basaya.ui.game.CrosswordActivity
import com.example.basaya.ui.quiz.QuizActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.basaya.data.cache.ContentCountsCache
import com.example.basaya.data.database.AppDatabase
import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.widget.ImageView
import com.google.android.material.button.MaterialButton

class DashboardActivity : AppCompatActivity() {

    private lateinit var gameCard: CardView
    private lateinit var lectureCard: CardView
    private lateinit var quizCard: CardView
    private lateinit var activityCard: CardView
    private lateinit var pronunciationCard: CardView
    private lateinit var quizLockOverlay: LinearLayout

    private var gameIsFinished = false
    private var activityIsFinished = false
    private var quizIsFinished = false
    private var lectureFinished = false
    private var pronunciationIsFinished = false

    private var hasGameScore = false
    private var hasActivityScore = false
    private var hasQuizScore = false
    private var hasPronunciationScore = false

    private lateinit var lectureRepository: LectureRepository
    private lateinit var lessonId: String

    private lateinit var tvLectureCount: TextView
    private lateinit var tvGameCount: TextView
    private lateinit var tvActivityCount: TextView
    private lateinit var tvQuizCount: TextView
    private lateinit var tvPronunciationCount: TextView

    private lateinit var tvLessonTitle: TextView
    private lateinit var tvLessonDescription: TextView

    private lateinit var lectureCompletedBadge: LinearLayout
    private lateinit var gameCompletedBadge: LinearLayout
    private lateinit var activityCompletedBadge: LinearLayout
    private lateinit var quizCompletedBadge: LinearLayout
    private lateinit var pronunciationCompletedBadge: LinearLayout

    private lateinit var countsCache: ContentCountsCache
    private lateinit var completionCache: CompletionStateCache

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val db by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dashboard)

        val statusBarBg = findViewById<View>(R.id.statusBarBg)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            statusBarBg.layoutParams.height = statusBarHeight
            statusBarBg.requestLayout()
            insets
        }

        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        lessonId = intent.getStringExtra("LESSON_ID") ?: ""

        gameCard = findViewById(R.id.gameCard)
        lectureCard = findViewById(R.id.lectureCard)
        quizCard = findViewById(R.id.quizCard)
        activityCard = findViewById(R.id.activityCard)
        pronunciationCard = findViewById(R.id.pronunciationCard)
        quizLockOverlay = findViewById(R.id.quizLockOverlay)

        lectureCompletedBadge = findViewById(R.id.lectureCompletedBadge)
        gameCompletedBadge = findViewById(R.id.gameCompletedBadge)
        activityCompletedBadge = findViewById(R.id.activityCompletedBadge)
        quizCompletedBadge = findViewById(R.id.quizCompletedBadge)
        pronunciationCompletedBadge = findViewById(R.id.pronunciationCompletedBadge)

        tvLectureCount = findViewById(R.id.tvLectureCount)
        tvGameCount = findViewById(R.id.tvGameCount)
        tvActivityCount = findViewById(R.id.tvActivityCount)
        tvQuizCount = findViewById(R.id.tvQuizCount)
        tvPronunciationCount = findViewById(R.id.tvPronunciationCount)

        lectureRepository = LectureRepository(this)

        countsCache = ContentCountsCache(this)
        completionCache = CompletionStateCache(this)

        loadContentCounts()

        tvLessonTitle = findViewById(R.id.tvLessonTitle)
        tvLessonDescription = findViewById(R.id.tvLessonDesc)

        lifecycleScope.launch {
            try {
                val lessonDoc = firestore
                    .collection("lessons")
                    .document(lessonId)
                    .get()
                    .await()

                tvLessonTitle.text = lessonDoc.getString("title") ?: "BaSaya"
                tvLessonDescription.text = lessonDoc.getString("description") ?: ""

            } catch (e: Exception) {
                tvLessonTitle.text = "BaSaya"
                tvLessonDescription.text = ""
            }
        }

        gameCard.setOnClickListener {
            if (gameIsFinished) {
                showLessonDialog(
                    iconRes = R.drawable.ic_check_completed,
                    title = "Tapos na",
                    message = "Nakumpleto mo na ang larong ito. Hindi na ito maaaring ulitin."
                )
            } else {
                openContentIfAvailable(CrosswordActivity::class.java)
            }
        }

        lectureCard.setOnClickListener {
            if (lectureFinished) {
                showLessonDialog(
                    iconRes = R.drawable.ic_check_completed,
                    title = "Tapos na",
                    message = "Natapos mo na ang leksyon na ito. Maaari mo pa rin itong basahin muli anumang oras.",
                    primaryText = "Basahin muli",
                    onPrimary = {
                        openContentIfAvailable(LectureActivity::class.java)
                    },
                    secondaryText = "Isara"
                )
            } else {
                openContentIfAvailable(LectureActivity::class.java)
            }
        }

        activityCard.setOnClickListener {
            if (activityIsFinished) {
                showLessonDialog(
                    iconRes = R.drawable.ic_check_completed,
                    title = "Tapos na",
                    message = "Nakumpleto mo na ang gawaing ito. Hindi na ito maaaring ulitin."
                )
            } else {
                openContentIfAvailable(PracticeActivity::class.java)
            }
        }

        quizCard.setOnClickListener {
            if (quizIsFinished) {
                showLessonDialog(
                    iconRes = R.drawable.ic_check_completed,
                    title = "Tapos na",
                    message = "Natapos mo na ang pagsusulit. Hindi na ito maaaring ulitin."
                )
            } else {
                openContentIfAvailable(QuizActivity::class.java)
            }
        }

        pronunciationCard.setOnClickListener {
            if (pronunciationIsFinished) {
                showLessonDialog(
                    iconRes = R.drawable.ic_check_completed,
                    title = "Tapos na",
                    message = "Nakumpleto mo na ang bigkas na ito. Hindi na ito maaaring ulitin."
                )
            } else {
                openContentIfAvailable(PronunciationActivity::class.java)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        checkQuizUnlockState()
        loadCompletionState()
    }

    private fun checkQuizUnlockState() {
        if (lessonId.isBlank()) return

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        lifecycleScope.launch {
            val isFinished = lectureRepository.checkCompletion(uid, lessonId)

            if (isFinished) {
                quizCard.isClickable = true
                quizLockOverlay.visibility = View.GONE
            } else {
                quizCard.isClickable = false
                quizLockOverlay.visibility = View.VISIBLE
            }
        }
    }

    private fun loadCompletionState() {
        if (lessonId.isBlank()) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        completionCache.get(lessonId)?.let { cached ->
            lectureFinished = cached.lectureFinished
            lectureCompletedBadge.visibility =
                if (cached.lectureFinished) View.VISIBLE else View.GONE

            gameIsFinished = cached.gameFinished
            if (cached.gameTotal > 0) {
                tvGameCount.text = "${cached.gameScore}/${cached.gameTotal} levels"
                hasGameScore = true
            }
            gameCompletedBadge.visibility = if (cached.gameFinished) View.VISIBLE else View.GONE
            gameCard.alpha = if (cached.gameFinished) 0.85f else 1f

            activityIsFinished = cached.activityFinished
            if (cached.activityScore != null && cached.activityTotal != null) {
                tvActivityCount.text = "Pahina • ${cached.activityScore}/${cached.activityTotal}"
                hasActivityScore = true
            }
            activityCompletedBadge.visibility = if (cached.activityFinished) View.VISIBLE else View.GONE
            activityCard.alpha = if (cached.activityFinished) 0.85f else 1f

            quizIsFinished = cached.quizFinished
            if (cached.quizScore != null && cached.quizTotal != null) {
                tvQuizCount.text = "Puntos • ${cached.quizScore}/${cached.quizTotal}"
                hasQuizScore = true
            }
            quizCompletedBadge.visibility = if (cached.quizFinished) View.VISIBLE else View.GONE
            quizCard.alpha = if (cached.quizFinished) 0.85f else 1f
            if (cached.quizFinished) quizLockOverlay.visibility = View.GONE

            pronunciationIsFinished = cached.pronunciationFinished
            if (cached.pronunciationScore != null && cached.pronunciationTotal != null) {
                tvPronunciationCount.text = "Salita • ${cached.pronunciationScore}/${cached.pronunciationTotal}"
                hasPronunciationScore = true
            }
            pronunciationCompletedBadge.visibility = if (cached.pronunciationFinished) View.VISIBLE else View.GONE
            pronunciationCard.alpha = if (cached.pronunciationFinished) 0.85f else 1f
        }

        lifecycleScope.launch {
            try {
                val doc = firestore
                    .collection("users")
                    .document(uid)
                    .collection("assignedLessons")
                    .document(lessonId)
                    .get()
                    .await()

                // Lecture check only not close
                lectureFinished = doc.getBoolean("lectureFinished") ?: false
                lectureCompletedBadge.visibility =
                    if (lectureFinished) View.VISIBLE else View.GONE

                val gameFinished = doc.getBoolean("gameFinished") ?: false
                gameIsFinished = gameFinished

                val gameScore = doc.getLong("gameScore") ?: 0
                val gameTotal = doc.getLong("gameTotal") ?: 0

                if (gameTotal > 0) {
                    tvGameCount.text = "$gameScore/$gameTotal levels"
                    hasGameScore = true
                }

                if (gameFinished) {
                    gameCompletedBadge.visibility = View.VISIBLE
                    gameCard.alpha = 0.85f
                } else {
                    gameCompletedBadge.visibility = View.GONE
                    gameCard.alpha = 1f
                }

                // Activity score shows as soon as it exists; lock only depends on finished
                val activityFinished = doc.getBoolean("activityFinished") ?: false
                activityIsFinished = activityFinished

                val activityScore = doc.getLong("activityScore")
                val activityTotal = doc.getLong("activityTotal")

                if (activityScore != null && activityTotal != null) {
                    tvActivityCount.text = "Pahina • $activityScore/$activityTotal"
                    hasActivityScore = true
                }

                if (activityFinished) {
                    activityCompletedBadge.visibility = View.VISIBLE
                    activityCard.alpha = 0.85f
                } else {
                    activityCompletedBadge.visibility = View.GONE
                    activityCard.alpha = 1f
                }

                // Quiz score shows as soon as it exists; lock only depends on finished
                val quizFinished = doc.getBoolean("quizFinished") ?: false
                quizIsFinished = quizFinished

                val quizScore = doc.getLong("quizScore")
                val quizTotal = doc.getLong("quizTotal")

                if (quizScore != null && quizTotal != null) {
                    tvQuizCount.text = "Puntos • $quizScore/$quizTotal"
                    hasQuizScore = true
                }

                if (quizFinished) {
                    quizCompletedBadge.visibility = View.VISIBLE
                    quizCard.alpha = 0.85f
                    quizLockOverlay.visibility = View.GONE
                } else {
                    quizCompletedBadge.visibility = View.GONE
                    quizCard.alpha = 1f
                }

                // score shows as soon as it exists; lock only depends on finished
                val pronunciationFinished = doc.getBoolean("pronunciationFinished") ?: false
                pronunciationIsFinished = pronunciationFinished

                val pronunciationScore = doc.getLong("pronunciationScore")
                val pronunciationTotal = doc.getLong("pronunciationTotal")

                if (pronunciationScore != null && pronunciationTotal != null) {
                    tvPronunciationCount.text = "Salita • $pronunciationScore/$pronunciationTotal"
                    hasPronunciationScore = true
                }

                if (pronunciationFinished) {
                    pronunciationCompletedBadge.visibility = View.VISIBLE
                    pronunciationCard.alpha = 0.85f
                } else {
                    pronunciationCompletedBadge.visibility = View.GONE
                    pronunciationCard.alpha = 1f
                }

                completionCache.save(
                    lessonId,
                    CompletionStateCache.State(
                        lectureFinished = lectureFinished,
                        gameFinished = gameIsFinished,
                        gameScore = gameScore,
                        gameTotal = gameTotal,
                        activityFinished = activityIsFinished,
                        activityScore = activityScore,
                        activityTotal = activityTotal,
                        quizFinished = quizIsFinished,
                        quizScore = quizScore,
                        quizTotal = quizTotal,
                        pronunciationFinished = pronunciationIsFinished,
                        pronunciationScore = pronunciationScore,
                        pronunciationTotal = pronunciationTotal
                    )
                )

            } catch (e: Exception) {
                Log.e("DashboardActivity", "Failed to load completion state", e)
            }
        }
    }

    private fun loadContentCounts() {
        if (lessonId.isBlank()) return

        countsCache.get(lessonId)?.let { cached ->
            tvLectureCount.text = "${cached.lecturePages} pages"
            if (!hasGameScore) tvGameCount.text = "0/${cached.gameLevels} levels"
            if (!hasActivityScore) tvActivityCount.text = "0/${cached.activityPages} pages"
            if (!hasQuizScore) tvQuizCount.text = "0/${cached.quizQuestions} questions"
            if (!hasPronunciationScore) tvPronunciationCount.text = "0/${cached.pronunciationWordCount} salita"
        }

        lifecycleScope.launch {
            try {
                val lectureSnapshot = firestore
                    .collection("lessons")
                    .document(lessonId)
                    .collection("lectures")
                    .limit(1)
                    .get()
                    .await()

                val lectureDoc = lectureSnapshot.documents.first()

                val gameSnapshot = firestore
                    .collection("lessons")
                    .document(lessonId)
                    .collection("game")
                    .get()
                    .await()

                val activitySnapshot = firestore
                    .collection("lessons")
                    .document(lessonId)
                    .collection("activity")
                    .limit(1)
                    .get()
                    .await()

                val activityDoc = activitySnapshot.documents.first()

                val quizSnapshot = firestore
                    .collection("lessons")
                    .document(lessonId)
                    .collection("quiz")
                    .limit(1)
                    .get()
                    .await()

                val quizDoc = quizSnapshot.documents.first()

                val pronunciationSnapshot = firestore
                    .collection("lessons")
                    .document(lessonId)
                    .collection("pronunciation")
                    .limit(1)
                    .get()
                    .await()

                val pronunciationDoc = pronunciationSnapshot.documents.first()

                val lecturePages = (lectureDoc.get("pages") as? List<*>)?.size ?: 0
                val gameLevels = gameSnapshot.documents.size
                val activityPages = (activityDoc.get("pages") as? List<*>)?.size ?: 0
                val quizQuestions = (quizDoc.get("questions") as? List<*>)?.size ?: 0
                val pronunciationWordCount = (pronunciationDoc.get("words") as? List<*>)?.size ?: 0

                tvLectureCount.text = "$lecturePages pages"

                if (!hasGameScore) {
                    tvGameCount.text = "0/$gameLevels levels"
                }
                if (!hasActivityScore) {
                    tvActivityCount.text = "0/$activityPages pages"
                }
                if (!hasQuizScore) {
                    tvQuizCount.text = "0/$quizQuestions questions"
                }
                if (!hasPronunciationScore) {
                    tvPronunciationCount.text = "0/$pronunciationWordCount salita"
                }

                // Successful fetch — refresh the cache for next time we're offline
                countsCache.save(
                    lessonId,
                    ContentCountsCache.Counts(
                        lecturePages = lecturePages,
                        gameLevels = gameLevels,
                        activityPages = activityPages,
                        quizQuestions = quizQuestions,
                        pronunciationWordCount = pronunciationWordCount
                    )
                )

            } catch (e: Exception) {
                if (countsCache.get(lessonId) == null) {
                    tvLectureCount.text = "0 pages"
                    if (!hasGameScore) tvGameCount.text = "0/0 levels"
                    if (!hasActivityScore) tvActivityCount.text = "0/0 pages"
                    if (!hasQuizScore) tvQuizCount.text = "0/0 questions"
                    if (!hasPronunciationScore) tvPronunciationCount.text = "0/0 salita"
                }
            }
        }
    }

    private fun hasInternetConnection(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities =
            connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun openContentIfAvailable(destination: Class<out Activity>) {
        lifecycleScope.launch {
            val isDownloaded = db.lessonDao()
                .isLessonDownloaded(lessonId) ?: false

            if (isDownloaded || hasInternetConnection()) {
                startActivity(
                    Intent(this@DashboardActivity, destination).apply {
                        putExtra("LESSON_ID", lessonId)
                    }
                )
            } else {
                showLessonDialog(
                    iconRes = R.drawable.ic_download_modern,
                    title = "Hindi available offline",
                    message = "Walang internet at hindi pa na-download ang aralin na ito.",
                    primaryText = "OK"
                )
            }
        }
    }

    private fun showLessonDialog(
        iconRes: Int,
        title: String,
        message: String,
        primaryText: String = "OK",
        onPrimary: (() -> Unit)? = null,
        secondaryText: String? = null,
        onSecondary: (() -> Unit)? = null
    ) {
        val dialog = Dialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_lesson_status, null)

        dialogView.findViewById<ImageView>(R.id.ivDialogIcon)
            .setImageResource(iconRes)

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = title
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = message

        dialogView.findViewById<MaterialButton>(R.id.btnDialogPrimary).apply {
            text = primaryText
            setOnClickListener {
                dialog.dismiss()
                onPrimary?.invoke()
            }
        }

        dialogView.findViewById<TextView>(R.id.btnDialogSecondary).apply {
            if (secondaryText != null) {
                visibility = View.VISIBLE
                text = secondaryText
                setOnClickListener {
                    dialog.dismiss()
                    onSecondary?.invoke()
                }
            }
        }

        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()

        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

}