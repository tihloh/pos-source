package com.tihloh.pos.product

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ProductLookupResult(
    val barcode: String,
    val name: String,
    val brand: String?,
    val category: String?,
    val quantity: String?,
    val description: String?,
    val imageUrl: String?,
    val source: String
)

class ProductLookupService {
    suspend fun lookup(barcode: String): ProductLookupResult? = withContext(Dispatchers.IO) {
        lookupAt("https://world.openfoodfacts.org/api/v2/product/$barcode.json", barcode, "Open Food Facts")
            ?: lookupAt("https://world.openproductsfacts.org/api/v2/product/$barcode.json", barcode, "Open Products Facts")
    }

    private fun lookupAt(endpoint: String, barcode: String, source: String): ProductLookupResult? {
        return try {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = 7_000
                readTimeout = 7_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "POS Android - product lookup")
            }
            try {
                if (connection.responseCode !in 200..299) return null
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                if (json.optInt("status", 0) != 1) return null
                val product = json.optJSONObject("product") ?: return null
                val name = product.optString("product_name").ifBlank {
                    product.optString("generic_name")
                }
                if (name.isBlank()) return null

                ProductLookupResult(
                    barcode = barcode,
                    name = name,
                    brand = product.optString("brands").takeIf(String::isNotBlank),
                    category = product.optString("categories").takeIf(String::isNotBlank),
                    quantity = product.optString("quantity").takeIf(String::isNotBlank),
                    description = product.optString("generic_name").takeIf(String::isNotBlank),
                    imageUrl = product.optString("image_front_url").takeIf(String::isNotBlank),
                    source = source
                )
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
}
