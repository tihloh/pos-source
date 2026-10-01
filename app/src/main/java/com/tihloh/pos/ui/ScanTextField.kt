package com.tihloh.pos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ScanTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            readOnly = readOnly,
            modifier = Modifier.weight(1f)
        )

        FilledTonalIconButton(
            onClick = onScan,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                Icons.Default.QrCodeScanner,
                contentDescription = "Scan barcode"
            )
        }
    }
}
