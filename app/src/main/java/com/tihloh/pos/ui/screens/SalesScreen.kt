package com.tihloh.pos.ui.screens

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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tihloh.pos.data.PosRepository
import com.tihloh.pos.data.SaleDetail
import com.tihloh.pos.ui.money
import com.tihloh.pos.ui.quantity
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun SalesScreen(repository: PosRepository) {
    val sales by repository.sales.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<SaleDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Sales", style = MaterialTheme.typography.headlineMedium)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (sales.isEmpty()) {
            Text("No sales yet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sales, key = { it.id }) { sale ->
                    Card(
                        Modifier.fillMaxWidth().clickable {
                            scope.launch {
                                runCatching { repository.saleDetail(sale.id) }
                                    .onSuccess { detail = it }
                                    .onFailure { error = it.message }
                            }
                        }
                    ) {
                        Row(Modifier.padding(14.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(sale.receiptNumber, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    DateFormat.getDateTimeInstance(
                                        DateFormat.MEDIUM,
                                        DateFormat.SHORT
                                    ).format(Date(sale.createdAt))
                                )
                            }
                            Column {
                                Text(money(sale.totalCents), style = MaterialTheme.typography.titleMedium)
                                Text("Change ${money(sale.changeCents)}")
                            }
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
                        Spacer(Modifier.height(5.dp))
                        Text("Total: ${money(saleDetail.sale.totalCents)}")
                        Text("Paid: ${money(saleDetail.sale.amountPaidCents)}")
                        Text("Change: ${money(saleDetail.sale.changeCents)}")
                        val payment = saleDetail.payments.firstOrNull()
                        if (payment != null) {
                            Text("Payment: ${payment.type}")
                            payment.reference?.let { Text("Reference: $it") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detail = null }) { Text("Close") }
            }
        )
    }
}
