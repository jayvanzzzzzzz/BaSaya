package ph.basaya.app.data.dictionary

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ph.basaya.app.data.database.AppDatabase
import ph.basaya.app.data.entity.TeacherDictionaryEntity

object TeacherDictionarySync {

    private const val TAG = "TeacherDictionarySync"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var dictionaryListener: ListenerRegistration? = null

    @Synchronized
    fun initialize(context: Context) {
        if (authListener != null) return

        val appContext = context.applicationContext
        val auth = FirebaseAuth.getInstance()
        authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            synchronized(this) {
                dictionaryListener?.remove()
                dictionaryListener = null
                if (firebaseAuth.currentUser == null) return@synchronized

                dictionaryListener = FirebaseFirestore.getInstance()
                    .collection("dictionary")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.w(TAG, "Dictionary sync listener failed", error)
                            return@addSnapshotListener
                        }
                        if (snapshot == null || snapshot.metadata.isFromCache) return@addSnapshotListener

                        val entries = snapshot.documents.map { doc ->
                            TeacherDictionaryEntity(
                                key = doc.id,
                                word = doc.getString("word") ?: doc.id,
                                accent = doc.getString("accent").orEmpty(),
                                sensesJson = Dictionary.gson.toJson(Dictionary.sensesFromDocument(doc)),
                                synonymsJson = Dictionary.gson.toJson(
                                    (doc.get("synonyms") as? List<*>).orEmpty().filterIsInstance<String>()
                                ),
                                hidden = doc.getBoolean("hidden") == true,
                                fixedSyllables = doc.getString("syllables").orEmpty()
                            )
                        }
                        scope.launch {
                            syncMutex.withLock {
                                try {
                                    AppDatabase.getDatabase(appContext)
                                        .teacherDictionaryDao()
                                        .replaceAll(entries)
                                    Log.i(TAG, "Saved ${entries.size} teacher/admin dictionary entries")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Could not save the synced dictionary to Room", e)
                                }
                            }
                        }
                    }
            }
        }
        auth.addAuthStateListener(authListener!!)
    }
}
