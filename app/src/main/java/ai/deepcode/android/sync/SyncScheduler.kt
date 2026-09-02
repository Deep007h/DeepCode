package ai.deepcode.android.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<AutoFetchWorker>(20, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "auto_fetch",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
