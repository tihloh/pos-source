package com.tihloh.pos.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.tihloh.pos.data.InventoryTransactionEntity
import com.tihloh.pos.data.PosRepository
import com.tihloh.pos.data.ProductEntity
import com.tihloh.pos.ui.ProductThumbnail
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
    val transactions by repository.inventoryTransactions.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<ProductEntity?>(null) }
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(scannedProduct?.id) {
        if (scannedProduct != null) {
            query = ""
            selected = scannedProduct
            error = null
            onScannedProductHandled()
        }
    }

    val filtered = remember(products, query) {
        products.filter { it.inventoryEnabled }.filter {
            query.isBlank() ||
                it.name.contains(query, ignoreCase = true) ||
                it.barcode.orEmpty().contains(query, ignoreCase = true) ||
                it.sku.orEmpty().contains(query, ignoreCase = true)
        }
    }

    val ledgerByProduct = remember(transactions) { transactions.groupBy { it.productId } }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Inventory", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Beginning + Added − Removed − Sold = Current",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

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

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            items(filtered, key = { it.id }) { product ->
                val ledger = ledgerByProduct[product.id].orEmpty()
                InventoryRow(
                    product = product,
                    ledger = ledger,
                    onAdjust = { selected = product }
                )
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
                        "RECEIVE", "ADD" -> qty
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
private fun InventoryRow(
    product: ProductEntity,
    ledger: List<InventoryTransactionEntity>,
    onAdjust: () -> Unit
) {
    val beginning = ledger.filter { it.type == "OPENING" }.sumOf { it.quantityDelta }
    val added = ledger.filter { it.type != "OPENING" && it.quantityDelta > 0 }
        .sumOf { it.quantityDelta }
    val sold = -ledger.filter { it.type == "SALE" && it.quantityDelta < 0 }
        .sumOf { it.quantityDelta }
    val removed = -ledger.filter { it.type != "SALE" && it.quantityDelta < 0 }
        .sumOf { it.quantityDelta }

    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ProductThumbnail(
                imageUrl = product.imageUrl,
                width = 88.dp
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(product.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            product.barcode ?: product.sku ?: "No barcode",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "${quantity(product.stockCache)} ${product.unit}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedButton(onClick = onAdjust) { Text("Adjust") }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CompactMetric("Beg", quantity(beginning), Modifier.weight(1f))
                    CompactMetric("+Add", quantity(added), Modifier.weight(1f))
                    CompactMetric("-Rem", quantity(removed), Modifier.weight(1f))
                    CompactMetric("-Sold", quantity(sold), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CompactMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
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

    val result = when {
        parsed == null -> product.stockCache
        mode == "COUNT" -> parsed
        mode == "REMOVE" -> product.stockCache - parsed
        else -> product.stockCache + parsed
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust inventory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Current balance: ${quantity(product.stockCache)} ${product.unit}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = mode == "RECEIVE",
                        onClick = { mode = "RECEIVE" },
                        label = { Text("Receive") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = mode == "ADD",
                        onClick = { mode = "ADD" },
                        label = { Text("Add") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = mode == "REMOVE",
                        onClick = { mode = "REMOVE" },
                        label = { Text("Remove") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = mode == "COUNT",
                        onClick = { mode = "COUNT" },
                        label = { Text("Physical count") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = qty,
                    onValueChange = { qty = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text(if (mode == "COUNT") "New physical count" else "Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (parsed != null) {
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (mode == "COUNT") "Variance" else "Ending balance")
                            Text(
                                if (mode == "COUNT") quantity(parsed - product.stockCache)
                                else quantity(result),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(mode, parsed ?: 0.0, note.ifBlank { null }) },
                enabled = valid && result >= -0.000001
            ) { Text("Apply") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
