package com.tihloh.pos.ui.screens

import android.widget.Toast
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
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tihloh.pos.data.PosRepository
import com.tihloh.pos.data.SupplierEntity
import com.tihloh.pos.printer.PrinterConfig
import com.tihloh.pos.printer.PrinterSettings
import com.tihloh.pos.printer.TcpEscPosPrinter
import com.tihloh.pos.sync.CentralSyncClient
import com.tihloh.pos.sync.SyncConfig
import com.tihloh.pos.sync.SyncSettings
import kotlinx.coroutines.launch

@Composable
fun MoreScreen(
    repository: PosRepository,
    checkUpdate: suspend () -> Unit
) {
    val suppliers by repository.suppliers.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<SupplierEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var printerDialog by remember { mutableStateOf(false) }
    var syncDialog by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("More", style = MaterialTheme.typography.headlineMedium)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f).clickable { printerDialog = true }
            ) {
                Column(Modifier.padding(14.dp)) {
                    Icon(Icons.Default.Print, contentDescription = null)
                    Text("Receipt printer", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "ESC/POS network printer",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Card(
                modifier = Modifier.weight(1f).clickable { syncDialog = true }
            ) {
                Column(Modifier.padding(14.dp)) {
                    Icon(Icons.Default.CloudSync, contentDescription = null)
                    Text("Central sync", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Local-first + online server",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Button(
            onClick = { checking = true },
            enabled = !checking,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (checking) "Checking for update…" else "Check for app update")
        }

        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Suppliers", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(onClick = { adding = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Add")
            }
        }

        if (suppliers.isEmpty()) {
            Text("No suppliers yet.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(suppliers, key = { it.id }) { supplier ->
                    Card(
                        Modifier.fillMaxWidth().clickable { editing = supplier }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
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

    if (printerDialog) {
        PrinterDialog(
            initial = PrinterSettings(context).load(),
            onDismiss = { printerDialog = false },
            onSave = {
                PrinterSettings(context).save(it)
                printerDialog = false
                message = "Printer settings saved."
            },
            onTest = { cfg ->
                scope.launch {
                    TcpEscPosPrinter(cfg.host, cfg.port).test()
                        .onSuccess {
                            Toast.makeText(context, "Test receipt sent.", Toast.LENGTH_SHORT).show()
                        }
                        .onFailure {
                            Toast.makeText(
                                context,
                                it.message ?: "Printer test failed.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
            }
        )
    }

    if (syncDialog) {
        SyncDialog(
            initial = SyncSettings(context).load(),
            syncing = syncing,
            onDismiss = { if (!syncing) syncDialog = false },
            onSave = {
                SyncSettings(context).save(it)
                message = "Central sync settings saved."
            },
            onSync = { cfg ->
                SyncSettings(context).save(cfg)
                syncing = true
                scope.launch {
                    CentralSyncClient(cfg).push(repository.syncSnapshot())
                        .onSuccess {
                            message = "Central sync completed."
                            syncDialog = false
                        }
                        .onFailure {
                            message = it.message ?: "Central sync failed."
                        }
                    syncing = false
                }
            }
        )
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
private fun PrinterDialog(
    initial: PrinterConfig,
    onDismiss: () -> Unit,
    onSave: (PrinterConfig) -> Unit,
    onTest: (PrinterConfig) -> Unit
) {
    var enabled by remember(initial) { mutableStateOf(initial.enabled) }
    var host by remember(initial) { mutableStateOf(initial.host) }
    var port by remember(initial) { mutableStateOf(initial.port.toString()) }
    val cfg = PrinterConfig(enabled, host.trim(), port.toIntOrNull() ?: 9100)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Receipt printer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-print ready")
                        Text(
                            "Network ESC/POS · usually port 9100",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Printer IP / host") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit) },
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { onTest(cfg) },
                    enabled = host.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Print test receipt") }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(cfg) },
                enabled = !enabled || host.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SyncDialog(
    initial: SyncConfig,
    syncing: Boolean,
    onDismiss: () -> Unit,
    onSave: (SyncConfig) -> Unit,
    onSync: (SyncConfig) -> Unit
) {
    var enabled by remember(initial) { mutableStateOf(initial.enabled) }
    var url by remember(initial) { mutableStateOf(initial.baseUrl) }
    var token by remember(initial) { mutableStateOf(initial.apiToken) }
    val cfg = SyncConfig(enabled, url.trim(), token.trim())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Central server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "The local Room database remains the source used by the POS. " +
                        "When enabled, the app can push a snapshot to /api/v1/sync."
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable online sync", modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("https://pos.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("API token (optional)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { onSync(cfg) },
                    enabled = enabled && url.isNotBlank() && !syncing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (syncing) "Syncing…" else "Sync now")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(cfg) }) { Text("Save") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Close") } }
    )
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
