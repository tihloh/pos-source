package com.tihloh.pos.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.tihloh.pos.data.CustomerEntity
import com.tihloh.pos.data.PosRepository
import com.tihloh.pos.philsys.PhilSysParseResult
import com.tihloh.pos.philsys.PhilSysProfile
import com.tihloh.pos.philsys.PhilSysQrParser
import com.tihloh.pos.ui.ScanTextField
import kotlinx.coroutines.launch

@Composable
fun CustomersScreen(
    repository: PosRepository,
    pendingBarcode: String?,
    onPendingBarcodeHandled: () -> Unit,
    pendingPhilSysQr: String?,
    onPendingPhilSysQrHandled: () -> Unit,
    onScanRequest: () -> Unit,
    onPhilSysScanRequest: () -> Unit,
    onBack: () -> Unit
) {
    val customers by repository.customers.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<CustomerEntity?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPhilSysConsent by remember { mutableStateOf(false) }
    var philSysProfile by remember { mutableStateOf<PhilSysProfile?>(null) }

    LaunchedEffect(pendingBarcode) {
        val code = pendingBarcode ?: return@LaunchedEffect
        val existing = repository.findCustomerByBarcode(code)
        if (existing != null) {
            editing = existing
        } else {
            editing = CustomerEntity(barcode = code.trim(), name = "")
        }
        onPendingBarcodeHandled()
    }


    LaunchedEffect(pendingPhilSysQr) {
        val raw = pendingPhilSysQr ?: return@LaunchedEffect
        when (val parsed = PhilSysQrParser.parse(raw)) {
            is PhilSysParseResult.Success -> {
                philSysProfile = parsed.profile
                error = null
            }
            is PhilSysParseResult.Invalid -> {
                error = parsed.message
            }
        }
        onPendingPhilSysQrHandled()
    }

    val filtered = remember(customers, query) {
        customers.filter {
            query.isBlank() ||
                it.name.contains(query, true) ||
                it.barcode.contains(query, true) ||
                it.phone.orEmpty().contains(query, true) ||
                it.email.orEmpty().contains(query, true)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Text(
                "Customers",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).padding(start = 12.dp)
            )
            OutlinedButton(onClick = { showPhilSysConsent = true }) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Text("PhilID")
            }
            Button(onClick = { editing = CustomerEntity(barcode = "", name = "") }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Add")
            }
        }

        ScanTextField(
            value = query,
            onValueChange = { query = it },
            label = "Search name / barcode / phone",
            onScan = onScanRequest
        )

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (filtered.isEmpty()) {
            Text("No matching customers.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered, key = { it.id }) { customer ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { editing = customer }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Badge, contentDescription = null)
                            Column(Modifier.weight(1f)) {
                                Text(customer.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    customer.barcode,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val detail = listOfNotNull(
                                    customer.phone
                                ).joinToString(" · ")
                                if (detail.isNotBlank()) {
                                    Text(detail, style = MaterialTheme.typography.bodySmall)
                                }
                                if (customer.identitySource == "PHILSYS_QR") {
                                    Text(
                                        if (customer.identityVerified) {
                                            "PhilSys verified"
                                        } else {
                                            "PhilSys QR imported · not cryptographically verified"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (customer.identityVerified) {
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
            }
        }
    }

    if (showPhilSysConsent) {
        AlertDialog(
            onDismissRequest = { showPhilSysConsent = false },
            title = { Text("Scan PhilID / ePhilID") },
            text = {
                Text(
                    "With the customer's consent, POS will read the demographic information " +
                        "needed to prefill a customer account. The raw QR, digital signature, " +
                        "and PhilSys Card Number will not be stored. This import does not by " +
                        "itself cryptographically verify the PhilID."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPhilSysConsent = false
                        onPhilSysScanRequest()
                    }
                ) { Text("Consent given · Scan") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPhilSysConsent = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    philSysProfile?.let { profile ->
        PhilSysReviewDialog(
            profile = profile,
            onDismiss = { philSysProfile = null },
            onUse = {
                editing = CustomerEntity(
                    barcode = "",
                    name = profile.fullName,
                    identitySource = "PHILSYS_QR",
                    identityVerified = false
                )
                philSysProfile = null
            }
        )
    }

    editing?.let { customer ->
        CustomerDialog(
            initial = customer,
            onDismiss = { editing = null },
            onDelete = if (customer.id == 0L) null else {
                {
                    scope.launch {
                        runCatching { repository.archiveCustomer(customer.id) }
                            .onSuccess { editing = null }
                            .onFailure { error = it.message }
                    }
                }
            },
            onSave = { updated ->
                scope.launch {
                    runCatching {
                        require(updated.name.isNotBlank()) { "Customer name is required." }
                        repository.saveCustomer(updated)
                    }.onSuccess {
                        editing = null
                    }.onFailure {
                        error = it.message ?: "Unable to save customer."
                    }
                }
            }
        )
    }
}

@Composable
private fun CustomerDialog(
    initial: CustomerEntity,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (CustomerEntity) -> Unit
) {
    var barcode by remember(initial) { mutableStateOf(initial.barcode) }
    var name by remember(initial) { mutableStateOf(initial.name) }
    var phone by remember(initial) { mutableStateOf(initial.phone.orEmpty()) }
    var email by remember(initial) { mutableStateOf(initial.email.orEmpty()) }
    var address by remember(initial) { mutableStateOf(initial.address.orEmpty()) }
    var notes by remember(initial) { mutableStateOf(initial.notes.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Add customer" else "Edit customer") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text("Customer barcode") },
                        supportingText = { Text("Leave blank to auto-generate.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item { CustomerField("Name *", name) { name = it } }
                item { CustomerField("Phone", phone) { phone = it } }
                item { CustomerField("Email", email) { email = it } }
                item { CustomerField("Address", address) { address = it } }
                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            barcode = barcode,
                            name = name,
                            phone = phone.ifBlank { null },
                            email = email.ifBlank { null },
                            address = address.ifBlank { null },
                            careOf = null,
                            notes = notes.ifBlank { null }
                        )
                    )
                },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    OutlinedButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun CustomerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}


@Composable
private fun PhilSysReviewDialog(
    profile: PhilSysProfile,
    onDismiss: () -> Unit,
    onUse: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review PhilSys details") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    Text(
                        "Detected as a PSA-signed PhilSys QR structure. " +
                            "Digital-signature validity has not been verified in this app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item { Text("Name: ${profile.fullName}") }
                profile.sex?.let { value -> item { Text("Sex: $value") } }
                profile.dateOfBirth?.let { value -> item { Text("Date of birth: $value") } }
                profile.placeOfBirth?.let { value -> item { Text("Place of birth: $value") } }
                profile.dateIssued?.let { value -> item { Text("Date issued: $value") } }
                item {
                    Text(
                        "PCN, raw QR payload, and digital signature are intentionally not retained.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onUse) { Text("Use for customer") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
