package com.tihloh.pos.update

import android.content.Context
import com.tihloh.pos.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L

data class UpdateInfo(
    val version: String,
    val title: String,
    val notes: String,
    val releaseUrl: String,
    val apkUrl: String?
) {
    val preferredUrl: String get() = apkUrl ?: releaseUrl
}

class UpdateChecker(private val context: Context) {
    private val prefs = context.getSharedPreferences("pos_updates", Context.MODE_PRIVATE)

    suspend fun check(force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (!force && now - last < CHECK_INTERVAL_MS) return@withContext null

        try {
            val url = URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}")
            }

            connection.use {
                if (it.responseCode !in 200..299) return@withContext null
                val body = it.inputStream.bufferedReader().use { reader -> reader.readText() }
                val json = JSONObject(body)
                prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
                val tag = json.optString("tag_name").removePrefix("v")
                if (!isNewer(tag, BuildConfig.VERSION_NAME)) return@withContext null

                val assets = json.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) {
                    for (index in 0 until assets.length()) {
                        val asset = assets.optJSONObject(index) ?: continue
                        val name = asset.optString("name")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url").takeIf(String::isNotBlank)
                            if (name.contains("POS", ignoreCase = true)) break
                        }
                    }
                }

                UpdateInfo(
                    version = tag,
                    title = json.optString("name").ifBlank { "POS $tag" },
                    notes = json.optString("body"),
                    releaseUrl = json.optString("html_url"),
                    apkUrl = apkUrl
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isNewer(remote: String, local: String): Boolean {
        val r = normalize(remote)
        val l = normalize(local)
        val count = maxOf(r.size, l.size)
        for (i in 0 until count) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }

    private fun normalize(version: String): List<Int> = version
        .removePrefix("v")
        .substringBefore('-')
        .split('.')
        .map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }

    companion object {
        private const val KEY_LAST_CHECK = "last_check"
    }
}

private inline fun <T : HttpURLConnection, R> T.use(block: (T) -> R): R =
    try { block(this) } finally { disconnect() }
