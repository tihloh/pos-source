package com.tihloh.pos.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM ProductEntity WHERE active = 1 ORDER BY name")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM ProductEntity WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): ProductEntity?

    @Query("SELECT * FROM ProductEntity WHERE active = 1 AND (name LIKE '%' || :query || '%' OR sku LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%') ORDER BY name LIMIT 100")
    suspend fun search(query: String): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(product: ProductEntity): Long
}

@Dao
interface InventoryDao {
    @Insert
    suspend fun add(transaction: InventoryTransactionEntity): Long

    @Query("SELECT * FROM InventoryTransactionEntity WHERE productId = :productId ORDER BY createdAt DESC")
    fun observeProductLedger(productId: Long): Flow<List<InventoryTransactionEntity>>

    @Query("SELECT COALESCE(SUM(quantityDelta), 0) FROM InventoryTransactionEntity WHERE productId = :productId")
    suspend fun balance(productId: Long): Double
}

@Dao
interface SalesDao {
    @Query("SELECT * FROM SaleEntity ORDER BY createdAt DESC LIMIT :limit")
    fun observeLatest(limit: Int = 100): Flow<List<SaleEntity>>

    @Insert
    suspend fun addSale(sale: SaleEntity): Long

    @Insert
    suspend fun addItems(items: List<SaleItemEntity>)

    @Insert
    suspend fun addPayments(payments: List<PaymentEntity>)
}

@Dao
interface SupplierDao {
    @Query("SELECT * FROM SupplierEntity WHERE active = 1 ORDER BY name")
    fun observeAll(): Flow<List<SupplierEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(supplier: SupplierEntity): Long
}
