package com.tihloh.pos.printer

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object EscPosReceiptEncoder {
    fun encode(receipt: ReceiptData): ByteArray {
        val b = mutableListOf<Byte>()
        fun bytes(vararg values: Int) = values.forEach { b += it.toByte() }
        fun text(value: String) { value.toByteArray(Charsets.UTF_8).forEach { b += it } }
        fun line(value: String = "") {
            wrap(value, 32).forEach {
                text(it)
                bytes(10)
            }
        }

        bytes(0x1B, 0x40)
        val rendered = renderTemplate(receipt)
        rendered.lines().forEach(::line)
        line(); line()
        bytes(0x1D, 0x56, 0x00)
        return b.toByteArray()
    }

    fun renderTemplate(receipt: ReceiptData): String {
        fun money(cents: Long): String = "P%.2f".format(cents / 100.0)
        fun qty(value: Double): String =
            if (value % 1.0 == 0.0) value.toLong().toString()
            else "%.2f".format(value)

        val date = Date(receipt.timestamp)
        val items = receipt.lines.joinToString("\n") {
            val first = it.name
            val second = "${qty(it.quantity)} x ${money(it.unitPriceCents)}  ${money(it.totalCents)}"
            "$first\n$second"
        }

        val replacements = linkedMapOf(
            "{store}" to receipt.storeName,
            "{receipt}" to receipt.receiptNumber,
            "{date}" to SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date),
            "{time}" to SimpleDateFormat("HH:mm", Locale.getDefault()).format(date),
            "{datetime}" to DateFormat.getDateTimeInstance().format(date),
            "{customer}" to (receipt.customerName ?: "Walk-in"),
            "{items}" to items,
            "{item_count}" to receipt.lines.sumOf { it.quantity }.let(::qty),
            "{subtotal}" to money(receipt.subtotalCents),
            "{discount}" to money(receipt.discountCents),
            "{total}" to money(receipt.totalCents),
            "{payment}" to receipt.paymentType,
            "{paid}" to money(receipt.amountPaidCents),
            "{change}" to money(receipt.changeCents)
        )

        var result = receipt.receiptTemplate
        replacements.forEach { (placeholder, value) ->
            result = result.replace(placeholder, value)
        }
        return result
    }

    private fun wrap(value: String, width: Int): List<String> {
        if (value.length <= width) return listOf(value)
        val out = mutableListOf<String>()
        var remaining = value
        while (remaining.length > width) {
            val cut = remaining.take(width)
            val split = cut.lastIndexOf(' ').takeIf { it > width / 2 } ?: width
            out += remaining.take(split).trimEnd()
            remaining = remaining.drop(split).trimStart()
        }
        out += remaining
        return out
    }
}
