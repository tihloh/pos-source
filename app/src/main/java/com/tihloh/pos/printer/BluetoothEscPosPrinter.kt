package com.tihloh.pos.printer

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

data class PairedPrinter(
    val name: String,
    val address: String
)

object BluetoothPrinterSupport {
    fun hasPermission(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

    fun pairedDevices(context: Context): List<PairedPrinter> {
        if (!hasPermission(context)) return emptyList()
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        return adapter.bondedDevices
            .map { PairedPrinter(it.name ?: "Bluetooth printer", it.address) }
            .sortedBy { it.name.lowercase() }
    }
}

class BluetoothEscPosPrinter(
    private val context: Context,
    private val address: String
) : PosPrinter {
    override suspend fun print(receipt: ReceiptData): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(BluetoothPrinterSupport.hasPermission(context)) {
                "Bluetooth permission is required."
            }
            val adapter = BluetoothAdapter.getDefaultAdapter()
                ?: error("Bluetooth is not supported on this device.")
            require(adapter.isEnabled) { "Bluetooth is turned off." }
            val device: BluetoothDevice = adapter.getRemoteDevice(address)
            val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            try {
                socket.connect()
                socket.outputStream.use { out ->
                    out.write(EscPosReceiptEncoder.encode(receipt))
                    out.flush()
                }
            } finally {
                runCatching { socket.close() }
            }
        }
    }

    companion object {
        private val SPP_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
