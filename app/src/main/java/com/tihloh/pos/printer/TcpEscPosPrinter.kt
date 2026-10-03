package com.tihloh.pos.printer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.text.DateFormat
import java.util.Date

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
                out.write(buildReceipt(receipt))
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

    private fun buildReceipt(receipt: ReceiptData): ByteArray {
        val b = mutableListOf<Byte>()
        fun bytes(vararg values: Int) = values.forEach { b += it.toByte() }
        fun text(value: String) { value.toByteArray(Charsets.UTF_8).forEach { b += it } }
        fun line(value: String = "") { text(value.take(32)); bytes(10) }
        fun money(cents: Long): String = "P%.2f".format(cents / 100.0)

        bytes(0x1B, 0x40)
        bytes(0x1B, 0x61, 0x01)
        bytes(0x1B, 0x45, 0x01)
        line("POS")
        bytes(0x1B, 0x45, 0x00)
        line("Receipt ${receipt.receiptNumber}")
        line(DateFormat.getDateTimeInstance().format(Date(receipt.timestamp)))
        bytes(0x1B, 0x61, 0x00)
        line("--------------------------------")

        receipt.lines.forEach {
            line(it.name)
            val qty = if (it.quantity % 1.0 == 0.0) it.quantity.toLong().toString()
            else "%.2f".format(it.quantity)
            line("$qty x ${money(it.unitPriceCents)}  ${money(it.totalCents)}")
        }

        line("--------------------------------")
        line("Subtotal: ${money(receipt.subtotalCents)}")
        if (receipt.discountCents != 0L) line("Discount: ${money(receipt.discountCents)}")
        bytes(0x1B, 0x45, 0x01)
        line("TOTAL: ${money(receipt.totalCents)}")
        bytes(0x1B, 0x45, 0x00)
        line("Payment: ${receipt.paymentType}")
        line("Paid: ${money(receipt.amountPaidCents)}")
        line("Change: ${money(receipt.changeCents)}")
        line()
        bytes(0x1B, 0x61, 0x01)
        line("Thank you!")
        line(); line(); line()
        bytes(0x1D, 0x56, 0x00)
        return b.toByteArray()
    }
}
