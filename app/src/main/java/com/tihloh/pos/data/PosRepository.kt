package com.tihloh.pos.data

import androidx.room.withTransaction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong
import com.tihloh.pos.sync.SyncSnapshot

data class SaleLineInput(
    val productId: Long,
    val quantity: Double
)

data class SaleDetail(
    val sale: SaleEntity,
    val items: List<SaleItemEntity>,
    val payments: List<PaymentEntity>
)

class PosRepository(private val db: AppDatabase) {
    val products = db.products().observeAll()
    val sales = db.sales().observeLatest()
    val suppliers = db.suppliers().observeAll()
    val customers = db.customers().observeAll()
    val inventoryTransactions = db.inventory().observeAll()
    val customerPayments = db.customerPayments().observeAll()
    val auditLogs = db.auditLogs().observeLatest()

    suspend fun findByBarcode(barcode: String): ProductEntity? {
        val raw = barcode.trim()
        if (raw.isBlank()) return null

        val compact = raw.filter { !it.isWhitespace() && !it.isISOControl() }
        val aimStripped = if (
            compact.length > 3 &&
            compact.startsWith("]") &&
            compact[1].isLetterOrDigit() &&
            compact[2].isLetterOrDigit()
        ) compact.drop(3) else compact

        val candidates = linkedSetOf(raw, compact, aimStripped)

        // UPC-A scanners and EAN-13 databases commonly differ only by a leading zero.
        if (aimStripped.all(Char::isDigit)) {
            if (aimStripped.length == 12) candidates += "0$aimStripped"
            if (aimStripped.length == 13 && aimStripped.startsWith("0")) {
                candidates += aimStripped.drop(1)
            }
        }

        for (candidate in candidates.filter(String::isNotBlank)) {
            db.products().findByBarcode(candidate)?.let { return it }
        }

        // Safe normalized fallback for older records saved with spaces/control characters.
        return db.products().getAllActive().firstOrNull { product ->
            product.barcode.orEmpty()
                .filter { !it.isWhitespace() && !it.isISOControl() } in candidates
        }
    }

    suspend fun saveProduct(
        product: ProductEntity,
        openingQuantity: Double = 0.0,
        supplierIds: List<Long>? = null
    ): Long = db.withTransaction {
            val now = System.currentTimeMillis()
            val cleanBarcode = product.barcode?.trim()?.ifBlank { null }
            val clean = product.copy(
                barcode = cleanBarcode,
                name = product.name.trim(),
                sku = product.sku?.trim()?.ifBlank { null },
                updatedAt = now
            )

            val id = if (clean.id == 0L) {
                db.products().insert(clean.copy(stockCache = 0.0, createdAt = now))
            } else {
                db.products().update(clean)
                clean.id
            }

            if (supplierIds != null) {
                db.productSuppliers().clearForProduct(id)
                val distinct = supplierIds.distinct()
                if (distinct.isNotEmpty()) {
                    db.productSuppliers().upsertLinks(
                        distinct.mapIndexed { index, supplierId ->
                            ProductSupplierCrossRef(
                                productId = id,
                                supplierId = supplierId,
                                isPrimary = index == 0
                            )
                        }
                    )
                }
            }

            if (clean.id == 0L && clean.inventoryEnabled && openingQuantity != 0.0) {
                db.inventory().add(
                    InventoryTransactionEntity(
                        productId = id,
                        type = "OPENING",
                        quantityDelta = openingQuantity,
                        note = "Opening stock"
                    )
                )
                db.products().adjustStock(id, openingQuantity, now)
            }

            id
        }

    suspend fun supplierIdsForProduct(productId: Long): List<Long> =
        db.productSuppliers().supplierIdsForProduct(productId)

    suspend fun productsForSupplier(supplierId: Long): List<ProductEntity> {
        val ids = db.productSuppliers().productIdsForSupplier(supplierId).toSet()
        return db.products().search("").filter { it.id in ids }
    }

    suspend fun setProductSuppliers(productId: Long, supplierIds: List<Long>) = db.withTransaction {
        db.productSuppliers().clearForProduct(productId)
        val distinct = supplierIds.distinct()
        if (distinct.isNotEmpty()) {
            db.productSuppliers().upsertLinks(
                distinct.mapIndexed { index, supplierId ->
                    ProductSupplierCrossRef(productId, supplierId, index == 0)
                }
            )
        }
    }

    suspend fun archiveProduct(productId: Long) {
        db.products().archive(productId)
    }

    suspend fun adjustStock(
        productId: Long,
        delta: Double,
        type: String,
        note: String? = null
    ) = db.withTransaction {
        require(delta != 0.0) { "Quantity cannot be zero." }
        val product = db.products().getById(productId) ?: error("Product not found.")
        require(product.inventoryEnabled) { "Inventory is disabled for this item." }
        require(product.stockCache + delta >= -0.000001) { "Not enough stock." }

        db.inventory().add(
            InventoryTransactionEntity(
                productId = productId,
                type = type,
                quantityDelta = delta,
                unitCostCents = product.costCents,
                note = note
            )
        )
        db.products().adjustStock(productId, delta)
    }

    suspend fun checkout(
        lines: List<SaleLineInput>,
        paymentType: String,
        amountPaidCents: Long,
        paymentReference: String? = null,
        customerId: Long? = null
    ): SaleDetail = db.withTransaction {
        require(lines.isNotEmpty()) { "Cart is empty." }

        val resolved = lines.map { input ->
            require(input.quantity > 0) { "Invalid quantity." }
            val product = db.products().getById(input.productId) ?: error("Product not found.")
            if (product.inventoryEnabled) {
                require(product.stockCache + 0.000001 >= input.quantity) {
                    "Insufficient stock for " + product.name + ". Available: " + product.stockCache
                }
            }
            product to input.quantity
        }

        val subtotal = resolved.sumOf { (product, quantity) ->
            (product.sellingPriceCents * quantity).roundToLong()
        }
        val total = subtotal
        val isAccountPayable = paymentType == "Account Payable"
        if (isAccountPayable) {
            require(customerId != null) { "A registered customer is required for Account Payable." }
            require(amountPaidCents == 0L) { "Account Payable must start with zero payment." }
        } else {
            require(amountPaidCents >= total) { "Payment is less than the total." }
        }

        val receipt = "POS-" + LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"))

        val selectedCustomer = customerId?.let { db.customers().getById(it) }
        val sale = SaleEntity(
            receiptNumber = receipt,
            customerId = selectedCustomer?.id,
            customerName = selectedCustomer?.name,
            subtotalCents = subtotal,
            totalCents = total,
            amountPaidCents = amountPaidCents,
            changeCents = if (isAccountPayable) 0L else amountPaidCents - total,
            status = if (isAccountPayable) "ACCOUNT_PAYABLE" else "COMPLETED"
        )
        val saleId = db.sales().addSale(sale)

        val items = resolved.map { (product, quantity) ->
            SaleItemEntity(
                saleId = saleId,
                productId = product.id,
                productName = product.name,
                quantity = quantity,
                unitPriceCents = product.sellingPriceCents,
                lineTotalCents = (product.sellingPriceCents * quantity).roundToLong()
            )
        }
        db.sales().addItems(items)

        val payments = listOf(
            PaymentEntity(
                saleId = saleId,
                type = paymentType,
                amountCents = amountPaidCents,
                reference = paymentReference?.trim()?.ifBlank { null }
            )
        )
        db.sales().addPayments(payments)

        resolved.forEach { (product, quantity) ->
            if (product.inventoryEnabled) {
                db.inventory().add(
                    InventoryTransactionEntity(
                        productId = product.id,
                        type = "SALE",
                        quantityDelta = -quantity,
                        unitCostCents = product.costCents,
                        referenceType = "SALE",
                        referenceId = saleId,
                        note = receipt
                    )
                )
                db.products().adjustStock(product.id, -quantity)
            }
        }

        SaleDetail(
            sale = sale.copy(id = saleId),
            items = items,
            payments = payments
        )
    }

    suspend fun receiveCustomerPayment(
        customerId: Long,
        amountCents: Long,
        paymentType: String,
        reference: String? = null
    ): CustomerPaymentEntity = db.withTransaction {
        require(amountCents > 0L) { "Payment amount must be greater than zero." }
        require(paymentType.isNotBlank()) { "Payment method is required." }
        val customer = db.customers().getById(customerId) ?: error("Customer not found.")
        val payableSales = db.sales().getRange(0L, Long.MAX_VALUE)
            .filter {
                it.customerId == customerId &&
                    it.status == "ACCOUNT_PAYABLE" &&
                    it.totalCents > it.amountPaidCents
            }
            .sortedBy { it.createdAt }

        val outstanding = payableSales.sumOf {
            (it.totalCents - it.amountPaidCents).coerceAtLeast(0L)
        }
        require(outstanding > 0L) { "This customer has no outstanding account balance." }
        require(amountCents <= outstanding) {
            "Payment exceeds the outstanding balance of " + outstanding + " cents."
        }

        val payment = CustomerPaymentEntity(
            customerId = customerId,
            amountCents = amountCents,
            paymentType = paymentType,
            reference = reference?.trim()?.ifBlank { null }
        )
        val paymentId = db.customerPayments().insert(payment)

        var remaining = amountCents
        val allocations = mutableListOf<CustomerPaymentAllocationEntity>()
        for (sale in payableSales) {
            if (remaining <= 0L) break
            val due = (sale.totalCents - sale.amountPaidCents).coerceAtLeast(0L)
            if (due <= 0L) continue
            val applied = minOf(remaining, due)
            val newPaid = sale.amountPaidCents + applied
            db.sales().updatePaymentState(
                saleId = sale.id,
                amountPaidCents = newPaid,
                status = if (newPaid >= sale.totalCents) "ACCOUNT_PAID" else "ACCOUNT_PAYABLE"
            )
            db.sales().addPayments(
                listOf(
                    PaymentEntity(
                        saleId = sale.id,
                        type = paymentType,
                        amountCents = applied,
                        reference = reference?.trim()?.ifBlank { null }
                    )
                )
            )
            allocations += CustomerPaymentAllocationEntity(
                paymentId = paymentId,
                saleId = sale.id,
                amountCents = applied
            )
            remaining -= applied
        }
        db.customerPayments().insertAllocations(allocations)
        db.auditLogs().insert(
            AuditLogEntity(
                action = "CUSTOMER_PAYMENT",
                entityType = "CUSTOMER",
                entityId = customerId,
                summary = "Received account payment from ${customer.name}",
                metadata = "paymentId=$paymentId;amountCents=$amountCents;method=$paymentType"
            )
        )
        payment.copy(id = paymentId)
    }

    suspend fun customerPaymentHistory(customerId: Long): List<CustomerPaymentEntity> =
        db.customerPayments().forCustomer(customerId)

    suspend fun deleteSale(
        saleId: Long,
        reason: String,
        authMethod: String
    ) = db.withTransaction {
        val cleanReason = reason.trim()
        require(cleanReason.length >= 3) { "Deletion reason is required." }
        val sale = db.sales().getSale(saleId) ?: error("Sale not found.")
        require(sale.status != "DELETED") { "Sale is already deleted." }

        if (sale.status == "ACCOUNT_PAYABLE" || sale.status == "ACCOUNT_PAID") {
            require(sale.amountPaidCents == 0L) {
                "A sale with account payments cannot be deleted because payments have already been applied."
            }
        }

        val items = db.sales().getItems(saleId)
        items.forEach { item ->
            val product = db.products().getById(item.productId) ?: return@forEach
            if (product.inventoryEnabled) {
                db.inventory().add(
                    InventoryTransactionEntity(
                        productId = product.id,
                        type = "SALE_DELETE_REVERSAL",
                        quantityDelta = item.quantity,
                        unitCostCents = product.costCents,
                        referenceType = "SALE",
                        referenceId = saleId,
                        note = "Deleted ${sale.receiptNumber}: $cleanReason"
                    )
                )
                db.products().adjustStock(product.id, item.quantity)
            }
        }

        db.sales().markDeleted(saleId)
        db.auditLogs().insert(
            AuditLogEntity(
                action = "SALE_DELETE",
                entityType = "SALE",
                entityId = saleId,
                summary = "Deleted sale ${sale.receiptNumber}",
                metadata = "reason=$cleanReason;totalCents=${sale.totalCents};customer=${sale.customerName.orEmpty()}",
                authMethod = authMethod
            )
        )
    }
    suspend fun salesRange(from: Long, to: Long): List<SaleEntity> =
        db.sales().getRange(from, to)

    suspend fun syncSnapshot(): SyncSnapshot = SyncSnapshot(
        products = db.products().getAllActive(),
        suppliers = db.suppliers().getAllActive(),
        customers = db.customers().getAllActive(),
        sales = db.sales().getRange(0L, Long.MAX_VALUE)
    )

    suspend fun saleDetail(saleId: Long): SaleDetail? {
        val sale = db.sales().getSale(saleId) ?: return null
        return SaleDetail(
            sale = sale,
            items = db.sales().getItems(saleId),
            payments = db.sales().getPayments(saleId)
        )
    }

    suspend fun saveSupplier(supplier: SupplierEntity): Long {
        return if (supplier.id == 0L) db.suppliers().insert(supplier)
        else {
            db.suppliers().update(supplier)
            supplier.id
        }
    }

    suspend fun findCustomerByBarcode(barcode: String): CustomerEntity? {
        val cleaned = barcode.trim().filter { !it.isWhitespace() && !it.isISOControl() }
        if (cleaned.isBlank()) return null
        return db.customers().findByBarcode(cleaned)
    }

    suspend fun saveCustomer(customer: CustomerEntity): Long = db.withTransaction {
        val now = System.currentTimeMillis()
        val barcode = customer.barcode.trim()
            .filter { !it.isWhitespace() && !it.isISOControl() }
            .ifBlank { "CUS-$now" }

        val clean = customer.copy(
            barcode = barcode,
            name = customer.name.trim(),
            phone = customer.phone?.trim()?.ifBlank { null },
            email = customer.email?.trim()?.ifBlank { null },
            address = customer.address?.trim()?.ifBlank { null },
            careOf = null,
            notes = customer.notes?.trim()?.ifBlank { null },
            updatedAt = now
        )

        if (clean.id == 0L) {
            db.customers().insert(clean.copy(createdAt = now))
        } else {
            db.customers().update(clean)
            clean.id
        }
    }

    suspend fun archiveCustomer(customerId: Long) {
        db.customers().archive(customerId)
    }
}
