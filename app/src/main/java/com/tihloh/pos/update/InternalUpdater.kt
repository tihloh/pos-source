package com.tihloh.pos.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class InternalUpdater(private val context: Context) {

    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )

    suspend fun install(update: UpdateInfo): Result<Unit> = withContext(Dispatchers.IO) {
        val apkUrl = update.apkUrl
            ?: return@withContext Result.failure(
                IllegalStateException("This release does not contain an APK asset.")
            )

        val installer = context.packageManager.packageInstaller
        var sessionId: Int? = null

        try {
            val connection = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty(
                    "User-Agent",
                    "${context.packageName}/${update.version}"
                )
                setRequestProperty("Accept", "application/vnd.android.package-archive")
            }

            try {
                val status = connection.responseCode
                if (status !in 200..299) {
                    throw IllegalStateException("APK download failed with HTTP $status.")
                }

                val params = PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL
                ).apply {
                    setAppPackageName(context.packageName)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setRequireUserAction(
                            PackageInstaller.SessionParams.USER_ACTION_REQUIRED
                        )
                    }
                }

                sessionId = installer.createSession(params)
                val session = installer.openSession(sessionId)

                try {
                    val length = connection.contentLengthLong.takeIf { it > 0 } ?: -1L

                    connection.inputStream.use { input ->
                        session.openWrite("base.apk", 0, length).use { output ->
                            input.copyTo(output, DEFAULT_BUFFER_SIZE * 8)
                            session.fsync(output)
                        }
                    }

                    val callbackIntent = Intent(
                        context,
                        UpdateInstallReceiver::class.java
                    ).apply {
                        action = UpdateInstallReceiver.ACTION_INSTALL_STATUS
                    }

                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        callbackIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )

                    session.commit(pendingIntent.intentSender)
                } finally {
                    session.close()
                }
            } finally {
                connection.disconnect()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            sessionId?.let { id ->
                runCatching { installer.abandonSession(id) }
            }
            Result.failure(e)
        }
    }
}
