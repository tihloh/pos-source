package com.tihloh.pos.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object UpdateScheduler {
    private const val PERIODIC_NAME = "pos-forced-update-periodic"
    private const val STARTUP_NAME = "pos-forced-update-startup"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            STARTUP_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ForcedUpdateWorker>()
                .setConstraints(constraints)
                .build()
        )

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ForcedUpdateWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
        )
    }
}
