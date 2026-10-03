package com.tihloh.pos.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ForcedUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val checker = UpdateChecker(applicationContext)
        val updater = InternalUpdater(applicationContext)
        val update = checker.check(force = true) ?: return Result.success()

        // On managed/allowed devices, stage the installer immediately in the background.
        // Android may still require its system confirmation screen for ordinary sideloaded apps.
        return if (updater.canInstallPackages()) {
            updater.install(update).fold(
                onSuccess = { Result.success() },
                onFailure = { Result.retry() }
            )
        } else {
            Result.success()
        }
    }
}
