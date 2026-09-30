package com.tihloh.pos.printer

enum class PrinterConnectionType { BLUETOOTH, USB, TCP }

data class ReceiptLine(
    val name: String,
    val quantity: Double,
    val unitPriceCents: Long,
    val totalCents: Long
)

data class ReceiptData(
    val receiptNumber: String,
    val lines: List<ReceiptLine>,
    val subtotalCents: Long,
    val discountCents: Long,
    val totalCents: Long,
    val paymentType: String,
    val amountPaidCents: Long,
    val changeCents: Long,
    val timestamp: Long
)

interface PosPrinter {
    suspend fun print(receipt: ReceiptData): Result<Unit>
}

// ESC/POS Bluetooth, USB and TCP implementations will implement PosPrinter.
