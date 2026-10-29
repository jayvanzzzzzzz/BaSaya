package ph.basaya.app.data.repository

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object DownloadedLessonRefresh {

    private const val TAG = "DownloadedLessonRefresh"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshing = ConcurrentHashMap.newKeySet<String>()

    fun launch(key: String, refresh: suspend () -> Unit) {
        if (!refreshing.add(key)) return

        scope.launch {
            try {
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not refresh downloaded content ($key)", e)
            } finally {
                refreshing.remove(key)
            }
        }
    }
}
