package com.tihloh.pos.printer

import android.content.Context

data class PrinterConfig(
    val enabled: Boolean,
    val host: String,
    val port: Int
)

class PrinterSettings(context: Context) {
    private val prefs = context.getSharedPreferences("pos_printer", Context.MODE_PRIVATE)

    fun load(): PrinterConfig = PrinterConfig(
        enabled = prefs.getBoolean("enabled", false),
        host = prefs.getString("host", "").orEmpty(),
        port = prefs.getInt("port", 9100)
    )

    fun save(config: PrinterConfig) {
        prefs.edit()
            .putBoolean("enabled", config.enabled)
            .putString("host", config.host.trim())
            .putInt("port", config.port)
            .apply()
    }
}
