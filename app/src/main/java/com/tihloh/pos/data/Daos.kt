package com.tihloh.pos.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.OnConflictStrategy
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM ProductEntity WHERE active = 1 ORDER BY name")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM ProductEntity WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ProductEntity?

    @Query("SELECT * FROM ProductEntity WHERE active = 1 ORDER BY name")
    suspend fun getAllActive(): List<ProductEntity>

    @Query("SELECT * FROM ProductEntity WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): ProductEntity?

    @Query("SELECT * FROM ProductEntity WHERE active = 1 AND (name LIKE '%' || :query || '%' OR sku LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%') ORDER BY name LIMIT 100")
    suspend fun search(query: String): List<ProductEntity>

    @Insert
    suspend fun insert(product: ProductEntity): Long

    @Update
    suspend fun update(product: ProductEntity)

    @Query("UPDATE ProductEntity SET stockCache = stockCache + :delta, updatedAt = :updatedAt WHERE id = :productId")
    suspend fun adjustStock(productId: Long, delta: Double, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE ProductEntity SET active = 0, updatedAt = :updatedAt WHERE id = :productId")
    suspend fun archive(productId: Long, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface InventoryDao {
    @Insert
    suspend fun add(transaction: InventoryTransactionEntity): Long

    @Query("SELECT * FROM InventoryTransactionEntity WHERE productId = :productId ORDER BY createdAt DESC")
    fun observeProductLedger(productId: Long): Flow<List<InventoryTransactionEntity>>

    @Query("SELECT COALESCE(SUM(quantityDelta), 0) FROM InventoryTransactionEntity WHERE productId = :productId")
    suspend fun balance(productId: Long): Double

    @Query("SELECT * FROM InventoryTransactionEntity ORDER BY createdAt")
    fun observeAll(): Flow<List<InventoryTransactionEntity>>
}

@Dao
interface SalesDao {
    @Query("SELECT * FROM SaleEntity ORDER BY createdAt DESC")
    fun observeLatest(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM SaleEntity WHERE id = :saleId LIMIT 1")
    suspend fun getSale(saleId: Long): SaleEntity?

    @Query("SELECT * FROM SaleItemEntity WHERE saleId = :saleId ORDER BY id")
    suspend fun getItems(saleId: Long): List<SaleItemEntity>

    @Query("SELECT * FROM PaymentEntity WHERE saleId = :saleId ORDER BY id")
    suspend fun getPayments(saleId: Long): List<PaymentEntity>

    @Insert
    suspend fun addSale(sale: SaleEntity): Long

    @Insert
    suspend fun addItems(items: List<SaleItemEntity>)

    @Insert
    suspend fun addPayments(payments: List<PaymentEntity>)

    @Query("SELECT * FROM SaleEntity WHERE createdAt BETWEEN :from AND :to ORDER BY createdAt DESC")
    suspend fun getRange(from: Long, to: Long): List<SaleEntity>
}

@Dao
interface SupplierDao {
    @Query("SELECT * FROM SupplierEntity WHERE active = 1 ORDER BY name")
    fun observeAll(): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM SupplierEntity WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SupplierEntity?

    @Query("SELECT * FROM SupplierEntity WHERE active = 1 ORDER BY name")
    suspend fun getAllActive(): List<SupplierEntity>

    @Insert
    suspend fun insert(supplier: SupplierEntity): Long

    @Update
    suspend fun update(supplier: SupplierEntity)
}


@Dao
interface CustomerDao {
    @Query("SELECT * FROM CustomerEntity WHERE active = 1 ORDER BY name")
    fun observeAll(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM CustomerEntity WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CustomerEntity?

    @Query("SELECT * FROM CustomerEntity WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): CustomerEntity?

    @Query("SELECT * FROM CustomerEntity WHERE active = 1 ORDER BY name")
    suspend fun getAllActive(): List<CustomerEntity>

    @Insert
    suspend fun insert(customer: CustomerEntity): Long

    @Update
    suspend fun update(customer: CustomerEntity)

    @Query("UPDATE CustomerEntity SET active = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun archive(id: Long, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface ProductSupplierDao {
    @Query("SELECT supplierId FROM ProductSupplierCrossRef WHERE productId = :productId ORDER BY isPrimary DESC, supplierId")
    suspend fun supplierIdsForProduct(productId: Long): List<Long>

    @Query("SELECT productId FROM ProductSupplierCrossRef WHERE supplierId = :supplierId ORDER BY productId")
    suspend fun productIdsForSupplier(supplierId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLinks(links: List<ProductSupplierCrossRef>)

    @Query("DELETE FROM ProductSupplierCrossRef WHERE productId = :productId")
    suspend fun clearForProduct(productId: Long)

    @Query("DELETE FROM ProductSupplierCrossRef WHERE supplierId = :supplierId")
    suspend fun clearForSupplier(supplierId: Long)

    @Query("SELECT * FROM ProductSupplierCrossRef")
    suspend fun all(): List<ProductSupplierCrossRef>
}
