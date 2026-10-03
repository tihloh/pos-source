package com.tihloh.pos.printer

import com.tihloh.pos.data.SaleDetail

fun SaleDetail.toReceiptData(): ReceiptData {
    val payment = payments.firstOrNull()
    return ReceiptData(
        receiptNumber = sale.receiptNumber,
        lines = items.map {
            ReceiptLine(
                name = it.productName,
                quantity = it.quantity,
                unitPriceCents = it.unitPriceCents,
                totalCents = it.lineTotalCents
            )
        },
        subtotalCents = sale.subtotalCents,
        discountCents = sale.discountCents,
        totalCents = sale.totalCents,
        paymentType = payment?.type ?: "Unknown",
        amountPaidCents = sale.amountPaidCents,
        changeCents = sale.changeCents,
        timestamp = sale.createdAt,
        customerName = sale.customerName
    )
}
