package ph.basaya.app.data.dictionary

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ph.basaya.app.data.database.AppDatabase
import ph.basaya.app.data.entity.TeacherDictionaryEntity
import java.text.Collator
import java.util.Locale

/**
 * The Diksiyonaryo, like the website's js/dictionary.js. Two sources:
 *  - assets/dictionary/tl-seed.json: Tagalog Wiktionary words (CC BY-SA), bundled with the
 *    app so they work offline. The same file as the website's data/dictionary/tl-seed.json;
 *    copy it here again whenever the website rebuilds it (tools/build-dictionary.mjs).
 *  - Firestore dictionary/{key}: words teachers add or fix on the website's teacher portal.
 *    The doc id is the search key, so a teacher's word replaces the seed word with the
 *    same key, and `hidden: true` marks a seed word deleted (by an admin).
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
    /** Every match; the list's page nav splits them (like the website's MAX_RESULTS). */
    private const val MAX_RESULTS = 2000

    internal val gson = Gson()
    private val collator: Collator = Collator.getInstance(Locale("fil"))
    private val seedLock = Mutex()
    private var seed: List<Entry>? = null
    private var seedByKey: Map<String, Entry> = emptyMap()

    /** The Wiktionary words, read from the app once. */
    suspend fun loadSeed(context: Context): List<Entry> = seedLock.withLock {
        seed ?: withContext(Dispatchers.IO) {
            val json = context.applicationContext.assets.open(SEED_ASSET).bufferedReader().use { it.readText() }
            gson.fromJson(json, Array<SeedEntry>::class.java)
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
    internal fun sensesFromDocument(doc: DocumentSnapshot): List<Sense> =
        (doc.get("senses") as? List<Map<String, Any?>>).orEmpty().map { sense ->
            Sense(
                pos = sense["pos"] as? String ?: "",
                definitions = (sense["definitions"] as? List<*>).orEmpty().filterIsInstance<String>(),
                examples = (sense["examples"] as? List<*>).orEmpty().filterIsInstance<String>(),
            )
        }

    private fun fromEntity(entity: TeacherDictionaryEntity): Entry {
        val sensesType = object : TypeToken<List<Sense>>() {}.type
        val synonymsType = object : TypeToken<List<String>>() {}.type
        return Entry(
            word = entity.word,
            key = entity.key,
            accent = entity.accent,
            senses = gson.fromJson(entity.sensesJson, sensesType) ?: emptyList(),
            synonyms = gson.fromJson(entity.synonymsJson, synonymsType) ?: emptyList(),
            fromTeacher = true,
            hidden = entity.hidden,
            fixedSyllables = entity.fixedSyllables
        )
    }

    private suspend fun cachedTeacherEntry(context: Context, key: String): Entry? =
        try {
            AppDatabase.getDatabase(context).teacherDictionaryDao().getByKey(key)?.let(::fromEntity)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read cached dictionary entry $key", e)
            null
        }

    private suspend fun cachedTeacherEntries(context: Context): List<Entry> =
        try {
            AppDatabase.getDatabase(context).teacherDictionaryDao().getAll().map(::fromEntity)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read cached teacher dictionary", e)
            emptyList()
        }

    /** Words starting with [text], the exact match first. Teacher words replace seed words. */
    suspend fun search(context: Context, text: String): List<Entry> {
        val prefix = DictionaryWords.normalize(text)
        if (prefix.isEmpty()) return emptyList()
        val found = LinkedHashMap<String, Entry>()
        loadSeed(context).filter { it.key.startsWith(prefix) }.forEach { found[it.key] = it }
        cachedTeacherEntries(context).filter { it.key.startsWith(prefix) }.forEach { entry ->
            if (entry.hidden) found.remove(entry.key) else found[entry.key] = entry
        }
        return found.values
            .sortedWith(compareBy<Entry> { it.key != prefix }.thenComparator { a, b -> collator.compare(a.key, b.key) })
            .take(MAX_RESULTS)
    }

    /** One word by its spelling, or null (also null when a teacher hid it). */
    suspend fun lookup(context: Context, text: String): Entry? {
        val key = DictionaryWords.normalize(text)
        if (key.isEmpty() || '/' in key) return null
        loadSeed(context)
        cachedTeacherEntry(context, key)?.let { return it.takeUnless { entry -> entry.hidden } }
        return seedByKey[key]
    }

    /** Root words the dictionary has for an inflected word: "kumakain" → [kain]. */
    suspend fun suggestRoots(context: Context, text: String): List<Entry> {
        val roots = DictionaryWords.guessRoots(text).filter { '/' !in it }
        if (roots.isEmpty()) return emptyList()
        loadSeed(context)
        val teacher = cachedTeacherEntries(context).filter { it.key in roots }.associateBy { it.key }
        return roots
            .mapNotNull { teacher[it] ?: seedByKey[it] }
            .filterNot { it.hidden }
            .take(3)
    }

    /**
     * Every word, A to Z ("Lahat"): the seed with cached teacher words laid over it, deleted
     * ones left out. The teacher dictionary is synchronized to Room while online.
     */
    suspend fun allWords(context: Context): List<Entry> {
        val found = LinkedHashMap<String, Entry>()
        loadSeed(context).forEach { found[it.key] = it }
        cachedTeacherEntries(context).forEach { entry ->
            if (entry.hidden) found.remove(entry.key) else found[entry.key] = entry
        }
        return found.values.sortedWith { a, b -> collator.compare(a.key, b.key) }
    }
}
