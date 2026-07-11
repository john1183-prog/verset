package com.johndev.verset.sync

import android.content.Context
import androidx.work.*
import com.johndev.verset.data.AppDatabase
import com.johndev.verset.data.Prefs
import com.johndev.verset.repository.SyncRepository
import java.util.concurrent.TimeUnit

/**
 * Runs a Firestore sync in the background every 6 hours when the user is
 * signed in and the device has network. Completely silent — no notification,
 * no UI interruption. Users can still trigger a manual sync from Settings.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.get(applicationContext)
        val repo = SyncRepository(db)
        return if (repo.syncNow().isSuccess) Result.success() else Result.retry()
    }

    companion object {
        private const val WORK_NAME = "verset_auto_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                // Back off and retry up to 3x if sync fails (e.g. momentary network drop)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // don't replace existing scheduled run
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
