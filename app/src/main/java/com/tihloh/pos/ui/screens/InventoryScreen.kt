package com.tihloh.pos.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tihloh.pos.data.PosRepository
import com.tihloh.pos.data.ProductEntity
import com.tihloh.pos.ui.ScanTextField
import com.tihloh.pos.ui.quantity
import kotlinx.coroutines.launch

@Composable
fun InventoryScreen(
    repository: PosRepository,
    scannedProduct: ProductEntity?,
    onScannedProductHandled: () -> Unit,
    onScanRequest: () -> Unit
) {
    val products by repository.products.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<ProductEntity?>(null) }
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(scannedProduct?.id) {
        if (scannedProduct != null) {
            query = scannedProduct.barcode
                ?: scannedProduct.sku
                ?: scannedProduct.name
            onScannedProductHandled()
        }
    }

    val filtered = remember(products, query) {
        products
            .filter { it.inventoryEnabled }
            .filter {
                query.isBlank() ||
                    it.name.contains(query, ignoreCase = true) ||
                    it.barcode.orEmpty().contains(query, ignoreCase = true) ||
                    it.sku.orEmpty().contains(query, ignoreCase = true)
            }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Inventory", style = MaterialTheme.typography.headlineMedium)
        Text("Find stock quickly, then adjust only when needed.")
        ScanTextField(
            value = query,
            onValueChange = { query = it },
            label = "Search product / barcode",
            onScan = onScanRequest
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (filtered.isEmpty()) {
            Text("No matching inventory item.")
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { product ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(product.name, style = MaterialTheme.typography.titleMedium)
                            Text(product.barcode ?: product.sku ?: "No barcode")
                            Text("Stock: ${quantity(product.stockCache)} ${product.unit}")
                        }
                        Button(onClick = { selected = product }) { Text("Adjust") }
                    }
                }
            }
        }
    }

    selected?.let { product ->
        StockDialog(
            product = product,
            onDismiss = { selected = null },
            onApply = { mode, qty, note ->
                scope.launch {
                    val delta = when (mode) {
                        "RECEIVE" -> qty
                        "ADD" -> qty
                        "REMOVE" -> -qty
                        "COUNT" -> qty - product.stockCache
                        else -> 0.0
                    }
                    val type = when (mode) {
                        "RECEIVE" -> "RECEIVING"
                        "COUNT" -> "PHYSICAL_COUNT"
                        else -> "ADJUSTMENT"
                    }
                    runCatching { repository.adjustStock(product.id, delta, type, note) }
                        .onSuccess { selected = null }
                        .onFailure { error = it.message ?: "Inventory update failed." }
                }
            }
        )
    }
}

@Composable
private fun StockDialog(
    product: ProductEntity,
    onDismiss: () -> Unit,
    onApply: (mode: String, quantity: Double, note: String?) -> Unit
) {
    var mode by remember { mutableStateOf("RECEIVE") }
    var qty by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val parsed = qty.toDoubleOrNull()
    val valid = parsed != null && parsed >= 0 && !(mode != "COUNT" && parsed == 0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(product.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Current: ${quantity(product.stockCache)} ${product.unit}")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "RECEIVE" to "Receive",
                        "ADD" to "Add",
                        "REMOVE" to "Remove",
                        "COUNT" to "Count"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = mode == value,
                            onClick = { mode = value },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = qty,
                    onValueChange = { qty = it },
                    label = {
                        Text(if (mode == "COUNT") "Physical count" else "Quantity")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (mode == "COUNT" && parsed != null) {
                    Text("Variance: ${quantity(parsed - product.stockCache)}")
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(mode, parsed ?: 0.0, note.ifBlank { null }) },
                enabled = valid
            ) { Text("Apply") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
