package com.tihloh.pos.ui.screens

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tihloh.pos.data.PosRepository
import com.tihloh.pos.data.SaleDetail
import com.tihloh.pos.printer.ReceiptPrinter
import com.tihloh.pos.printer.toReceiptData
import com.tihloh.pos.ui.money
import com.tihloh.pos.ui.quantity
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

private enum class SalesScope(val label: String) {
    TODAY("Today"),
    WEEK("7 Days"),
    MONTH("30 Days"),
    ALL("All"),
    DATE("Date"),
    RANGE("Range")
}

@Composable
fun SalesScreen(
    repository: PosRepository,
    verifyPin: (String) -> Boolean,
    biometricAvailable: Boolean,
    requestBiometric: (() -> Unit) -> Unit
) {
    val allSales by repository.sales.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var detail by remember { mutableStateOf<SaleDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedScope by remember { mutableStateOf(SalesScope.TODAY) }
    var query by remember { mutableStateOf("") }
    var customFrom by remember { mutableStateOf<Long?>(null) }
    var customTo by remember { mutableStateOf<Long?>(null) }
    var pendingDelete by remember { mutableStateOf<SaleDetail?>(null) }

    val now = System.currentTimeMillis()
    val quickFrom = remember(selectedScope, now / 60_000) {
        when (selectedScope) {
            SalesScope.TODAY -> startOfDay(now)
            SalesScope.WEEK -> now - 7L * 24 * 60 * 60 * 1000
            SalesScope.MONTH -> now - 30L * 24 * 60 * 60 * 1000
            SalesScope.ALL, SalesScope.DATE, SalesScope.RANGE -> 0L
        }
    }

    val from = when (selectedScope) {
        SalesScope.DATE, SalesScope.RANGE -> customFrom ?: 0L
        else -> quickFrom
    }
    val to = when (selectedScope) {
        SalesScope.DATE -> customFrom?.let(::endOfDay) ?: Long.MAX_VALUE
        SalesScope.RANGE -> customTo?.let(::endOfDay) ?: Long.MAX_VALUE
        else -> Long.MAX_VALUE
    }

    val filtered = remember(allSales, selectedScope, query, from, to) {
        allSales.filter {
            it.createdAt in from..to &&
                (
                    query.isBlank() ||
                        it.receiptNumber.contains(query, ignoreCase = true) ||
                        it.customerName.orEmpty().contains(query, ignoreCase = true)
                )
        }
    }
    val total = filtered.sumOf { it.totalCents }
    val paid = filtered.sumOf { it.amountPaidCents }
    val outstanding = filtered.sumOf { (it.totalCents - it.amountPaidCents).coerceAtLeast(0L) }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Sales", style = MaterialTheme.typography.headlineMedium)

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SalesScope.entries.forEach { option ->
                FilterChip(
                    selected = selectedScope == option,
                    onClick = {
                        when (option) {
                            SalesScope.DATE -> {
                                pickDate(context, customFrom ?: now) { selected ->
                                    customFrom = startOfDay(selected)
                                    customTo = customFrom
                                    selectedScope = SalesScope.DATE
                                }
                            }
                            SalesScope.RANGE -> {
                                pickDate(context, customFrom ?: now) { start ->
                                    pickDate(context, customTo ?: start) { end ->
                                        val first = minOf(startOfDay(start), startOfDay(end))
                                        val last = maxOf(startOfDay(start), startOfDay(end))
                                        customFrom = first
                                        customTo = last
                                        selectedScope = SalesScope.RANGE
                                    }
                                }
                            }
                            else -> selectedScope = option
                        }
                    },
                    label = { Text(option.label) }
                )
            }
        }

        if (selectedScope == SalesScope.DATE && customFrom != null) {
            Text(
                "Showing ${formatFilterDate(customFrom!!)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (selectedScope == SalesScope.RANGE && customFrom != null && customTo != null) {
            Text(
                "Showing ${formatFilterDate(customFrom!!)} – ${formatFilterDate(customTo!!)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Receipt / customer search") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Card(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Total sales", style = MaterialTheme.typography.labelMedium)
                    Text(money(total), style = MaterialTheme.typography.headlineSmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Transactions", style = MaterialTheme.typography.labelMedium)
                    Text(filtered.size.toString(), style = MaterialTheme.typography.headlineSmall)
                }
            }
        }

        if (filtered.isNotEmpty() && (paid != total || outstanding > 0L)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Payments received: ${money(paid)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (outstanding > 0L) {
                    Text(
                        "Outstanding: ${money(outstanding)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (filtered.isEmpty()) {
            Text("No sales in the selected period.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { sale ->
                    Card(
                        Modifier.fillMaxWidth().clickable {
                            scope.launch {
                                runCatching { repository.saleDetail(sale.id) }
                                    .onSuccess { detail = it }
                                    .onFailure { error = it.message }
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(sale.receiptNumber, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    DateFormat.getDateTimeInstance(
                                        DateFormat.MEDIUM,
                                        DateFormat.SHORT
                                    ).format(Date(sale.createdAt)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                sale.customerName?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (sale.status == "ACCOUNT_PAYABLE") {
                                    Text(
                                        "Account Payable · Due " +
                                            money((sale.totalCents - sale.amountPaidCents).coerceAtLeast(0L)),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else if (sale.status == "ACCOUNT_PAID") {
                                    Text(
                                        "Account settled",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(money(sale.totalCents), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }

    detail?.let { saleDetail ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(saleDetail.sale.receiptNumber) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(saleDetail.items) { item ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                "${item.productName} × ${quantity(item.quantity)}",
                                modifier = Modifier.weight(1f)
                            )
                            Text(money(item.lineTotalCents))
                        }
                    }
                    item {
                        Spacer(Modifier.height(6.dp))
                        Text("Total: ${money(saleDetail.sale.totalCents)}")
                        Text("Paid: ${money(saleDetail.sale.amountPaidCents)}")
                        if (saleDetail.sale.status == "ACCOUNT_PAYABLE" || saleDetail.sale.status == "ACCOUNT_PAID") {
                            Text(
                                "Balance due: " +
                                    money(
                                        (saleDetail.sale.totalCents - saleDetail.sale.amountPaidCents)
                                            .coerceAtLeast(0L)
                                    )
                            )
                        } else {
                            Text("Change: ${money(saleDetail.sale.changeCents)}")
                        }
                        saleDetail.payments.firstOrNull()?.let {
                            Text("Payment: ${it.type}")
                            it.reference?.let { ref -> Text("Reference: $ref") }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        ReceiptPrinter(context)
                            .print(saleDetail.toReceiptData())
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            pendingDelete = saleDetail
                            detail = null
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { detail = null }) { Text("Close") }
                }
            }
        )
    }

    pendingDelete?.let { saleDetail ->
        DeleteSaleDialog(
            saleDetail = saleDetail,
            verifyPin = verifyPin,
            biometricAvailable = biometricAvailable,
            requestBiometric = requestBiometric,
            onDismiss = { pendingDelete = null },
            onAuthenticated = { reason, authMethod ->
                scope.launch {
                    runCatching {
                        repository.deleteSale(
                            saleId = saleDetail.sale.id,
                            reason = reason,
                            authMethod = authMethod
                        )
                    }.onSuccess {
                        pendingDelete = null
                        Toast.makeText(
                            context,
                            "Sale deleted. Inventory was restored and the action was logged.",
                            Toast.LENGTH_LONG
                        ).show()
                    }.onFailure {
                        error = it.message ?: "Unable to delete sale."
                    }
                }
            }
        )
    }
}


@Composable
private fun DeleteSaleDialog(
    saleDetail: SaleDetail,
    verifyPin: (String) -> Boolean,
    biometricAvailable: Boolean,
    requestBiometric: (() -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onAuthenticated: (String, String) -> Unit
) {
    var reason by remember(saleDetail.sale.id) { mutableStateOf("") }
    var pin by remember(saleDetail.sale.id) { mutableStateOf("") }
    var pinError by remember(saleDetail.sale.id) { mutableStateOf(false) }
    val reasonValid = reason.trim().length >= 3
    val hasAppliedAccountPayment =
        (saleDetail.sale.status == "ACCOUNT_PAYABLE" || saleDetail.sale.status == "ACCOUNT_PAID") &&
            saleDetail.sale.amountPaidCents > 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete sale?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    saleDetail.sale.receiptNumber + " · " + money(saleDetail.sale.totalCents),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "This removes the sale from normal sales views, restores its inventory, " +
                        "and keeps a permanent audit log."
                )
                if (hasAppliedAccountPayment) {
                    Text(
                        "This sale already has an account payment and cannot be deleted.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason for deletion *") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it.take(8).filter(Char::isDigit)
                        pinError = false
                    },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (pinError) {
                    Text("Incorrect PIN", color = MaterialTheme.colorScheme.error)
                }
                if (biometricAvailable) {
                    OutlinedButton(
                        onClick = {
                            if (reasonValid && !hasAppliedAccountPayment) {
                                requestBiometric { onAuthenticated(reason.trim(), "BIOMETRIC") }
                            }
                        },
                        enabled = reasonValid && !hasAppliedAccountPayment,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Authenticate with biometric / device credential")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (verifyPin(pin)) {
                        onAuthenticated(reason.trim(), "PIN")
                    } else {
                        pinError = true
                    }
                },
                enabled = reasonValid && !hasAppliedAccountPayment
            ) {
                Text("Delete with PIN")
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
private fun startOfDay(millis: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun endOfDay(millis: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis

private fun formatFilterDate(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

private fun pickDate(
    context: android.content.Context,
    initialMillis: Long,
    onSelected: (Long) -> Unit
) {
    val initial = Calendar.getInstance().apply { timeInMillis = initialMillis }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            val selected = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            onSelected(selected.timeInMillis)
        },
        initial.get(Calendar.YEAR),
        initial.get(Calendar.MONTH),
        initial.get(Calendar.DAY_OF_MONTH)
    ).show()
}
