package com.tihloh.pos.sync

import com.tihloh.pos.data.CustomerEntity
import com.tihloh.pos.data.ProductEntity
import com.tihloh.pos.data.SaleEntity
import com.tihloh.pos.data.SupplierEntity
import com.tihloh.pos.product.ProductImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

data class SyncSnapshot(
    val products: List<ProductEntity>,
    val suppliers: List<SupplierEntity>,
    val customers: List<CustomerEntity>,
    val sales: List<SaleEntity>
)

class CentralSyncClient(private val config: SyncConfig) {
    suspend fun push(snapshot: SyncSnapshot): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(config.enabled) { "Central sync is disabled." }
            require(config.baseUrl.isNotBlank()) { "Central server URL is required." }

            val connection = (
                URL(config.baseUrl.trimEnd('/') + "/api/v1/sync").openConnection()
                    as HttpURLConnection
            ).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                if (config.apiToken.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${config.apiToken}")
                }
            }

            val body = JSONObject().apply {
                put("deviceTime", System.currentTimeMillis())
                put("products", JSONArray(snapshot.products.map { p ->
                    JSONObject().apply {
                        put("id", p.id)
                        put("barcode", p.barcode)
                        put("sku", p.sku)
                        put("name", p.name)
                        put("brand", p.brand)
                        put("category", p.category)
                        put("itemType", p.itemType)
                        put("inventoryEnabled", p.inventoryEnabled)
                        put("costCents", p.costCents)
                        put("sellingPriceCents", p.sellingPriceCents)
                        put("unit", p.unit)
                        put("stock", p.stockCache)
                        put("active", p.active)
                        put("updatedAt", p.updatedAt)
                        put("imageUrl", p.imageUrl)
                        ProductImageStore.fileFromUrl(p.imageUrl)?.let { file ->
                            if (file.length() in 1..3_000_000) {
                                put("imageMime", "image/jpeg")
                                put(
                                    "imageBase64",
                                    Base64.getEncoder().encodeToString(file.readBytes())
                                )
                            }
                        }
                    }
                }))
                put("suppliers", JSONArray(snapshot.suppliers.map { s ->
                    JSONObject().apply {
                        put("id", s.id)
                        put("name", s.name)
                        put("contactPerson", s.contactPerson)
                        put("phone", s.phone)
                        put("email", s.email)
                        put("address", s.address)
                        put("active", s.active)
                    }
                }))
                put("customers", JSONArray(snapshot.customers.map { c ->
                    JSONObject().apply {
                        put("id", c.id)
                        put("barcode", c.barcode)
                        put("name", c.name)
                        put("phone", c.phone)
                        put("email", c.email)
                        put("address", c.address)
                        put("careOf", c.careOf)
                        put("notes", c.notes)
                        put("identitySource", c.identitySource)
                        put("identityVerified", c.identityVerified)
                        put("identityVerifiedAt", c.identityVerifiedAt)
                        put("active", c.active)
                        put("createdAt", c.createdAt)
                        put("updatedAt", c.updatedAt)
                    }
                }))
                put("sales", JSONArray(snapshot.sales.map { s ->
                    JSONObject().apply {
                        put("id", s.id)
                        put("receiptNumber", s.receiptNumber)
                        put("customerId", s.customerId)
                        put("customerName", s.customerName)
                        put("careOf", s.careOf)
                        put("subtotalCents", s.subtotalCents)
                        put("discountCents", s.discountCents)
                        put("totalCents", s.totalCents)
                        put("amountPaidCents", s.amountPaidCents)
                        put("changeCents", s.changeCents)
                        put("status", s.status)
                        put("createdAt", s.createdAt)
                    }
                }))
            }.toString()

            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            connection.disconnect()
            require(code in 200..299) { "Central sync failed with HTTP $code." }
        }
    }
}
