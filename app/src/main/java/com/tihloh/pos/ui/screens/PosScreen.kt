package com.tihloh.pos.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tihloh.pos.data.CustomerEntity
import com.tihloh.pos.data.PosRepository
import com.tihloh.pos.data.ProductEntity
import com.tihloh.pos.data.SaleDetail
import com.tihloh.pos.data.SaleLineInput
import com.tihloh.pos.printer.ReceiptPrinter
import com.tihloh.pos.printer.toReceiptData
import com.tihloh.pos.ui.ProductThumbnail
import com.tihloh.pos.ui.ScanTextField
import com.tihloh.pos.ui.money
import com.tihloh.pos.ui.parseMoneyToCents
import com.tihloh.pos.ui.quantity
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

@Composable
fun PosScreen(
    repository: PosRepository,
    cart: SnapshotStateList<CartLine>,
    scannedProduct: ProductEntity?,
    onScannedProductHandled: () -> Unit,
    scannedCustomer: CustomerEntity?,
    onScannedCustomerHandled: () -> Unit,
    onProductScanRequest: () -> Unit,
    onCustomerScanRequest: () -> Unit
) {
    val products by repository.products.collectAsState(initial = emptyList())
    val customers by repository.customers.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checkout by remember { mutableStateOf(false) }
    var completedSale by remember { mutableStateOf<SaleDetail?>(null) }
    var quantityProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var selectedCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerPicker by remember { mutableStateOf(false) }

    LaunchedEffect(scannedProduct?.id) {
        val product = scannedProduct ?: return@LaunchedEffect
        quantityProduct = product
        onScannedProductHandled()
    }

    LaunchedEffect(scannedCustomer?.id) {
        val customer = scannedCustomer ?: return@LaunchedEffect
        selectedCustomer = customer
        customerPicker = false
        onScannedCustomerHandled()
    }

    val total = cart.sumOf { (it.product.sellingPriceCents * it.quantity).roundToLong() }
    val filtered = remember(products, query) {
        if (query.isBlank()) emptyList()
        else products.filter {
            it.name.contains(query, true) ||
                it.barcode.orEmpty().contains(query, true) ||
                it.sku.orEmpty().contains(query, true)
        }.take(20)
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.PointOfSale,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text("POS", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Scan, sell, and checkout quickly",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        ScanTextField(
            value = query,
            onValueChange = { query = it },
            label = "Search product / barcode",
            onScan = onProductScanRequest
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = { customerPicker = true }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Customer", style = MaterialTheme.typography.labelMedium)
                    Text(
                        selectedCustomer?.name ?: "Walk-in customer",
                        style = MaterialTheme.typography.titleSmall
                    )
                    selectedCustomer?.careOf?.let {
                        Text(
                            "Care of: $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    if (selectedCustomer == null) "Select" else "Change",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }


        if (query.isNotBlank()) {
            LazyColumn(
                modifier = Modifier.weight(0.35f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered, key = { it.id }) { product ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ProductThumbnail(
                                imageUrl = product.imageUrl,
                                width = 92.dp
                            )
                            Column(Modifier.weight(1f)) {
                                Text(product.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${money(product.sellingPriceCents)} · " +
                                        if (product.inventoryEnabled) "Stock ${quantity(product.stockCache)}"
                                        else "Non-stock"
                                )
                            }
                            Button(
                                onClick = {
                                    quantityProduct = product
                                    query = ""
                                },
                                enabled = !product.inventoryEnabled || product.stockCache > 0
                            ) { Text("Add") }
                        }
                    }
                }
            }
        }

        Text("Cart", style = MaterialTheme.typography.titleLarge)
        if (cart.isEmpty()) {
            Card(Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    Modifier.fillMaxSize().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Cart is empty")
                    Text("Use the scanner button or search above.")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(cart, key = { it.product.id }) { line ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp)
                    ) {
                        Row(
                            Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ProductThumbnail(
                                imageUrl = line.product.imageUrl,
                                width = 92.dp
                            )
                            Column(Modifier.weight(1f)) {
                                Text(line.product.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${quantity(line.quantity)} × ${money(line.product.sellingPriceCents)} = " +
                                        money((line.product.sellingPriceCents * line.quantity).roundToLong())
                                )
                            }
                            IconButton(onClick = {
                                val index = cart.indexOfFirst { it.product.id == line.product.id }
                                if (index >= 0) {
                                    val next = cart[index].quantity - 1
                                    if (next <= 0) cart.removeAt(index)
                                    else cart[index] = cart[index].copy(quantity = next)
                                }
                            }) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease")
                            }
                            Text(
                                quantity(line.quantity),
                                style = MaterialTheme.typography.titleSmall
                            )
                            IconButton(onClick = {
                                error = addProductToCart(cart, line.product)
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Increase")
                            }
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            Column(Modifier.weight(1f)) {
                Text("TOTAL", style = MaterialTheme.typography.labelLarge)
                Text(money(total), style = MaterialTheme.typography.headlineSmall)
            }
                Button(
                    onClick = { checkout = true },
                    enabled = cart.isNotEmpty() && total >= 0
                ) {
                    Text("Checkout")
                }
            }
        }
    }

    quantityProduct?.let { product ->
        QuantityDialog(
            product = product,
            onDismiss = { quantityProduct = null },
            onAdd = { qty ->
                error = addProductToCart(cart, product, qty)
                if (error == null) {
                    quantityProduct = null
                }
            }
        )
    }

    if (checkout) {
        CheckoutDialog(
            totalCents = total,
            customer = selectedCustomer,
            onDismiss = { checkout = false },
            onCheckout = { paymentType, amountPaid, reference ->
                scope.launch {
                    runCatching {
                        repository.checkout(
                            lines = cart.map { SaleLineInput(it.product.id, it.quantity) },
                            paymentType = paymentType,
                            amountPaidCents = amountPaid,
                            paymentReference = reference,
                            customerId = selectedCustomer?.id
                        )
                    }.onSuccess {
                        completedSale = it
                        cart.clear()
                        selectedCustomer = null
                        checkout = false
                    }.onFailure {
                        error = it.message ?: "Checkout failed."
                    }
                }
            }
        )
    }

    if (customerPicker) {
        CustomerPickerDialog(
            customers = customers,
            selected = selectedCustomer,
            onDismiss = { customerPicker = false },
            onScan = onCustomerScanRequest,
            onSelect = {
                selectedCustomer = it
                customerPicker = false
            },
            onWalkIn = {
                selectedCustomer = null
                customerPicker = false
            }
        )
    }

    completedSale?.let { detail ->
        AlertDialog(
            onDismissRequest = { completedSale = null },
            title = { Text("Sale completed") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(detail.sale.receiptNumber)
                    detail.items.forEach {
                        Text("${it.productName} · ${quantity(it.quantity)} × ${money(it.unitPriceCents)}")
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Total: ${money(detail.sale.totalCents)}")
                    Text("Paid: ${money(detail.sale.amountPaidCents)}")
                    if (detail.sale.status == "ACCOUNT_PAYABLE") {
                        Text(
                            "Account Payable: " +
                                money(detail.sale.totalCents - detail.sale.amountPaidCents)
                        )
                    } else {
                        Text("Change: ${money(detail.sale.changeCents)}")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        ReceiptPrinter(context)
                            .print(detail.toReceiptData())
                            .onSuccess {
                                Toast.makeText(context, "Receipt printed.", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    it.message ?: "Printing failed.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    }
                }) { Text("Print") }
            },
            dismissButton = {
                TextButton(onClick = { completedSale = null }) { Text("Done") }
            }
        )
    }
}

@Composable
private fun QuantityDialog(
    product: ProductEntity,
    onDismiss: () -> Unit,
    onAdd: (Double) -> Unit
) {
    var amount by remember(product.id) { mutableStateOf("1") }
    val parsed = amount.toDoubleOrNull()
    val valid = parsed != null &&
        parsed > 0 &&
        (!product.inventoryEnabled || parsed <= product.stockCache + 0.000001)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(product.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Price: " + money(product.sellingPriceCents))
                if (product.inventoryEnabled) {
                    Text("Available: " + quantity(product.stockCache) + " " + product.unit)
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { value ->
                        amount = value.filter { it.isDigit() || it == '.' }
                    },
                    label = { Text("Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (parsed != null && parsed > 0) {
                    Text(
                        "Line total: " +
                            money((product.sellingPriceCents * parsed).roundToLong())
                    )
                }
                if (parsed != null && product.inventoryEnabled && parsed > product.stockCache) {
                    Text(
                        "Only " + quantity(product.stockCache) + " available.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(parsed ?: 1.0) },
                enabled = valid
            ) { Text("Add to cart") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CheckoutDialog(
    totalCents: Long,
    customer: CustomerEntity?,
    onDismiss: () -> Unit,
    onCheckout: (
        paymentType: String,
        amountPaidCents: Long,
        reference: String?
    ) -> Unit
) {
    var paymentType by remember { mutableStateOf("Cash") }
    var amount by remember(totalCents) { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    val isAccountPayable = paymentType == "Account Payable"
    val paid = if (isAccountPayable) 0L else parseMoneyToCents(amount)
    val valid = if (isAccountPayable) {
        customer != null
    } else {
        paid != null && paid >= totalCents
    }
    val change = if (!isAccountPayable && paid != null && paid >= totalCents) {
        paid - totalCents
    } else {
        0L
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("Amount due", style = MaterialTheme.typography.labelLarge)
                        Text(
                            money(totalCents),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (customer != null) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("Customer", style = MaterialTheme.typography.labelMedium)
                            Text(customer.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                customer.barcode,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Text("Payment method", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Cash", "GCash").forEach { type ->
                        FilterChip(
                            selected = paymentType == type,
                            onClick = { paymentType = type },
                            label = { Text(type) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Maya", "Card", "Account Payable").forEach { type ->
                        FilterChip(
                            selected = paymentType == type,
                            onClick = { paymentType = type },
                            label = { Text(type) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (isAccountPayable) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("Account Payable", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (customer == null) {
                                    "Select a registered customer before saving this sale."
                                } else {
                                    "The full amount will be recorded as unpaid for ${customer.name}."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (customer == null) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Text(
                                "Balance due: ${money(totalCents)}",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Payment amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { amount = "%.2f".format(totalCents / 100.0) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Exact") }
                        if (paymentType == "Cash") {
                            OutlinedButton(
                                onClick = {
                                    val current = parseMoneyToCents(amount) ?: 0L
                                    amount = "%.2f".format((current + 10000) / 100.0)
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("+ ₱100") }
                        }
                    }
                }

                if (paymentType != "Cash" && !isAccountPayable) {
                    OutlinedTextField(
                        value = reference,
                        onValueChange = { reference = it },
                        label = { Text("Reference (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (!isAccountPayable) {
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Change")
                            Text(
                                money(change),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCheckout(
                        paymentType,
                        paid ?: 0L,
                        reference.ifBlank { null }
                    )
                },
                enabled = valid
            ) { Text(if (isAccountPayable) "Save to Account" else "Pay & Save") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}


@Composable
private fun CustomerPickerDialog(
    customers: List<CustomerEntity>,
    selected: CustomerEntity?,
    onDismiss: () -> Unit,
    onScan: () -> Unit,
    onSelect: (CustomerEntity) -> Unit,
    onWalkIn: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(customers, query) {
        customers.filter {
            query.isBlank() ||
                it.name.contains(query, true) ||
                it.barcode.contains(query, true) ||
                it.phone.orEmpty().contains(query, true)
        }.take(50)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select customer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ScanTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "Name / barcode / phone",
                    onScan = onScan
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.id }) { customer ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onSelect(customer) }
                        ) {
                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                Text(customer.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    customer.barcode,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (selected?.id == customer.id) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onWalkIn) { Text("Walk-in") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
