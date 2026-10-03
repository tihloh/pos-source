package com.tihloh.pos.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProductEntity::class,
        SupplierEntity::class,
        InventoryTransactionEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        PaymentEntity::class,
        InventoryPeriodEntity::class,
        ProductSupplierCrossRef::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun products(): ProductDao
    abstract fun inventory(): InventoryDao
    abstract fun sales(): SalesDao
    abstract fun suppliers(): SupplierDao
    abstract fun productSuppliers(): ProductSupplierDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ProductSupplierCrossRef (
                        productId INTEGER NOT NULL,
                        supplierId INTEGER NOT NULL,
                        isPrimary INTEGER NOT NULL DEFAULT 0,
                        supplierCostCents INTEGER,
                        supplierSku TEXT,
                        PRIMARY KEY(productId, supplierId),
                        FOREIGN KEY(productId) REFERENCES ProductEntity(id) ON DELETE CASCADE,
                        FOREIGN KEY(supplierId) REFERENCES SupplierEntity(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_ProductSupplierCrossRef_productId ON ProductSupplierCrossRef(productId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_ProductSupplierCrossRef_supplierId ON ProductSupplierCrossRef(supplierId)"
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO ProductSupplierCrossRef(productId, supplierId, isPrimary)
                    SELECT id, supplierId, 1 FROM ProductEntity
                    WHERE supplierId IS NOT NULL
                    """.trimIndent()
                )
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "pos.db"
            )
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
