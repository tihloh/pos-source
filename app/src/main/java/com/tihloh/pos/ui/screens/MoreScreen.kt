package com.tihloh.pos.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tihloh.pos.data.PosRepository
import com.tihloh.pos.data.SupplierEntity
import kotlinx.coroutines.launch

@Composable
fun MoreScreen(
    repository: PosRepository,
    checkUpdate: suspend () -> Unit
) {
    val suppliers by repository.suppliers.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<SupplierEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("More", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Button(onClick = { adding = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(" Supplier")
            }
        }

        Button(
            onClick = { checking = true },
            enabled = !checking,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (checking) "Checking for update…" else "Check for app update")
        }

        message?.let { Text(it) }

        Text("Suppliers", style = MaterialTheme.typography.titleLarge)
        if (suppliers.isEmpty()) {
            Text("No suppliers yet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(suppliers, key = { it.id }) { supplier ->
                    Card(
                        Modifier.fillMaxWidth().clickable { editing = supplier }
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(supplier.name, style = MaterialTheme.typography.titleMedium)
                                supplier.contactPerson?.let { Text(it) }
                                supplier.phone?.let { Text(it) }
                            }
                            IconButton(onClick = { editing = supplier }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit supplier")
                            }
                        }
                    }
                }
            }
        }
    }

    if (checking) {
        LaunchedEffect(Unit) {
            checkUpdate()
            message = "Update check completed."
            checking = false
        }
    }

    if (adding) {
        SupplierDialog(
            initial = SupplierEntity(name = ""),
            onDismiss = { adding = false },
            onSave = { supplier ->
                scope.launch {
                    runCatching { repository.saveSupplier(supplier) }
                        .onSuccess { adding = false }
                        .onFailure { message = it.message }
                }
            }
        )
    }

    editing?.let { supplier ->
        SupplierDialog(
            initial = supplier,
            onDismiss = { editing = null },
            onSave = { updated ->
                scope.launch {
                    runCatching { repository.saveSupplier(updated) }
                        .onSuccess { editing = null }
                        .onFailure { message = it.message }
                }
            }
        )
    }
}

@Composable
private fun SupplierDialog(
    initial: SupplierEntity,
    onDismiss: () -> Unit,
    onSave: (SupplierEntity) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial.name) }
    var contact by remember(initial) { mutableStateOf(initial.contactPerson.orEmpty()) }
    var phone by remember(initial) { mutableStateOf(initial.phone.orEmpty()) }
    var email by remember(initial) { mutableStateOf(initial.email.orEmpty()) }
    var address by remember(initial) { mutableStateOf(initial.address.orEmpty()) }
    var notes by remember(initial) { mutableStateOf(initial.notes.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Add supplier" else "Edit supplier") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SupplierField("Name *", name) { name = it }
                SupplierField("Contact person", contact) { contact = it }
                SupplierField("Phone", phone) { phone = it }
                SupplierField("Email", email) { email = it }
                SupplierField("Address", address) { address = it }
                SupplierField("Notes", notes) { notes = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            contactPerson = contact.trim().ifBlank { null },
                            phone = phone.trim().ifBlank { null },
                            email = email.trim().ifBlank { null },
                            address = address.trim().ifBlank { null },
                            notes = notes.trim().ifBlank { null }
                        )
                    )
                },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SupplierField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
