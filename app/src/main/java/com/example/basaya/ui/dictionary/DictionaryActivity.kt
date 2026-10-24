package com.example.basaya.ui.dictionary

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.basaya.R
import com.example.basaya.data.dictionary.Dictionary
import com.example.basaya.data.dictionary.DictionaryAudio
import com.example.basaya.data.dictionary.Syllables
import com.example.basaya.ui.pronunciation.PronunciationBookView
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Diksiyonaryo: Tagalog words explained in Filipino (data/dictionary/Dictionary.kt). Search
 * as you type, tap a word for its full meaning, tap a synonym to jump to it. The same as
 * the website's student/views/dictionary.js.
 */
class DictionaryActivity : AppCompatActivity() {

    companion object {
        private val LETTERS = "a b k d e g h i l m n ng o p r s t u w y".split(" ")
        private const val SEARCH_DELAY_MS = 250L
        /** The speaker's waves rolling while a word plays: none, the small one, both. */
        private val SPEAKER_WAVES = intArrayOf(R.drawable.ic_dict_speaker_0, R.drawable.ic_dict_speaker_1, R.drawable.ic_speaker_on)
        private const val WAVE_FRAME_MS = 220L

        fun intent(context: Context): Intent = Intent(context, DictionaryActivity::class.java)
    }

    private lateinit var etSearch: EditText
    private lateinit var recycler: RecyclerView
    private lateinit var detailScroll: NestedScrollView
    private lateinit var detailCard: LinearLayout
    private lateinit var emptyState: View
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptyBody: TextView
    private lateinit var progress: ProgressBar
    private lateinit var buddy: PronunciationBookView
    private lateinit var appBar: AppBarLayout
    private val adapter = WordAdapter(onClick = { showEntry(it) }, onPageChange = { recycler.scrollToPosition(0) })

    /** The running search; a newer one cancels it so an old answer never shows. */
    private var job: Job? = null
    private var showingEntry = false
    private val audio by lazy { DictionaryAudio(this) }
    private var speakJob: Job? = null
    private var speakerButton: ImageButton? = null
    private var wavesJob: Job? = null
    private var breathing: ObjectAnimator? = null
    /** "Lahat" ("") and the letters, to mark the one matching the search box. */
    private val letterChips = mutableListOf<Pair<String, TextView>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dictionary)

        val header = findViewById<View>(R.id.header)
        val statusStrip = findViewById<View>(R.id.statusStrip)
        val credit = findViewById<View>(R.id.tvCredit)
        val creditBottom = credit.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            header.updatePadding(top = bars.top)
            // The header stops scrolling one status bar short, so the pinned search stays below it.
            header.minimumHeight = bars.top
            statusStrip.updateLayoutParams { height = bars.top }
            credit.updatePadding(bottom = creditBottom + bars.bottom)
            insets
        }
        // Fade the strip in over the first status bar's worth of scroll, hiding the header under it.
        appBar = findViewById(R.id.appBar)
        appBar.addOnOffsetChangedListener { _, offset ->
            statusStrip.alpha = if (statusStrip.height == 0) 0f else (-offset / statusStrip.height.toFloat()).coerceIn(0f, 1f)
        }
        // White status bar icons on the violet header
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        etSearch = findViewById(R.id.etSearch)
        recycler = findViewById(R.id.recyclerWords)
        detailScroll = findViewById(R.id.detailScroll)
        detailCard = findViewById(R.id.detailCard)
        emptyState = findViewById(R.id.emptyState)
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle)
        tvEmptyBody = findViewById(R.id.tvEmptyBody)
        progress = findViewById(R.id.progress)
        buddy = findViewById(R.id.buddy)
        buddy.setMood(PronunciationBookView.Mood.HAPPY)
        findViewById<View>(R.id.btnBack).setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        // Back from a word goes to the list first, then closes the screen.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (showingEntry) showList() else finish()
            }
        })

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (etSearch.tag == true) return // set from code (letter, synonym)
                search(s?.toString().orEmpty(), SEARCH_DELAY_MS)
            }
        })
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
            hideKeyboard()
            search(etSearch.text.toString(), 0)
            true
        }

        buildLetters()
        showAll()
    }

    // ---------- loading ----------

    private fun setQuery(text: String) {
        etSearch.tag = true
        etSearch.setText(text)
        etSearch.setSelection(text.length)
        etSearch.tag = null
    }

    private fun run(delayMs: Long = 0, block: suspend () -> Unit) {
        job?.cancel()
        job = lifecycleScope.launch {
            if (delayMs > 0) delay(delayMs)
            showLoading()
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(getString(R.string.dict_error_title), getString(R.string.dict_error_body))
            }
        }
    }

    private fun search(text: String, delayMs: Long) {
        val query = text.trim()
        markLetter(query)
        if (query.isEmpty()) {
            showAll()
            return
        }
        run(delayMs) {
            val found = Dictionary.search(this, query)
            if (found.isNotEmpty()) {
                showList(found, null, paged = true)
                return@run
            }
            val roots = Dictionary.suggestRoots(this, query)
            if (roots.isNotEmpty()) showList(roots, getString(R.string.dict_root_hint, query))
            else showMessage(getString(R.string.dict_empty_title), getString(R.string.dict_empty_body, query))
        }
    }

    /** Before typing (and on "Lahat"): every word, A to Z, with the page nav. */
    private fun showAll() {
        run { showList(Dictionary.allWords(this), getString(R.string.dict_all_words), paged = true) }
    }

    private fun openWord(word: String) {
        setQuery(word)
        markLetter(word)
        run {
            val entry = Dictionary.lookup(this, word)
            if (entry != null) showEntry(entry) else search(word, 0)
        }
    }

    // ---------- showing ----------

    private fun showOnly(view: View?) {
        listOf(recycler, detailScroll, emptyState, progress).forEach {
            it.visibility = if (it === view) View.VISIBLE else View.GONE
        }
    }

    private fun showLoading() = showOnly(progress)

    private fun showMessage(title: String, body: String) {
        showingEntry = false
        tvEmptyTitle.text = title
        tvEmptyBody.text = body
        showOnly(emptyState)
        // The message doesn't scroll, so bring the header back for it.
        appBar.setExpanded(true, true)
    }

    /** [paged]: word lists get the page nav at the bottom; root suggestions don't. */
    private fun showList(entries: List<Dictionary.Entry>, heading: String?, paged: Boolean = false) {
        showingEntry = false
        adapter.submit(entries, heading, paged)
        recycler.scrollToPosition(0)
        showOnly(recycler)
    }

    /** Back from a word: the list it came from. */
    private fun showList() {
        showingEntry = false
        if (adapter.itemCount > 0) showOnly(recycler) else search(etSearch.text.toString(), 0)
    }

    private fun showEntry(entry: Dictionary.Entry) {
        hideKeyboard()
        showingEntry = true
        lifecycleScope.launch { audio.preload(entry) }
        detailCard.removeAllViews()

        // Back to the list
        detailCard.addView(TextView(this).apply {
            text = getString(R.string.dict_back_to_list)
            setTextColor(ContextCompat.getColor(context, R.color.pg_section_ink))
            typeface = font(R.font.be_vietnam_bold)
            textSize = 14f
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.cw_ic_back, 0, 0, 0)
            compoundDrawablePadding = dp(4)
            compoundDrawableTintList = ContextCompat.getColorStateList(context, R.color.pg_section_ink)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), dp(12), dp(6))
            isClickable = true
            isFocusable = true
            setOnClickListener { showList() }
        }, wrap())

        // The word, with the speaker beside it (like Bigkas)
        val wordRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        wordRow.addView(TextView(this).apply {
            text = entry.shown
            setTextColor(ContextCompat.getColor(context, R.color.pg_ink))
            typeface = font(R.font.lexend)
            setTypeface(typeface, Typeface.BOLD)
            textSize = 30f
            isFocusable = true
            ViewCompat.setAccessibilityHeading(this, true)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        wordRow.addView(ImageButton(this).apply {
            background = ContextCompat.getDrawable(context, R.drawable.pr_btn_speaker)
            setImageResource(R.drawable.ic_speaker_on)
            imageTintList = ContextCompat.getColorStateList(context, android.R.color.white)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(14), dp(13), dp(14), dp(15))
            contentDescription = getString(R.string.dict_play_word, entry.shown)
            setOnClickListener { speak(this, entry) }
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(8) })
        detailCard.addView(wordRow, fill(top = 6))

        // Pantig under the word: ba·hay (a teacher's fix, or split automatically)
        val syllables = entry.syllables
        if (Syllables.hasBreaks(syllables)) {
            detailCard.addView(TextView(this).apply {
                text = syllables
                contentDescription = getString(R.string.dict_syllables_cd, syllables)
                setTextColor(ContextCompat.getColor(context, R.color.pg_ink_soft))
                typeface = font(R.font.be_vietnam_bold)
                textSize = 17f
                letterSpacing = 0.04f
            }, wrap())
        }

        if (entry.fromTeacher) {
            detailCard.addView(TextView(this).apply {
                text = getString(R.string.dict_from_teacher)
                setTextColor(ContextCompat.getColor(context, R.color.pg_talasalitaan))
                background = ContextCompat.getDrawable(context, R.drawable.nt_section_pill)
                backgroundTintList = ContextCompat.getColorStateList(context, R.color.pg_talasalitaan_tint)
                typeface = font(R.font.be_vietnam_bold)
                textSize = 12f
                setPadding(dp(10), dp(3), dp(10), dp(4))
            }, wrap(top = 6))
        }

        for (sense in entry.senses) {
            // A teacher can leave the part of speech empty; the meanings then start the group.
            val hasPos = sense.pos.isNotBlank()
            if (hasPos) {
                detailCard.addView(TextView(this).apply {
                    text = sense.pos
                    setTextColor(ContextCompat.getColor(context, R.color.pg_pagsusulit))
                    typeface = font(R.font.be_vietnam_bold)
                    setTypeface(typeface, Typeface.BOLD_ITALIC)
                    textSize = 13f
                }, wrap(top = 18))
            }

            sense.definitions.forEachIndexed { i, definition ->
                detailCard.addView(bodyText("${i + 1}. $definition"), fill(top = if (i == 0 && !hasPos) 18 else 6))
            }

            if (sense.examples.isNotEmpty()) {
                val box = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = ContextCompat.getDrawable(context, R.drawable.dc_example)
                    setPadding(dp(14), dp(10), dp(12), dp(10))
                }
                sense.examples.forEachIndexed { i, example ->
                    box.addView(bodyText("“$example”").apply {
                        setTypeface(typeface, Typeface.ITALIC)
                        setTextColor(ContextCompat.getColor(context, R.color.pg_ink_soft))
                    }, fill(top = if (i == 0) 0 else 6))
                }
                detailCard.addView(box, fill(top = 10))
            }
        }

        if (entry.synonyms.isNotEmpty()) {
            detailCard.addView(View(this).apply {
                setBackgroundColor(ContextCompat.getColor(context, R.color.pg_divider))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(18) })

            val chips = FlexboxLayout(this).apply {
                flexWrap = com.google.android.flexbox.FlexWrap.WRAP
                alignItems = com.google.android.flexbox.AlignItems.CENTER
            }
            chips.addView(TextView(this).apply {
                text = getString(R.string.dict_synonyms)
                setTextColor(ContextCompat.getColor(context, R.color.pg_ink_soft))
                typeface = font(R.font.be_vietnam_bold)
                textSize = 13f
            }, chipParams())
            for (synonym in entry.synonyms) {
                chips.addView(chip(synonym) { openWord(synonym) }, chipParams())
            }
            detailCard.addView(chips, fill(top = 10))
        }

        detailScroll.scrollTo(0, 0)
        showOnly(detailScroll)
    }

    /** Plays the word: its saved audio, made on the first tap by anyone (DictionaryAudio). */
    private fun speak(button: ImageButton, entry: Dictionary.Entry) {
        speakJob?.cancel()
        speakerButton?.let { setSpeaker(it, Speaker.IDLE) }
        speakerButton = button
        setSpeaker(button, Speaker.LOADING)
        speakJob = lifecycleScope.launch {
            val started = audio.play(entry) { setSpeaker(button, Speaker.IDLE) }
            setSpeaker(button, if (started) Speaker.PLAYING else Speaker.IDLE)
            if (!started) {
                Toast.makeText(this@DictionaryActivity, R.string.dict_audio_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private enum class Speaker { IDLE, LOADING, PLAYING }

    /**
     * The speaker's look, like the website's: while the audio loads it breathes with no
     * waves; while it plays its waves roll outward. With animations off, only the look changes.
     */
    private fun setSpeaker(button: ImageButton, state: Speaker) {
        wavesJob?.cancel()
        breathing?.cancel()
        button.scaleX = 1f
        button.scaleY = 1f
        button.isEnabled = state != Speaker.LOADING
        button.setImageResource(if (state == Speaker.LOADING) R.drawable.ic_dict_speaker_0 else R.drawable.ic_speaker_on)
        val animate = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        if (!animate) return
        when (state) {
            Speaker.LOADING -> breathing = ObjectAnimator.ofPropertyValuesHolder(
                button,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.88f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.88f),
            ).apply {
                duration = 550
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
            Speaker.PLAYING -> wavesJob = lifecycleScope.launch {
                var frame = 0
                while (true) {
                    button.setImageResource(SPEAKER_WAVES[frame++ % SPEAKER_WAVES.size])
                    delay(WAVE_FRAME_MS)
                }
            }
            Speaker.IDLE -> Unit
        }
    }

    override fun onStop() {
        super.onStop()
        speakJob?.cancel()
        audio.stop()
        speakerButton?.let { setSpeaker(it, Speaker.IDLE) }
    }

    override fun onDestroy() {
        super.onDestroy()
        audio.release()
    }

    private fun buildLetters() {
        val row = findViewById<LinearLayout>(R.id.lettersRow)
        for (letter in listOf("") + LETTERS) {
            val label = if (letter.isEmpty()) getString(R.string.dict_all) else letter.uppercase()
            row.addView(chip(label) {
                hideKeyboard()
                setQuery(letter)
                search(letter, 0)
            }.apply {
                minWidth = dp(44)
                minHeight = dp(40)
                gravity = Gravity.CENTER
                typeface = font(R.font.lexend)
                setTypeface(typeface, Typeface.BOLD)
                letterChips += letter to this
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(6)
            })
        }
        markLetter("")
    }

    /** Fills the chip matching the search box: "Lahat" when empty, a letter when it's one letter. */
    private fun markLetter(query: String) {
        val typed = query.trim().lowercase()
        for ((letter, chip) in letterChips) {
            val on = letter == typed
            chip.isSelected = on
            chip.background = ContextCompat.getDrawable(this, if (on) R.drawable.dc_chip_active else R.drawable.dc_chip)
            chip.setTextColor(ContextCompat.getColor(this, if (on) R.color.pg_surface else R.color.pg_section_ink))
        }
    }

    // ---------- small view helpers ----------

    private fun chip(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        background = ContextCompat.getDrawable(context, R.drawable.dc_chip)
        foreground = with(TypedValue()) {
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, this, true)
            ContextCompat.getDrawable(context, resourceId)
        }
        setTextColor(ContextCompat.getColor(context, R.color.pg_section_ink))
        typeface = font(R.font.be_vietnam_semi_bold)
        textSize = 14f
        setPadding(dp(12), dp(8), dp(12), dp(8))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun bodyText(value: String) = TextView(this).apply {
        text = value
        setTextColor(ContextCompat.getColor(context, R.color.pg_ink))
        typeface = font(R.font.public_sans)
        textSize = 15f
        setLineSpacing(0f, 1.25f)
    }

    private fun chipParams() = FlexboxLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { setMargins(0, dp(4), dp(6), dp(4)) }

    private fun wrap(top: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(top) }

    private fun fill(top: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(top) }

    private fun font(id: Int): Typeface? = ResourcesCompat.getFont(this, id)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(etSearch.windowToken, 0)
        etSearch.clearFocus()
    }

    // ---------- list ----------

    /**
     * The heading, one page of words, then the page nav (like the website's lesson list):
     * "Ipinapakita ang 11–20 sa 245 salita" over ‹ 1 … 4 5 6 … 25 ›. The page stays while a
     * word is open, so Back returns to it; a new search starts at page 1.
     */
    private class WordAdapter(
        private val onClick: (Dictionary.Entry) -> Unit,
        private val onPageChange: () -> Unit,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val TYPE_HEADING = 0
            private const val TYPE_WORD = 1
            private const val TYPE_PAGER = 2
            private const val PAGE_SIZE = 10

            /** Page numbers to show, with null where pages are skipped: 1 … 4 5 6 … 12 (js/pagination.js) */
            fun pageList(current: Int, pages: Int): List<Int?> {
                if (pages <= 7) return (1..pages).toList()
                val list = mutableListOf<Int?>(1)
                val from = maxOf(2, minOf(current - 1, pages - 4))
                val to = minOf(pages - 1, maxOf(current + 1, 5))
                if (from > 2) list += null
                list += (from..to)
                if (to < pages - 1) list += null
                list += pages
                return list
            }
        }

        private var heading: String? = null
        private var entries: List<Dictionary.Entry> = emptyList()
        private var paged = false
        private var page = 1

        fun submit(newEntries: List<Dictionary.Entry>, newHeading: String?, withPages: Boolean) {
            entries = newEntries
            heading = newHeading
            paged = withPages
            page = 1
            @Suppress("NotifyDataSetChanged")
            notifyDataSetChanged()
        }

        private fun goTo(target: Int) {
            if (target !in 1..pages || target == page) return
            page = target
            @Suppress("NotifyDataSetChanged")
            notifyDataSetChanged()
            onPageChange()
        }

        private val offset get() = if (heading != null) 1 else 0
        private val pages get() = maxOf(1, (entries.size + PAGE_SIZE - 1) / PAGE_SIZE)
        private val start get() = if (paged) (page - 1) * PAGE_SIZE else 0
        private val shown get() = if (paged) minOf(PAGE_SIZE, entries.size - start) else entries.size
        private val hasPager get() = paged && entries.isNotEmpty()

        override fun getItemCount() = offset + shown + if (hasPager) 1 else 0

        override fun getItemViewType(position: Int) = when {
            position < offset -> TYPE_HEADING
            position < offset + shown -> TYPE_WORD
            else -> TYPE_PAGER
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == TYPE_PAGER) return PagerHolder(parent.context)
            if (viewType == TYPE_HEADING) {
                val context = parent.context
                val view = TextView(context).apply {
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                    setTextColor(ContextCompat.getColor(context, R.color.pg_ink_soft))
                    typeface = ResourcesCompat.getFont(context, R.font.be_vietnam_bold)
                    textSize = 12f
                    letterSpacing = 0.06f
                    val density = context.resources.displayMetrics.density
                    setPadding(0, (2 * density).toInt(), 0, (10 * density).toInt())
                }
                return object : RecyclerView.ViewHolder(view) {}
            }
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_dictionary_word, parent, false)
            return WordHolder(view)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is PagerHolder) {
                bindPager(holder)
            } else if (holder is WordHolder) {
                val entry = entries[start + position - offset]
                holder.word.text = entry.shown
                val first = entry.senses.firstOrNull()
                holder.summary.text = listOfNotNull(first?.pos, first?.definitions?.firstOrNull())
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                holder.itemView.setOnClickListener { onClick(entry) }
            } else {
                (holder.itemView as TextView).text = heading
            }
        }

        private fun bindPager(holder: PagerHolder) {
            val context = holder.itemView.context
            holder.summary.text = HtmlCompat.fromHtml(
                context.getString(R.string.dict_page_summary, start + 1, start + shown, "%,d".format(entries.size)),
                HtmlCompat.FROM_HTML_MODE_COMPACT,
            )
            holder.buttons.removeAllViews()
            holder.buttons.addView(holder.arrow(next = false, enabled = page > 1) { goTo(page - 1) }, holder.slot())
            for (item in pageList(page, pages)) {
                holder.buttons.addView(
                    if (item == null) holder.gap() else holder.number(item, current = item == page) { goTo(item) },
                    holder.slot(),
                )
            }
            holder.buttons.addView(holder.arrow(next = true, enabled = page < pages) { goTo(page + 1) }, holder.slot())
        }

        private class WordHolder(view: View) : RecyclerView.ViewHolder(view) {
            val word: TextView = view.findViewById(R.id.tvWord)
            val summary: TextView = view.findViewById(R.id.tvSummary)
        }

        /** The page nav box, built in code; its buttons are rebuilt on every bind. */
        private class PagerHolder(context: Context) : RecyclerView.ViewHolder(LinearLayout(context)) {
            private val density = context.resources.displayMetrics.density
            private fun dp(value: Int) = (value * density).toInt()

            val summary = TextView(context).apply {
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, R.color.pg_ink_soft))
                typeface = ResourcesCompat.getFont(context, R.font.public_sans)
                textSize = 13f
            }
            val buttons = FlexboxLayout(context).apply {
                flexWrap = com.google.android.flexbox.FlexWrap.WRAP
                justifyContent = com.google.android.flexbox.JustifyContent.CENTER
                alignItems = com.google.android.flexbox.AlignItems.CENTER
            }

            init {
                (itemView as LinearLayout).apply {
                    orientation = LinearLayout.VERTICAL
                    background = ContextCompat.getDrawable(context, R.drawable.dc_pager)
                    setPadding(dp(10), dp(12), dp(10), dp(10))
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(4) }
                    addView(summary, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                    addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(8)
                    })
                }
            }

            fun slot() = FlexboxLayout.LayoutParams(dp(38), dp(38)).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }

            fun number(page: Int, current: Boolean, onClick: () -> Unit) = TextView(itemView.context).apply {
                text = page.toString()
                gravity = Gravity.CENTER
                background = ContextCompat.getDrawable(context, if (current) R.drawable.dc_page_current else R.drawable.dc_chip)
                setTextColor(ContextCompat.getColor(context, if (current) android.R.color.white else R.color.pg_ink_soft))
                typeface = ResourcesCompat.getFont(context, R.font.be_vietnam_bold)
                textSize = 14f
                contentDescription = context.getString(R.string.dict_page_cd, page)
                isSelected = current
                if (!current) {
                    foreground = with(TypedValue()) {
                        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, this, true)
                        ContextCompat.getDrawable(context, resourceId)
                    }
                    setOnClickListener { onClick() }
                }
            }

            fun gap() = TextView(itemView.context).apply {
                text = "…"
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, R.color.pg_ink_soft))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }

            fun arrow(next: Boolean, enabled: Boolean, onClick: () -> Unit) = ImageButton(itemView.context).apply {
                background = ContextCompat.getDrawable(context, R.drawable.dc_chip)
                setImageResource(R.drawable.ic_chevron_right)
                imageTintList = ContextCompat.getColorStateList(context, R.color.pg_section_ink)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(9), dp(9), dp(9), dp(9))
                if (!next) scaleX = -1f
                contentDescription = context.getString(if (next) R.string.dict_page_next else R.string.dict_page_prev)
                isEnabled = enabled
                alpha = if (enabled) 1f else 0.4f
                setOnClickListener { onClick() }
            }
        }
    }
}
