package eu.kanade.tachiyomi.data.track

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class UnattendedTrackService : Service() {
    private val db: DatabaseHelper = Injekt.get()
    private val trackManager: TrackManager = Injekt.get()
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        scope.launch {
            syncTracking()
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun syncTracking() {
        val tracks = db.getTracks().executeAsBlocking()
        tracks.forEach { track ->
            val service = trackManager.getService(track.sync_id)
            if (service != null && service.isLogged) {
                try {
                    service.update(track)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startService(Intent(context, UnattendedTrackService::class.java))
        }
    }
}
