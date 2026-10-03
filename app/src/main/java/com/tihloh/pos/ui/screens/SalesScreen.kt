package com.tihloh.pos.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.tihloh.pos.data.PosRepository
import com.tihloh.pos.data.SaleDetail
import com.tihloh.pos.printer.PrinterSettings
import com.tihloh.pos.printer.TcpEscPosPrinter
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
    ALL("All")
}

@Composable
fun SalesScreen(repository: PosRepository) {
    val allSales by repository.sales.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var detail by remember { mutableStateOf<SaleDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedScope by remember { mutableStateOf(SalesScope.TODAY) }
    var query by remember { mutableStateOf("") }

    val now = System.currentTimeMillis()
    val from = remember(selectedScope, now / 60_000) {
        when (selectedScope) {
            SalesScope.TODAY -> Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            SalesScope.WEEK -> now - 7L * 24 * 60 * 60 * 1000
            SalesScope.MONTH -> now - 30L * 24 * 60 * 60 * 1000
            SalesScope.ALL -> 0L
        }
    }

    val filtered = remember(allSales, selectedScope, query, from) {
        allSales.filter {
            it.createdAt >= from &&
                (query.isBlank() || it.receiptNumber.contains(query, ignoreCase = true))
        }
    }
    val total = filtered.sumOf { it.totalCents }
    val paid = filtered.sumOf { it.amountPaidCents }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Sales", style = MaterialTheme.typography.headlineMedium)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SalesScope.entries.forEach {
                FilterChip(
                    selected = selectedScope == it,
                    onClick = { selectedScope = it },
                    label = { Text(it.label) }
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Receipt search") },
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

        if (paid != total && filtered.isNotEmpty()) {
            Text(
                "Payments received: ${money(paid)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (filtered.isEmpty()) {
            Text("No sales in this scope.")
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
                        Text("Change: ${money(saleDetail.sale.changeCents)}")
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
                        val cfg = PrinterSettings(context).load()
                        if (!cfg.enabled || cfg.host.isBlank()) {
                            Toast.makeText(
                                context,
                                "Configure the network receipt printer in More.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            TcpEscPosPrinter(cfg.host, cfg.port)
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
                    }
                }) { Text("Print") }
            },
            dismissButton = {
                TextButton(onClick = { detail = null }) { Text("Close") }
            }
        )
    }
}
