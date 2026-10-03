package com.tihloh.pos.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(indices = [Index(value = ["barcode"], unique = true)])
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String? = null,
    val sku: String? = null,
    val name: String,
    val description: String? = null,
    val brand: String? = null,
    val category: String? = null,
    val imageUrl: String? = null,
    val itemType: String = "GOOD",
    val inventoryEnabled: Boolean = true,
    val costCents: Long = 0,
    val sellingPriceCents: Long = 0,
    val unit: String = "pc",
    val stockCache: Double = 0.0,
    val minimumStock: Double = 0.0,
    val supplierId: Long? = null,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity
data class SupplierEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val contactPerson: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val active: Boolean = true
)

@Entity(
    indices = [Index("productId"), Index("referenceId")],
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
data class InventoryTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val type: String,
    val quantityDelta: Double,
    val unitCostCents: Long? = null,
    val referenceType: String? = null,
    val referenceId: Long? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(indices = [Index("receiptNumber", unique = true)])
data class SaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptNumber: String,
    val subtotalCents: Long,
    val discountCents: Long = 0,
    val totalCents: Long,
    val amountPaidCents: Long,
    val changeCents: Long,
    val status: String = "COMPLETED",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    indices = [Index("saleId"), Index("productId")],
    foreignKeys = [
        ForeignKey(
            entity = SaleEntity::class,
            parentColumns = ["id"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
data class SaleItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val productId: Long,
    val productName: String,
    val quantity: Double,
    val unitPriceCents: Long,
    val lineTotalCents: Long
)

@Entity(
    indices = [Index("saleId")],
    foreignKeys = [
        ForeignKey(
            entity = SaleEntity::class,
            parentColumns = ["id"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val type: String,
    val amountCents: Long,
    val reference: String? = null
)

@Entity
data class InventoryPeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startAt: Long,
    val endAt: Long? = null,
    val status: String = "OPEN",
    val closedAt: Long? = null
)


@Entity(
    primaryKeys = ["productId", "supplierId"],
    indices = [Index("productId"), Index("supplierId")],
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SupplierEntity::class,
            parentColumns = ["id"],
            childColumns = ["supplierId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ProductSupplierCrossRef(
    val productId: Long,
    val supplierId: Long,
    val isPrimary: Boolean = false,
    val supplierCostCents: Long? = null,
    val supplierSku: String? = null
)
