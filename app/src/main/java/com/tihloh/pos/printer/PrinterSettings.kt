package com.tihloh.pos.printer

import android.content.Context

data class PrinterConfig(
    val enabled: Boolean = false,
    val connectionType: String = "TCP",
    val host: String = "",
    val port: Int = 9100,
    val bluetoothAddress: String = "",
    val bluetoothName: String = ""
)

class PrinterSettings(context: Context) {
    private val prefs = context.getSharedPreferences("pos_printer", Context.MODE_PRIVATE)

    fun load(): PrinterConfig = PrinterConfig(
        enabled = prefs.getBoolean("enabled", false),
        connectionType = prefs.getString("connection_type", "TCP") ?: "TCP",
        host = prefs.getString("host", "").orEmpty(),
        port = prefs.getInt("port", 9100),
        bluetoothAddress = prefs.getString("bluetooth_address", "").orEmpty(),
        bluetoothName = prefs.getString("bluetooth_name", "").orEmpty()
    )

    fun save(config: PrinterConfig) {
        prefs.edit()
            .putBoolean("enabled", config.enabled)
            .putString("connection_type", config.connectionType)
            .putString("host", config.host.trim())
            .putInt("port", config.port)
            .putString("bluetooth_address", config.bluetoothAddress)
            .putString("bluetooth_name", config.bluetoothName)
            .apply()
    }
}
