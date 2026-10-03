package com.tihloh.pos.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tihloh.pos.data.PosRepository
import com.tihloh.pos.data.ProductEntity
import com.tihloh.pos.data.SupplierEntity
import com.tihloh.pos.product.ProductImageStore
import com.tihloh.pos.product.ProductLookupOutcome
import com.tihloh.pos.product.ProductLookupService
import com.tihloh.pos.ui.NetworkImage
import com.tihloh.pos.ui.ProductThumbnail
import com.tihloh.pos.ui.ScanTextField
import com.tihloh.pos.ui.money
import com.tihloh.pos.ui.parseMoneyToCents
import com.tihloh.pos.ui.quantity
import kotlinx.coroutines.launch

private data class ProductDraft(
    val id: Long = 0,
    val barcode: String = "",
    val sku: String = "",
    val name: String = "",
    val description: String = "",
    val brand: String = "",
    val category: String = "",
    val imageUrl: String = "",
    val itemType: String = "GOOD",
    val inventoryEnabled: Boolean = true,
    val cost: String = "",
    val price: String = "",
    val unit: String = "pc",
    val openingStock: String = "",
    val stockCache: Double = 0.0,
    val supplierIds: List<Long> = emptyList()
)

@Composable
fun ProductsScreen(
    repository: PosRepository,
    pendingBarcode: String?,
    onPendingBarcodeHandled: () -> Unit,
    onScanRequest: () -> Unit
) {
    val products by repository.products.collectAsState(initial = emptyList())
    val suppliers by repository.suppliers.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val lookup = remember { ProductLookupService() }
    var search by remember { mutableStateOf("") }
    var editor by remember { mutableStateOf<ProductDraft?>(null) }
    var loadingLookup by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lookupStatus by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingBarcode) {
        val barcode = pendingBarcode ?: return@LaunchedEffect
        loadingLookup = true
        error = null
        lookupStatus = null
        try {
            val currentDraft = editor
            val existing = repository.findByBarcode(barcode)
            if (existing != null && currentDraft == null) {
                editor = existing.toDraft(repository.supplierIdsForProduct(existing.id))
                lookupStatus = "Product already exists locally."
            } else if (existing != null) {
                editor = currentDraft!!.copy(barcode = barcode)
                lookupStatus = "Barcode belongs to an existing local product."
            } else {
                when (val outcome = lookup.lookup(barcode)) {
                    is ProductLookupOutcome.Found -> {
                        val found = outcome.product
                        editor = if (currentDraft == null) {
                            ProductDraft(
                                barcode = found.barcode,
                                name = found.name,
                                description = found.description.orEmpty(),
                                brand = found.brand.orEmpty(),
                                category = found.category.orEmpty(),
                                imageUrl = found.imageUrl.orEmpty()
                            )
                        } else {
                            currentDraft.copy(
                                barcode = found.barcode,
                                name = currentDraft.name.ifBlank { found.name },
                                description = currentDraft.description.ifBlank {
                                    found.description.orEmpty()
                                },
                                brand = currentDraft.brand.ifBlank { found.brand.orEmpty() },
                                category = currentDraft.category.ifBlank {
                                    found.category.orEmpty()
                                },
                                imageUrl = currentDraft.imageUrl.ifBlank {
                                    found.imageUrl.orEmpty()
                                }
                            )
                        }
                        lookupStatus = "Loaded from " + found.source +
                            (found.quantity?.let { " · " + it } ?: "")
                    }
                    is ProductLookupOutcome.NotFound -> {
                        editor = currentDraft?.copy(barcode = barcode)
                            ?: ProductDraft(barcode = barcode)
                        error = "Barcode is valid, but it is not in the Open Facts databases yet."
                    }
                    is ProductLookupOutcome.Failed -> {
                        editor = currentDraft?.copy(barcode = barcode)
                            ?: ProductDraft(barcode = barcode)
                        error = outcome.message
                    }
                }
            }
        } finally {
            loadingLookup = false
            onPendingBarcodeHandled()
        }
    }

    val filtered = remember(products, search) {
        if (search.isBlank()) products
        else products.filter {
            it.name.contains(search, true) ||
                it.barcode.orEmpty().contains(search, true) ||
                it.sku.orEmpty().contains(search, true)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Products",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { editor = ProductDraft() }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Add")
            }
        }

        ScanTextField(
            value = search,
            onValueChange = { search = it },
            label = "Search name / barcode / SKU",
            onScan = onScanRequest
        )

        lookupStatus?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (loadingLookup) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Text("Looking up barcode…")
            }
        }

        if (filtered.isEmpty() && !loadingLookup) {
            Text("No products yet. Tap Add or scan a barcode.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { product ->
                    ProductRow(
                        product = product,
                        onEdit = {
                            scope.launch {
                                editor = product.toDraft(repository.supplierIdsForProduct(product.id))
                            }
                        },
                        onArchive = {
                            scope.launch {
                                runCatching { repository.archiveProduct(product.id) }
                                    .onFailure { error = it.message }
                            }
                        }
                    )
                }
            }
        }
    }

    editor?.let { draft ->
        ProductEditorDialog(
            initial = draft,
            onDismiss = { editor = null },
            onScanRequest = onScanRequest,
            suppliers = suppliers,
            onSave = { updated ->
                error = null
                scope.launch {
                    val result = runCatching {
                        val price = parseMoneyToCents(updated.price) ?: 0L
                        val cost = parseMoneyToCents(updated.cost) ?: 0L
                        require(updated.name.isNotBlank()) { "Product name is required." }
                        require(price >= 0 && cost >= 0) { "Price cannot be negative." }

                        repository.saveProduct(
                            ProductEntity(
                                id = updated.id,
                                barcode = updated.barcode.ifBlank { null },
                                sku = updated.sku.ifBlank { null },
                                name = updated.name,
                                description = updated.description.ifBlank { null },
                                brand = updated.brand.ifBlank { null },
                                category = updated.category.ifBlank { null },
                                imageUrl = updated.imageUrl.ifBlank { null },
                                itemType = updated.itemType,
                                inventoryEnabled = updated.inventoryEnabled,
                                costCents = cost,
                                sellingPriceCents = price,
                                unit = updated.unit.ifBlank { "pc" },
                                stockCache = updated.stockCache
                            ),
                            openingQuantity = updated.openingStock.toDoubleOrNull() ?: 0.0,
                            supplierIds = updated.supplierIds
                        )
                    }
                    result.onSuccess { editor = null }
                        .onFailure { error = it.message ?: "Unable to save product." }
                }
            }
        )
    }
}

@Composable
private fun ProductRow(
    product: ProductEntity,
    onEdit: () -> Unit,
    onArchive: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProductThumbnail(
                imageUrl = product.imageUrl,
                width = 96.dp
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(product.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(product.barcode, product.sku)
                                .joinToString(" · ")
                                .ifBlank { "No barcode" },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        money(product.sellingPriceCents),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                val stockText = if (product.inventoryEnabled) {
                    "Stock: ${quantity(product.stockCache)} ${product.unit}"
                } else "Non-inventory item"
                Text("$stockText · ${product.itemType}")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onArchive) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Archive")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductEditorDialog(
    initial: ProductDraft,
    onDismiss: () -> Unit,
    onScanRequest: () -> Unit,
    suppliers: List<SupplierEntity>,
    onSave: (ProductDraft) -> Unit
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    val isNew = initial.id == 0L
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            runCatching { ProductImageStore.save(context, uri) }
                .onSuccess { draft = draft.copy(imageUrl = it) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add product" else "Edit product") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = draft.itemType == "GOOD",
                            onClick = { draft = draft.copy(itemType = "GOOD", inventoryEnabled = true) },
                            label = { Text("Goods") }
                        )
                        FilterChip(
                            selected = draft.itemType == "SERVICE",
                            onClick = { draft = draft.copy(itemType = "SERVICE", inventoryEnabled = false) },
                            label = { Text("Service") }
                        )
                    }
                }
                item {
                    ScanTextField(
                        value = draft.barcode,
                        onValueChange = { draft = draft.copy(barcode = it) },
                        label = "Barcode",
                        onScan = onScanRequest
                    )
                }
                item { Field("SKU", draft.sku) { draft = draft.copy(sku = it) } }
                item { Field("Name *", draft.name) { draft = draft.copy(name = it) } }
                item { Field("Brand", draft.brand) { draft = draft.copy(brand = it) } }
                item { Field("Category", draft.category) { draft = draft.copy(category = it) } }
                item { Field("Description", draft.description) { draft = draft.copy(description = it) } }
                item {
                    Field("Selling price", draft.price, KeyboardType.Decimal) {
                        draft = draft.copy(price = it)
                    }
                }
                item {
                    Field("Cost price", draft.cost, KeyboardType.Decimal) {
                        draft = draft.copy(cost = it)
                    }
                }
                item { Field("Unit", draft.unit) { draft = draft.copy(unit = it) } }
                if (suppliers.isNotEmpty()) {
                    item {
                        Text("Suppliers", style = MaterialTheme.typography.titleSmall)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            suppliers.forEach { supplier ->
                                FilterChip(
                                    selected = supplier.id in draft.supplierIds,
                                    onClick = {
                                        draft = draft.copy(
                                            supplierIds = if (supplier.id in draft.supplierIds) {
                                                draft.supplierIds - supplier.id
                                            } else {
                                                draft.supplierIds + supplier.id
                                            }
                                        )
                                    },
                                    label = { Text(supplier.name) }
                                )
                            }
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = draft.inventoryEnabled,
                            onCheckedChange = { draft = draft.copy(inventoryEnabled = it) }
                        )
                        Text("Track inventory")
                    }
                }
                if (isNew && draft.inventoryEnabled) {
                    item {
                        Field("Opening stock", draft.openingStock, KeyboardType.Decimal) {
                            draft = draft.copy(openingStock = it)
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Product image",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = { imagePicker.launch("image/*") }) {
                            Text(if (draft.imageUrl.isBlank()) "Choose image" else "Change")
                        }
                    }
                }
                if (draft.imageUrl.isNotBlank()) {
                    item {
                        NetworkImage(
                            url = draft.imageUrl,
                            modifier = Modifier.fillMaxWidth().height(150.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(draft) }, enabled = draft.name.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun Field(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun ProductEntity.toDraft(supplierIds: List<Long> = emptyList()) = ProductDraft(
    id = id,
    barcode = barcode.orEmpty(),
    sku = sku.orEmpty(),
    name = name,
    description = description.orEmpty(),
    brand = brand.orEmpty(),
    category = category.orEmpty(),
    imageUrl = imageUrl.orEmpty(),
    itemType = itemType,
    inventoryEnabled = inventoryEnabled,
    cost = if (costCents == 0L) "" else (costCents / 100.0).toString(),
    price = if (sellingPriceCents == 0L) "" else (sellingPriceCents / 100.0).toString(),
    unit = unit,
    stockCache = stockCache,
    supplierIds = supplierIds
)
