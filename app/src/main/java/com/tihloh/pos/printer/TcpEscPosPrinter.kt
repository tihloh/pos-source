package com.tihloh.pos.printer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class TcpEscPosPrinter(
    private val host: String,
    private val port: Int = 9100
) : PosPrinter {

    override suspend fun print(receipt: ReceiptData): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(host.isNotBlank()) { "Printer host is not configured." }
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), 5_000)
            socket.soTimeout = 8_000
            socket.getOutputStream().use { out ->
                out.write(EscPosReceiptEncoder.encode(receipt))
                out.flush()
            }
            socket.close()
        }
    }

    suspend fun test(): Result<Unit> = print(
        ReceiptData(
            receiptNumber = "TEST",
            lines = listOf(ReceiptLine("Printer test", 1.0, 0, 0)),
            subtotalCents = 0,
            discountCents = 0,
            totalCents = 0,
            paymentType = "TEST",
            amountPaidCents = 0,
            changeCents = 0,
            timestamp = System.currentTimeMillis()
        )
    )

}
