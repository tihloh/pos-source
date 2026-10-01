package com.tihloh.pos.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tihloh.pos.data.AppDatabase
import com.tihloh.pos.data.PosRepository
import com.tihloh.pos.data.ProductEntity
import com.tihloh.pos.scanner.BarcodeScannerView
import com.tihloh.pos.security.PinStore
import com.tihloh.pos.ui.screens.CartLine
import com.tihloh.pos.ui.screens.InventoryScreen
import com.tihloh.pos.ui.screens.MoreScreen
import com.tihloh.pos.ui.screens.PosScreen
import com.tihloh.pos.ui.screens.ProductsScreen
import com.tihloh.pos.ui.screens.SalesScreen
import com.tihloh.pos.update.InternalUpdater
import com.tihloh.pos.update.UpdateChecker
import com.tihloh.pos.update.UpdateInfo
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val repository = remember {
        PosRepository(AppDatabase.get(context.applicationContext))
    }
    val checker = remember { UpdateChecker(context.applicationContext) }
    val internalUpdater = remember { InternalUpdater(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val cart = remember { mutableStateListOf<CartLine>() }

    var screen by rememberSaveable { mutableStateOf(MainScreen.POS) }
    var scannerMode by remember { mutableStateOf<ScannerMode?>(null) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var pendingInstall by remember { mutableStateOf<UpdateInfo?>(null) }
    var installingUpdate by remember { mutableStateOf(false) }
    var posScannedProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var inventoryScannedProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var pendingProductBarcode by remember { mutableStateOf<String?>(null) }

    fun installUpdate(info: UpdateInfo) {
        if (installingUpdate) return
        installingUpdate = true
        scope.launch {
            internalUpdater.install(info)
                .onSuccess {
                    Toast.makeText(
                        context,
                        "Update ready. Confirm the Android install prompt.",
                        Toast.LENGTH_LONG
                    ).show()
                    updateInfo = null
                }
                .onFailure {
                    Toast.makeText(
                        context,
                        it.message ?: "Unable to install update.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            installingUpdate = false
            pendingInstall = null
        }
    }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val info = pendingInstall
        if (info != null && internalUpdater.canInstallPackages()) {
            installUpdate(info)
        } else if (info != null) {
            Toast.makeText(
                context,
                "Allow POS to install updates, then try again.",
                Toast.LENGTH_LONG
            ).show()
            pendingInstall = null
        }
    }

    LaunchedEffect(Unit) {
        updateInfo = checker.check()
    }

    if (scannerMode != null) {
        val activeMode = scannerMode
        BarcodeScannerView(
            onScanned = { code ->
                scannerMode = null
                scope.launch {
                    when (activeMode) {
                        ScannerMode.POS -> {
                            val product = repository.findByBarcode(code)
                            if (product != null) {
                                posScannedProduct = product
                                screen = MainScreen.POS
                            } else {
                                screen = MainScreen.POS
                                Toast.makeText(
                                    context,
                                    "Product not found. Add it from Products first.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        ScannerMode.INVENTORY -> {
                            val product = repository.findByBarcode(code)
                            if (product != null) {
                                inventoryScannedProduct = product
                                screen = MainScreen.INVENTORY
                            } else {
                                screen = MainScreen.INVENTORY
                                Toast.makeText(
                                    context,
                                    "Product not found. Add it from Products first.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        ScannerMode.PRODUCT -> {
                            pendingProductBarcode = code
                            screen = MainScreen.PRODUCTS
                        }
                        null -> Unit
                    }
                }
            },
            onBack = { scannerMode = null }
        )
        return
    }

    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = {
                if (!installingUpdate) updateInfo = null
            },
            title = { Text("Update available: ${info.version}") },
            text = {
                Column {
                    Text(info.title)
                    if (info.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(info.notes.take(600))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (installingUpdate) {
                            "Preparing update internally…"
                        } else {
                            "The APK will be streamed directly into Android's installer. " +
                                "No APK will be saved in Downloads."
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !installingUpdate,
                    onClick = {
                        if (info.apkUrl == null) {
                            Toast.makeText(
                                context,
                                "This release has no APK asset.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else if (internalUpdater.canInstallPackages()) {
                            installUpdate(info)
                        } else {
                            pendingInstall = info
                            installPermissionLauncher.launch(
                                internalUpdater.installPermissionIntent()
                            )
                        }
                    }
                ) {
                    Text(if (installingUpdate) "Preparing…" else "Install update")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !installingUpdate,
                    onClick = { updateInfo = null }
                ) { Text("Later") }
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
            if (screen == MainScreen.POS || screen == MainScreen.INVENTORY || screen == MainScreen.PRODUCTS) {
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
        Box(Modifier.padding(padding)) {
            when (screen) {
                MainScreen.POS -> PosScreen(
                    repository = repository,
                    cart = cart,
                    scannedProduct = posScannedProduct,
                    onScannedProductHandled = { posScannedProduct = null }
                )
                MainScreen.SALES -> SalesScreen(repository)
                MainScreen.INVENTORY -> InventoryScreen(
                    repository = repository,
                    scannedProduct = inventoryScannedProduct,
                    onScannedProductHandled = { inventoryScannedProduct = null }
                )
                MainScreen.PRODUCTS -> ProductsScreen(
                    repository = repository,
                    pendingBarcode = pendingProductBarcode,
                    onPendingBarcodeHandled = { pendingProductBarcode = null }
                )
                MainScreen.MORE -> MoreScreen(
                    repository = repository,
                    checkUpdate = {
                        updateInfo = checker.check(force = true)
                    }
                )
            }
        }
    }
}
