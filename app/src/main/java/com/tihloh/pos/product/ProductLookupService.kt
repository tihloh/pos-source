package com.tihloh.pos.product

import com.tihloh.pos.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class ProductLookupResult(
    val barcode: String,
    val name: String,
    val brand: String?,
    val category: String?,
    val quantity: String?,
    val description: String?,
    val imageUrl: String?,
    val source: String,
    val productType: String?
)

sealed interface ProductLookupOutcome {
    data class Found(val product: ProductLookupResult) : ProductLookupOutcome
    data class NotFound(val barcode: String) : ProductLookupOutcome
    data class Failed(val barcode: String, val message: String) : ProductLookupOutcome
}

class ProductLookupService {

    suspend fun lookup(rawBarcode: String): ProductLookupOutcome = withContext(Dispatchers.IO) {
        val barcode = rawBarcode.trim()
        if (barcode.isBlank()) {
            return@withContext ProductLookupOutcome.Failed(barcode, "Barcode is empty.")
        }

        val encoded = URLEncoder.encode(barcode, StandardCharsets.UTF_8.toString())
        val fields = listOf(
            "code",
            "product_type",
            "product_name",
            "abbreviated_product_name",
            "generic_name",
            "brands",
            "categories",
            "quantity",
            "image_front_url",
            "image_url"
        ).joinToString(",")

        val endpoint =
            "https://world.openfoodfacts.org/api/v2/product/$encoded.json" +
                "?product_type=all&fields=$fields"

        lookupUniversal(endpoint, barcode)
    }

    private fun lookupUniversal(
        endpoint: String,
        barcode: String
    ): ProductLookupOutcome {
        var connection: HttpURLConnection? = null

        return try {
            connection = (URI(endpoint).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty(
                    "User-Agent",
                    "POSAndroid/${BuildConfig.VERSION_NAME} (https://github.com/tihloh/pos)"
                )
            }

            val httpCode = connection.responseCode
            val stream = if (httpCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (body.isBlank()) {
                return if (httpCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    ProductLookupOutcome.NotFound(barcode)
                } else {
                    ProductLookupOutcome.Failed(
                        barcode,
                        "Product lookup returned HTTP $httpCode with no response."
                    )
                }
            }

            val json = JSONObject(body)
            val status = json.optInt("status", 0)
            val product = json.optJSONObject("product")

            if (status != 1 || product == null) {
                return if (httpCode == HttpURLConnection.HTTP_NOT_FOUND || status == 0) {
                    ProductLookupOutcome.NotFound(barcode)
                } else {
                    ProductLookupOutcome.Failed(
                        barcode,
                        json.optString("status_verbose").ifBlank {
                            "Product lookup failed with HTTP $httpCode."
                        }
                    )
                }
            }

            val name = firstNonBlank(
                product.optString("product_name"),
                product.optString("abbreviated_product_name"),
                product.optString("generic_name"),
                product.optString("brands")
            ) ?: "Barcode $barcode"

            val productType = product.optString("product_type").takeIf { it.isNotBlank() }
            val source = when (productType) {
                "food" -> "Open Food Facts"
                "beauty" -> "Open Beauty Facts"
                "petfood" -> "Open Pet Food Facts"
                "product" -> "Open Products Facts"
                else -> sourceFromHost(connection.url.host)
            }

            val quantity = product.optString("quantity").takeIf { it.isNotBlank() }
            val genericName = product.optString("generic_name").takeIf { it.isNotBlank() }
            val description = listOfNotNull(genericName, quantity)
                .distinct()
                .filter { it != name }
                .joinToString(" · ")
                .ifBlank { null }

            ProductLookupOutcome.Found(
                ProductLookupResult(
                    barcode = product.optString("code").ifBlank { barcode },
                    name = name,
                    brand = product.optString("brands").takeIf { it.isNotBlank() },
                    category = product.optString("categories").takeIf { it.isNotBlank() },
                    quantity = quantity,
                    description = description,
                    imageUrl = firstNonBlank(
                        product.optString("image_front_url"),
                        product.optString("image_url")
                    ),
                    source = source,
                    productType = productType
                )
            )
        } catch (_: UnknownHostException) {
            ProductLookupOutcome.Failed(
                barcode,
                "Cannot reach Open Facts. Check the device internet connection."
            )
        } catch (_: SocketTimeoutException) {
            ProductLookupOutcome.Failed(
                barcode,
                "Open Facts lookup timed out. Please try again."
            )
        } catch (e: Exception) {
            ProductLookupOutcome.Failed(
                barcode,
                "Open Facts lookup failed: ${e.message ?: e.javaClass.simpleName}"
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun firstNonBlank(vararg values: String): String? =
        values.firstOrNull { it.isNotBlank() }

    private fun sourceFromHost(host: String): String = when {
        host.contains("openproductsfacts", ignoreCase = true) -> "Open Products Facts"
        host.contains("openbeautyfacts", ignoreCase = true) -> "Open Beauty Facts"
        host.contains("openpetfoodfacts", ignoreCase = true) -> "Open Pet Food Facts"
        else -> "Open Food Facts"
    }
}
