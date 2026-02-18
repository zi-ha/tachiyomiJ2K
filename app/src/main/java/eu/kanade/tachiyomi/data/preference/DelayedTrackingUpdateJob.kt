package eu.kanade.tachiyomi.data.preference

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class DelayedTrackingUpdateJob(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val db = Injekt.get<DatabaseHelper>()
            val trackManager = Injekt.get<TrackManager>()

            try {
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
                Result.success()
            } catch (e: Exception) {
                Result.failure()
            }
        }

    companion object {
        private const val TAG = "DelayedTrackingUpdate"

        fun setupTask(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<DelayedTrackingUpdateJob>()
                    .setInitialDelay(1, TimeUnit.MINUTES)
                    .addTag(TAG)
                    .build()

            WorkManager.getInstance(context).enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
