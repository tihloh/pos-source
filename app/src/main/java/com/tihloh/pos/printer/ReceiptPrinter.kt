package com.tihloh.pos.printer

import android.content.Context

class ReceiptPrinter(private val context: Context) {
    suspend fun print(receipt: ReceiptData): Result<Unit> {
        val cfg = PrinterSettings(context).load()
        if (!cfg.enabled) return Result.failure(IllegalStateException("Receipt printer is disabled."))
        return when (cfg.connectionType) {
            "BLUETOOTH" -> {
                if (cfg.bluetoothAddress.isBlank()) {
                    Result.failure(IllegalStateException("Select a Bluetooth printer first."))
                } else {
                    BluetoothEscPosPrinter(context, cfg.bluetoothAddress).print(receipt)
                }
            }
            else -> {
                if (cfg.host.isBlank()) {
                    Result.failure(IllegalStateException("Printer IP / host is not configured."))
                } else {
                    TcpEscPosPrinter(cfg.host, cfg.port).print(receipt)
                }
            }
        }
    }
}
