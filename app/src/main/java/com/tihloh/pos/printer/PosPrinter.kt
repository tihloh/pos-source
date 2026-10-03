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
    val timestamp: Long,
    val customerName: String? = null,
    val careOf: String? = null,
    val storeName: String = "POS",
    val receiptTemplate: String = DEFAULT_RECEIPT_TEMPLATE
)

const val DEFAULT_RECEIPT_TEMPLATE = """{store}
Receipt {receipt}
{datetime}
Customer: {customer}
Care of: {care_of}
--------------------------------
{items}
--------------------------------
Subtotal: {subtotal}
Discount: {discount}
TOTAL: {total}
Payment: {payment}
Paid: {paid}
Change: {change}

Thank you!"""

interface PosPrinter {
    suspend fun print(receipt: ReceiptData): Result<Unit>
}
