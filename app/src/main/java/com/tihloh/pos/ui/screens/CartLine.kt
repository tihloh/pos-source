package com.tihloh.pos.ui.screens

import androidx.compose.runtime.snapshots.SnapshotStateList
import com.tihloh.pos.data.ProductEntity

data class CartLine(
    val product: ProductEntity,
    val quantity: Double
)

fun addProductToCart(
    cart: SnapshotStateList<CartLine>,
    product: ProductEntity,
    quantity: Double = 1.0
): String? {
    if (quantity <= 0) return "Invalid quantity."
    val index = cart.indexOfFirst { it.product.id == product.id }
    val current = if (index >= 0) cart[index].quantity else 0.0
    val next = current + quantity

    if (product.inventoryEnabled && next > product.stockCache + 0.000001) {
        return "Insufficient stock for ${product.name}."
    }

    if (index >= 0) cart[index] = cart[index].copy(quantity = next)
    else cart.add(CartLine(product, quantity))
    return null
}
