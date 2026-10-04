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
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
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
import com.tihloh.pos.printer.DEFAULT_RECEIPT_TEMPLATE
import com.tihloh.pos.printer.PairedPrinter
import com.tihloh.pos.printer.PrinterConfig
import com.tihloh.pos.printer.PrinterSettings
import com.tihloh.pos.printer.ReceiptData
import com.tihloh.pos.printer.ReceiptLine
import com.tihloh.pos.printer.TcpEscPosPrinter
import com.tihloh.pos.sync.CentralSyncClient
import com.tihloh.pos.sync.SyncConfig
import com.tihloh.pos.sync.SyncSettings
import com.tihloh.pos.ui.theme.ThemeMode
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun MoreScreen(
    repository: PosRepository,
    onCustomers: () -> Unit,
    onSuppliers: () -> Unit,
    onSettings: () -> Unit
) {
    val auditLogs by repository.auditLogs.collectAsState(initial = emptyList())
    var auditDialog by remember { mutableStateOf(false) }
    var aboutDialog by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("More", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Manage people, suppliers, settings, and system activity.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        MenuCard(
            title = "Customers",
            description = "Accounts, balances, barcode lookup, and payments",
            icon = { Icon(Icons.Default.People, contentDescription = null) },
            onClick = onCustomers
        )
        MenuCard(
            title = "Suppliers",
            description = "Supplier directory and contact information",
            icon = { Icon(Icons.Default.Business, contentDescription = null) },
            onClick = onSuppliers
        )
        MenuCard(
            title = "Settings",
            description = "Appearance, receipt printer, sync, and app updates",
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            onClick = onSettings
        )
        MenuCard(
            title = "Audit log",
            description = "Protected deletions and customer account payments",
            icon = { Icon(Icons.Default.History, contentDescription = null) },
            onClick = { auditDialog = true }
        )
        MenuCard(
            title = "About",
            description = "App version and developer information",
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            onClick = { aboutDialog = true }
        )
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
                                    Text(log.summary)
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Point of Sale System", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Version ${BuildConfig.VERSION_NAME}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("Developer", style = MaterialTheme.typography.labelLarge)
                            Text("Christian Borsal Bustamante", style = MaterialTheme.typography.titleMedium)
                            Text("Full Stack Software Developer")
                            Text("GitHub: tihloh", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { aboutDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
fun SuppliersScreen(
    repository: PosRepository,
    onBack: () -> Unit
) {
    val suppliers by repository.suppliers.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<SupplierEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    val filtered = remember(suppliers, query) {
        suppliers.filter {
            query.isBlank() ||
                it.name.contains(query, true) ||
                it.contactPerson.orEmpty().contains(query, true) ||
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
                "Suppliers",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).padding(start = 12.dp)
            )
            Button(onClick = { adding = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Add")
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search supplier / contact") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        if (filtered.isEmpty()) {
            Text("No matching suppliers.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { supplier ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { editing = supplier }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Business, contentDescription = null)
                            Column(Modifier.weight(1f)) {
                                Text(supplier.name, style = MaterialTheme.typography.titleMedium)
                                val detail = listOfNotNull(
                                    supplier.contactPerson,
                                    supplier.phone,
                                    supplier.email
                                ).filter { it.isNotBlank() }.joinToString(" · ")
                                if (detail.isNotBlank()) {
                                    Text(
                                        detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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

    if (adding) {
        SupplierDialog(
            initial = SupplierEntity(name = ""),
            onDismiss = { adding = false },
            onSave = { supplier ->
                scope.launch {
                    runCatching { repository.saveSupplier(supplier) }
                        .onSuccess {
                            adding = false
                            message = "Supplier saved."
                        }
                        .onFailure { message = it.message ?: "Unable to save supplier." }
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
                        .onSuccess {
                            editing = null
                            message = "Supplier updated."
                        }
                        .onFailure { message = it.message ?: "Unable to update supplier." }
                }
            }
        )
    }
}

@Composable
fun SettingsScreen(
    repository: PosRepository,
    checkUpdate: suspend () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var printerDialog by remember { mutableStateOf(false) }
    var syncDialog by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
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
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).padding(start = 12.dp)
            )
        }

        Card(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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

        MenuCard(
            title = "Receipt printer",
            description = "Bluetooth or network ESC/POS printer",
            icon = { Icon(Icons.Default.Print, contentDescription = null) },
            onClick = {
                refreshBluetoothDevices()
                printerDialog = true
            }
        )
        MenuCard(
            title = "Central sync",
            description = "Configure the optional online synchronization server",
            icon = { Icon(Icons.Default.CloudSync, contentDescription = null) },
            onClick = { syncDialog = true }
        )

        Button(
            onClick = { checking = true },
            enabled = !checking,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (checking) "Checking for update…" else "Check for app update")
        }

        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
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
                syncDialog = false
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
}

@Composable
private fun MenuCard(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            icon()
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
                    if (pairedDevices.isEmpty()) {
                        item {
                            Text(
                                "No paired devices found. Pair the printer in Android first.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onOpenBluetoothSettings) { Text("Pair device") }
                                OutlinedButton(onClick = onRefreshBluetooth) { Text("Refresh") }
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
                        minLines = 9,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text(
                        "Placeholders: {store}, {receipt}, {date}, {time}, {datetime}, " +
                            "{customer}, {items}, {item_count}, {subtotal}, {discount}, " +
                            "{total}, {payment}, {paid}, {balance}, {change}",
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
                        ) { Text("Reset") }
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
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
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
