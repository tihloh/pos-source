package com.tihloh.pos.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tihloh.pos.scanner.BarcodeScannerView
import com.tihloh.pos.security.PinStore
import com.tihloh.pos.update.UpdateChecker
import com.tihloh.pos.update.UpdateInfo

private enum class MainScreen(val title: String, val icon: ImageVector) {
    POS("POS", Icons.Default.PointOfSale),
    SALES("Sales", Icons.Default.ReceiptLong),
    INVENTORY("Inventory", Icons.Default.Inventory2),
    PRODUCTS("Products", Icons.Default.Storefront),
    MORE("More", Icons.Default.MoreHoriz)
}

private enum class ScannerMode { POS, INVENTORY, PRODUCT }

@Composable
fun PosRoot(
    pinStore: PinStore,
    biometricAvailable: Boolean,
    requestBiometric: (() -> Unit) -> Unit
) {
    var unlocked by rememberSaveable { mutableStateOf(false) }
    var hasPin by remember { mutableStateOf(pinStore.hasPin()) }

    if (!hasPin) {
        SetupPinScreen { pin ->
            pinStore.savePin(pin)
            hasPin = true
            unlocked = true
        }
        return
    }

    if (!unlocked) {
        LockScreen(
            biometricAvailable = biometricAvailable,
            verifyPin = pinStore::verify,
            requestBiometric = { requestBiometric { unlocked = true } },
            onUnlocked = { unlocked = true }
        )
        return
    }

    MainShell()
}

@Composable
private fun SetupPinScreen(onSaved: (String) -> Unit) {
    var pin by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    val valid = pin.length in 4..8 && pin.all(Char::isDigit) && pin == confirm

    CenteredPanel(title = "Secure your POS") {
        Text("Create a 4–8 digit PIN. Biometrics can also unlock the app afterward.")
        Spacer(Modifier.height(16.dp))
        PinField("PIN", pin) { pin = it.take(8).filter(Char::isDigit) }
        PinField("Confirm PIN", confirm) { confirm = it.take(8).filter(Char::isDigit) }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { onSaved(pin) }, enabled = valid, modifier = Modifier.fillMaxWidth()) {
            Text("Save PIN")
        }
    }
}

@Composable
private fun LockScreen(
    biometricAvailable: Boolean,
    verifyPin: (String) -> Boolean,
    requestBiometric: () -> Unit,
    onUnlocked: () -> Unit
) {
    var pin by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }

    CenteredPanel(title = "POS Locked") {
        PinField("PIN", pin) {
            pin = it.take(8).filter(Char::isDigit)
            error = false
        }
        if (error) Text("Incorrect PIN", color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (verifyPin(pin)) onUnlocked() else error = true
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Unlock") }
        if (biometricAvailable) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = requestBiometric, modifier = Modifier.fillMaxWidth()) {
                Text("Use biometric / device credential")
            }
        }
    }
}

@Composable
private fun PinField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CenteredPanel(title: String, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}

@Composable
private fun MainShell() {
    var screen by rememberSaveable { mutableStateOf(MainScreen.POS) }
    var scannerMode by remember { mutableStateOf<ScannerMode?>(null) }
    var lastScan by rememberSaveable { mutableStateOf<String?>(null) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    val context = LocalContext.current
    val checker = remember { UpdateChecker(context.applicationContext) }

    LaunchedEffect(Unit) {
        updateInfo = checker.check()
    }

    if (scannerMode != null) {
        BarcodeScannerView(
            onScanned = { code ->
                lastScan = code
                scannerMode = null
            },
            onBack = { scannerMode = null }
        )
        return
    }

    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text("Update available: ${info.version}") },
            text = {
                Column {
                    Text(info.title)
                    if (info.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(info.notes.take(600))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.preferredUrl)))
                    updateInfo = null
                }) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { updateInfo = null }) { Text("Later") }
            }
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainScreen.entries.forEach { item ->
                    NavigationBarItem(
                        selected = screen == item,
                        onClick = { screen = item },
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (screen != MainScreen.SALES && screen != MainScreen.MORE) {
                FloatingActionButton(onClick = {
                    scannerMode = when (screen) {
                        MainScreen.POS -> ScannerMode.POS
                        MainScreen.INVENTORY -> ScannerMode.INVENTORY
                        MainScreen.PRODUCTS -> ScannerMode.PRODUCT
                        else -> null
                    }
                }) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan")
                }
            }
        }
    ) { padding ->
        when (screen) {
            MainScreen.POS -> PosScreen(padding, lastScan)
            MainScreen.SALES -> SalesScreen(padding)
            MainScreen.INVENTORY -> InventoryScreen(padding, lastScan)
            MainScreen.PRODUCTS -> ProductsScreen(padding, lastScan)
            MainScreen.MORE -> MoreScreen(padding) {
                updateInfo = checker.check(force = true)
            }
        }
    }
}

@Composable
private fun PosScreen(padding: PaddingValues, lastScan: String?) {
    SimpleScreen(padding, "POS") {
        Text("Scan an item, set quantity, add it to cart, then checkout.")
        StatusCard("Last scanned", lastScan ?: "No item scanned yet")
        StatusCard("Cart", "Cart engine and payment flow are the next implementation step.")
    }
}

@Composable
private fun SalesScreen(padding: PaddingValues) {
    SimpleScreen(padding, "Sales") {
        Text("Latest receipts will appear first. Tap a sale to view and reprint its receipt.")
    }
}

@Composable
private fun InventoryScreen(padding: PaddingValues, lastScan: String?) {
    SimpleScreen(padding, "Inventory") {
        Text("Scanner-first stock receiving, counting and adjustment.")
        StatusCard("Last scanned", lastScan ?: "Scan a product to update stock")
        StatusCard("Inventory model", "Ledger-based: stock in/out/adjustments are recorded instead of silently overwriting quantity.")
    }
}

@Composable
private fun ProductsScreen(padding: PaddingValues, lastScan: String?) {
    SimpleScreen(padding, "Products") {
        Text("Goods and non-goods are supported. Unknown barcodes can be looked up using Open Food Facts and Open Products Facts.")
        StatusCard("Last scanned", lastScan ?: "Scan to find or add a product")
    }
}

@Composable
private fun MoreScreen(padding: PaddingValues, checkUpdate: suspend () -> Unit) {
    var checking by remember { mutableStateOf(false) }
    SimpleScreen(padding, "More") {
        StatusCard("Suppliers", "Supplier management and stock receiving")
        StatusCard("Inventory periods", "Daily, weekly, monthly or custom periods")
        StatusCard("Printers", "ESC/POS Bluetooth, USB and LAN/TCP")
        StatusCard("Security", "PIN + biometric/device credential")
        Button(
            onClick = { checking = true },
            enabled = !checking
        ) { Text(if (checking) "Checking…" else "Check for update") }
        if (checking) {
            LaunchedEffect(Unit) {
                checkUpdate()
                checking = false
            }
        }
    }
}

@Composable
private fun SimpleScreen(
    padding: PaddingValues,
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        content()
    }
}

@Composable
private fun StatusCard(title: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(value)
        }
    }
}
