package com.example.basaya.data.dictionary

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

/**
 * The Diksiyonaryo, like the website's js/dictionary.js. Two sources:
 *  - assets/dictionary/tl-seed.json: Tagalog Wiktionary words (CC BY-SA), bundled with the
 *    app so they work offline. The same file as the website's data/dictionary/tl-seed.json;
 *    copy it here again whenever the website rebuilds it (tools/build-dictionary.mjs).
 *  - Firestore dictionary/{key}: words teachers add or fix on the website's teacher portal.
 *    The doc id is the search key, so a teacher's word replaces the seed word with the
 *    same key, and `hidden: true` takes a bad seed word out.
 */
object Dictionary {

    data class Sense(val pos: String, val definitions: List<String>, val examples: List<String>)

    data class Entry(
        val word: String,
        val key: String,
        val accent: String,
        val senses: List<Sense>,
        val synonyms: List<String>,
        val fromTeacher: Boolean,
        val hidden: Boolean = false,
        /** A teacher's fix for the automatic syllable split ("trak·tor"); blank = split automatically. */
        val fixedSyllables: String = "",
    ) {
        /** The spelling to show: the accented one when there is one (aklát). */
        val shown: String get() = accent.ifBlank { word }

        /** Pantig to show under the word: "ba·hay". */
        val syllables: String get() = fixedSyllables.ifBlank { Syllables.split(shown) }
    }

    // tl-seed.json rows: short names keep the file small.
    private class SeedSense(
        @SerializedName("p") val pos: String?,
        @SerializedName("d") val definitions: List<String>?,
        @SerializedName("e") val examples: List<String>?,
    )

    private class SeedEntry(
        @SerializedName("w") val word: String?,
        @SerializedName("k") val key: String?,
        @SerializedName("a") val accent: String?,
        @SerializedName("s") val senses: List<SeedSense>?,
        @SerializedName("syn") val synonyms: List<String>?,
    )

    private const val TAG = "Dictionary"
    private const val SEED_ASSET = "dictionary/tl-seed.json"
    private const val MAX_RESULTS = 30

    private val collator: Collator = Collator.getInstance(Locale("fil"))
    private val seedLock = Mutex()
    private var seed: List<Entry>? = null
    private var seedByKey: Map<String, Entry> = emptyMap()

    private fun words() = FirebaseFirestore.getInstance().collection("dictionary")

    /** The Wiktionary words, read from the app once. */
    suspend fun loadSeed(context: Context): List<Entry> = seedLock.withLock {
        seed ?: withContext(Dispatchers.IO) {
            val json = context.applicationContext.assets.open(SEED_ASSET).bufferedReader().use { it.readText() }
            Gson().fromJson(json, Array<SeedEntry>::class.java)
                .mapNotNull { row ->
                    val word = row.word ?: return@mapNotNull null
                    Entry(
                        word = word,
                        key = row.key ?: DictionaryWords.normalize(word),
                        accent = row.accent.orEmpty(),
                        senses = row.senses.orEmpty().map {
                            Sense(it.pos.orEmpty(), it.definitions.orEmpty(), it.examples.orEmpty())
                        },
                        synonyms = row.synonyms.orEmpty(),
                        fromTeacher = false,
                    )
                }
        }.also { list ->
            seed = list
            seedByKey = list.associateBy { it.key }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun fromDoc(doc: DocumentSnapshot): Entry {
        val senses = (doc.get("senses") as? List<Map<String, Any?>>).orEmpty().map { sense ->
            Sense(
                pos = sense["pos"] as? String ?: "",
                definitions = (sense["definitions"] as? List<*>).orEmpty().filterIsInstance<String>(),
                examples = (sense["examples"] as? List<*>).orEmpty().filterIsInstance<String>(),
            )
        }
        return Entry(
            word = doc.getString("word") ?: doc.id,
            key = doc.id,
            accent = doc.getString("accent").orEmpty(),
            senses = senses,
            synonyms = (doc.get("synonyms") as? List<*>).orEmpty().filterIsInstance<String>(),
            fromTeacher = true,
            hidden = doc.getBoolean("hidden") == true,
            fixedSyllables = doc.getString("syllables").orEmpty(),
        )
    }

    /**
     * Teacher words are extra: offline, or before the dictionary rules are deployed,
     * the Wiktionary words still work.
     */
    private suspend fun <T> orNull(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "Teacher words unavailable", e)
            null
        }

    private suspend fun teacherWordsStartingWith(prefix: String): List<Entry> =
        orNull {
            words()
                .whereGreaterThanOrEqualTo("key", prefix)
                .whereLessThanOrEqualTo("key", prefix + "")
                .orderBy("key")
                .limit(MAX_RESULTS.toLong())
                .get()
                .await()
                .documents
                .map(::fromDoc)
        }.orEmpty()

    /** Words starting with [text], the exact match first. Teacher words replace seed words. */
    suspend fun search(context: Context, text: String): List<Entry> = coroutineScope {
        val prefix = DictionaryWords.normalize(text)
        if (prefix.isEmpty()) return@coroutineScope emptyList()
        val teacher = async { teacherWordsStartingWith(prefix) }
        val found = LinkedHashMap<String, Entry>()
        loadSeed(context).filter { it.key.startsWith(prefix) }.forEach { found[it.key] = it }
        teacher.await().forEach { entry ->
            if (entry.hidden) found.remove(entry.key) else found[entry.key] = entry
        }
        found.values
            .sortedWith(compareBy<Entry> { it.key != prefix }.thenComparator { a, b -> collator.compare(a.key, b.key) })
            .take(MAX_RESULTS)
    }

    /** One word by its spelling, or null (also null when a teacher hid it). */
    suspend fun lookup(context: Context, text: String): Entry? {
        val key = DictionaryWords.normalize(text)
        if (key.isEmpty() || '/' in key) return null
        loadSeed(context)
        val doc = orNull { words().document(key).get().await() }
        if (doc != null && doc.exists()) return fromDoc(doc).takeUnless { it.hidden }
        return seedByKey[key]
    }

    /** Root words the dictionary has for an inflected word: "kumakain" → [kain]. */
    suspend fun suggestRoots(context: Context, text: String): List<Entry> {
        val roots = DictionaryWords.guessRoots(text).filter { '/' !in it }
        if (roots.isEmpty()) return emptyList()
        loadSeed(context)
        val teacher = orNull {
            words().whereIn(FieldPath.documentId(), roots.take(10)).get().await().documents.map(::fromDoc)
        }.orEmpty().associateBy { it.key }
        return roots
            .mapNotNull { teacher[it] ?: seedByKey[it] }
            .filterNot { it.hidden }
            .take(3)
    }

    /** A few random words to try before the student types anything. */
    suspend fun samples(context: Context, count: Int = 6): List<Entry> =
        loadSeed(context).shuffled().take(count)
}
