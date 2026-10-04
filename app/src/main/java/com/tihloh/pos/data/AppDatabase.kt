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
        ProductSupplierCrossRef::class,
        CustomerPaymentEntity::class,
        CustomerPaymentAllocationEntity::class,
        AuditLogEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun products(): ProductDao
    abstract fun inventory(): InventoryDao
    abstract fun sales(): SalesDao
    abstract fun suppliers(): SupplierDao
    abstract fun customers(): CustomerDao
    abstract fun productSuppliers(): ProductSupplierDao
    abstract fun customerPayments(): CustomerPaymentDao
    abstract fun auditLogs(): AuditLogDao

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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS CustomerPaymentEntity (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        customerId INTEGER NOT NULL,
                        amountCents INTEGER NOT NULL,
                        paymentType TEXT NOT NULL,
                        reference TEXT,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_CustomerPaymentEntity_customerId ON CustomerPaymentEntity(customerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_CustomerPaymentEntity_createdAt ON CustomerPaymentEntity(createdAt)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS CustomerPaymentAllocationEntity (
                        paymentId INTEGER NOT NULL,
                        saleId INTEGER NOT NULL,
                        amountCents INTEGER NOT NULL,
                        PRIMARY KEY(paymentId, saleId),
                        FOREIGN KEY(paymentId) REFERENCES CustomerPaymentEntity(id) ON DELETE CASCADE,
                        FOREIGN KEY(saleId) REFERENCES SaleEntity(id) ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_CustomerPaymentAllocationEntity_paymentId ON CustomerPaymentAllocationEntity(paymentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_CustomerPaymentAllocationEntity_saleId ON CustomerPaymentAllocationEntity(saleId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS AuditLogEntity (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        action TEXT NOT NULL,
                        entityType TEXT NOT NULL,
                        entityId INTEGER,
                        summary TEXT NOT NULL,
                        metadata TEXT,
                        authMethod TEXT,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_AuditLogEntity_action ON AuditLogEntity(action)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_AuditLogEntity_entityType ON AuditLogEntity(entityType)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_AuditLogEntity_entityId ON AuditLogEntity(entityId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_AuditLogEntity_createdAt ON AuditLogEntity(createdAt)")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "pos.db"
            )
.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
                .also { instance = it }
        }
    }
}
