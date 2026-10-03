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
        CustomerEntity::class,
        InventoryTransactionEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        PaymentEntity::class,
        InventoryPeriodEntity::class,
        ProductSupplierCrossRef::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun products(): ProductDao
    abstract fun inventory(): InventoryDao
    abstract fun sales(): SalesDao
    abstract fun suppliers(): SupplierDao
    abstract fun customers(): CustomerDao
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS CustomerEntity (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        barcode TEXT NOT NULL,
                        name TEXT NOT NULL,
                        phone TEXT,
                        email TEXT,
                        address TEXT,
                        careOf TEXT,
                        notes TEXT,
                        active INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_CustomerEntity_barcode ON CustomerEntity(barcode)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_CustomerEntity_name ON CustomerEntity(name)"
                )
                db.execSQL("ALTER TABLE SaleEntity ADD COLUMN customerId INTEGER")
                db.execSQL("ALTER TABLE SaleEntity ADD COLUMN customerName TEXT")
                db.execSQL("ALTER TABLE SaleEntity ADD COLUMN careOf TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE CustomerEntity ADD COLUMN identitySource TEXT")
                db.execSQL("ALTER TABLE CustomerEntity ADD COLUMN identityVerified INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE CustomerEntity ADD COLUMN identityVerifiedAt INTEGER")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "pos.db"
            )
.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                .also { instance = it }
        }
    }
}
