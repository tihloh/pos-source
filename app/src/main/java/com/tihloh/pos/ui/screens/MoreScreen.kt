package com.tihloh.pos.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import com.tihloh.pos.BuildConfig
import com.tihloh.pos.data.PosRepository
import com.tihloh.pos.data.SupplierEntity
import com.tihloh.pos.printer.BluetoothEscPosPrinter
import com.tihloh.pos.printer.BluetoothPrinterSupport
import com.tihloh.pos.printer.PairedPrinter
import com.tihloh.pos.printer.DEFAULT_RECEIPT_TEMPLATE
import com.tihloh.pos.printer.PrinterConfig
import com.tihloh.pos.printer.PrinterSettings
import com.tihloh.pos.printer.ReceiptData
import com.tihloh.pos.printer.ReceiptLine
import com.tihloh.pos.printer.TcpEscPosPrinter
import com.tihloh.pos.sync.CentralSyncClient
import com.tihloh.pos.sync.SyncConfig
import com.tihloh.pos.sync.SyncSettings
import com.tihloh.pos.ui.theme.ThemeMode
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun MoreScreen(
    repository: PosRepository,
    checkUpdate: suspend () -> Unit,
    onCustomers: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    val suppliers by repository.suppliers.collectAsState(initial = emptyList())
    val auditLogs by repository.auditLogs.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<SupplierEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var printerDialog by remember { mutableStateOf(false) }
    var syncDialog by remember { mutableStateOf(false) }
    var aboutDialog by remember { mutableStateOf(false) }
    var auditDialog by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var pairedDevices by remember { mutableStateOf<List<PairedPrinter>>(emptyList()) }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            pairedDevices = BluetoothPrinterSupport.pairedDevices(context)
        }
    }

    fun refreshBluetoothDevices() {
        if (BluetoothPrinterSupport.hasPermission(context)) {
            pairedDevices = BluetoothPrinterSupport.pairedDevices(context)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("More", style = MaterialTheme.typography.headlineMedium)

        Card(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DarkMode, contentDescription = null)
                    Text(
                        "Appearance",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> "System"
                                        ThemeMode.LIGHT -> "Light"
                                        ThemeMode.DARK -> "Dark"
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f).clickable {
                    refreshBluetoothDevices()
                    printerDialog = true
                }
            ) {
                Column(Modifier.padding(14.dp)) {
                    Icon(Icons.Default.Print, contentDescription = null)
                    Text("Receipt printer", style = MaterialTheme.typography.titleSmall)
                    Text("Bluetooth or network ESC/POS", style = MaterialTheme.typography.bodySmall)
                }
            }
            Card(
                modifier = Modifier.weight(1f).clickable { syncDialog = true }
            ) {
                Column(Modifier.padding(14.dp)) {
                    Icon(Icons.Default.CloudSync, contentDescription = null)
                    Text("Central sync", style = MaterialTheme.typography.titleSmall)
                    Text("Local-first + online server", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onCustomers)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.People, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text("Customers", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Accounts, barcode lookup, and account balances",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().clickable { auditDialog = true }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.History, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text("Audit log", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Protected sales deletions and account payment activity",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().clickable { aboutDialog = true }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text("About", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "App info, version, and developer",
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
                    Card(Modifier.fillMaxWidth().clickable { editing = supplier }) {
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

    if (auditDialog) {
        AlertDialog(
            onDismissRequest = { auditDialog = false },
            title = { Text("Audit log") },
            text = {
                if (auditLogs.isEmpty()) {
                    Text("No logged security or account actions yet.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(auditLogs, key = { it.id }) { log ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(
                                    Modifier.fillMaxWidth().padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(Modifier.fillMaxWidth()) {
                                        Text(
                                            log.action.replace('_', ' '),
                                            style = MaterialTheme.typography.labelLarge,
                                            modifier = Modifier.weight(1f)
                                        )
                                        log.authMethod?.let {
                                            Text(
                                                it,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Text(log.summary, style = MaterialTheme.typography.bodyMedium)
                                    log.metadata?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        DateFormat.getDateTimeInstance(
                                            DateFormat.MEDIUM,
                                            DateFormat.SHORT
                                        ).format(Date(log.createdAt)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { auditDialog = false }) { Text("Close") }
            }
        )
    }

    if (aboutDialog) {
        AlertDialog(
            onDismissRequest = { aboutDialog = false },
            title = { Text("About POS") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("POS", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Point of Sale System",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Version ${BuildConfig.VERSION_NAME}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Developer", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "Christian Borsal Bustamante",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text("Full Stack Software Developer")
                            Text("IT Professional | Systems & Automation")
                            Text(
                                "GitHub: tihloh",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        "Local-first Android POS with inventory, barcode scanning, " +
                            "receipt printing, suppliers, sales tracking, and central sync.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(onClick = { aboutDialog = false }) { Text("Close") }
            }
        )
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
            pairedDevices = pairedDevices,
            onRefreshBluetooth = ::refreshBluetoothDevices,
            onOpenBluetoothSettings = {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            },
            onDismiss = { printerDialog = false },
            onSave = {
                PrinterSettings(context).save(it)
                printerDialog = false
                message = "Printer settings saved."
            },
            onTest = { cfg ->
                scope.launch {
                    val testReceipt = ReceiptData(
                        receiptNumber = "TEST",
                        lines = listOf(ReceiptLine("Printer test", 1.0, 0, 0)),
                        subtotalCents = 0,
                        discountCents = 0,
                        totalCents = 0,
                        paymentType = "TEST",
                        amountPaidCents = 0,
                        changeCents = 0,
                        timestamp = System.currentTimeMillis(),
                        storeName = cfg.storeName,
                        receiptTemplate = cfg.receiptTemplate
                    )
                    val result = if (cfg.connectionType == "BLUETOOTH") {
                        BluetoothEscPosPrinter(context, cfg.bluetoothAddress).print(testReceipt)
                    } else {
                        TcpEscPosPrinter(cfg.host, cfg.port).print(testReceipt)
                    }
                    result.onSuccess {
                        Toast.makeText(context, "Test receipt sent.", Toast.LENGTH_SHORT).show()
                    }.onFailure {
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
    pairedDevices: List<PairedPrinter>,
    onRefreshBluetooth: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (PrinterConfig) -> Unit,
    onTest: (PrinterConfig) -> Unit
) {
    var enabled by remember(initial) { mutableStateOf(initial.enabled) }
    var connectionType by remember(initial) { mutableStateOf(initial.connectionType) }
    var host by remember(initial) { mutableStateOf(initial.host) }
    var port by remember(initial) { mutableStateOf(initial.port.toString()) }
    var bluetoothAddress by remember(initial) { mutableStateOf(initial.bluetoothAddress) }
    var bluetoothName by remember(initial) { mutableStateOf(initial.bluetoothName) }
    var storeName by remember(initial) { mutableStateOf(initial.storeName) }
    var receiptTemplate by remember(initial) { mutableStateOf(initial.receiptTemplate) }

    val cfg = PrinterConfig(
        enabled = enabled,
        connectionType = connectionType,
        host = host.trim(),
        port = port.toIntOrNull() ?: 9100,
        bluetoothAddress = bluetoothAddress,
        bluetoothName = bluetoothName,
        storeName = storeName.trim().ifBlank { "POS" },
        receiptTemplate = receiptTemplate.ifBlank { DEFAULT_RECEIPT_TEMPLATE }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Receipt printer") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Enable receipt printer")
                            Text("ESC/POS thermal printer", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = connectionType == "BLUETOOTH",
                            onClick = {
                                connectionType = "BLUETOOTH"
                                onRefreshBluetooth()
                            },
                            label = { Text("Bluetooth") },
                            leadingIcon = { Icon(Icons.Default.Bluetooth, null) }
                        )
                        FilterChip(
                            selected = connectionType == "TCP",
                            onClick = { connectionType = "TCP" },
                            label = { Text("Network / Wi-Fi") }
                        )
                    }
                }
                if (connectionType == "BLUETOOTH") {
                    item {
                        Text(
                            "Paired printers",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    if (pairedDevices.isEmpty()) {
                        item {
                            Text(
                                "No paired Bluetooth devices found. Pair the printer in Android first, then refresh.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onOpenBluetoothSettings) {
                                    Text("Pair device")
                                }
                                OutlinedButton(onClick = onRefreshBluetooth) {
                                    Text("Refresh")
                                }
                            }
                        }
                    } else {
                        items(pairedDevices, key = { it.address }) { device ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    bluetoothAddress = device.address
                                    bluetoothName = device.name
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(device.name, style = MaterialTheme.typography.titleSmall)
                                        Text(device.address, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (bluetoothAddress == device.address) {
                                        Text("Selected", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    item {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text("Printer IP / host") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it.filter(Char::isDigit) },
                            label = { Text("Port") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                item {
                    Text("Receipt", style = MaterialTheme.typography.titleMedium)
                }
                item {
                    OutlinedTextField(
                        value = storeName,
                        onValueChange = { storeName = it },
                        label = { Text("Store name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = receiptTemplate,
                        onValueChange = { receiptTemplate = it },
                        label = { Text("Receipt template") },
                        minLines = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text(
                        "Placeholders: {store}, {receipt}, {date}, {time}, {datetime}, " +
                            "{customer}, {items}, {item_count}, " +
                            "{subtotal}, {discount}, {total}, " +
                            "{payment}, {paid}, {balance}, {change}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { receiptTemplate = DEFAULT_RECEIPT_TEMPLATE },
                            modifier = Modifier.weight(1f)
                        ) { Text("Reset template") }
                        OutlinedButton(
                            onClick = { onTest(cfg) },
                            enabled = when (connectionType) {
                                "BLUETOOTH" -> bluetoothAddress.isNotBlank()
                                else -> host.isNotBlank()
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Test print") }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(cfg) },
                enabled = !enabled || when (connectionType) {
                    "BLUETOOTH" -> bluetoothAddress.isNotBlank()
                    else -> host.isNotBlank()
                }
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
