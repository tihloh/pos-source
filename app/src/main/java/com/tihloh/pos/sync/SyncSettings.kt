package com.tihloh.pos.sync

import android.content.Context

data class SyncConfig(
    val enabled: Boolean,
    val baseUrl: String,
    val apiToken: String
)

class SyncSettings(context: Context) {
    private val prefs = context.getSharedPreferences("pos_sync", Context.MODE_PRIVATE)

    fun load(): SyncConfig = SyncConfig(
        enabled = prefs.getBoolean("enabled", false),
        baseUrl = prefs.getString("base_url", "").orEmpty(),
        apiToken = prefs.getString("api_token", "").orEmpty()
    )

    fun save(config: SyncConfig) {
        prefs.edit()
            .putBoolean("enabled", config.enabled)
            .putString("base_url", config.baseUrl.trim().trimEnd('/'))
            .putString("api_token", config.apiToken.trim())
            .apply()
    }
}
