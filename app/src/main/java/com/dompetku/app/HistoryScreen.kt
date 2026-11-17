package com.dompetku.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class HistoryFilter(val label: String) {
    ALL("Semua"),
    NEEDS("Needs"),
    WANTS("Wants"),
    INCOME("Masuk"),
    SAVING("Tabung")
}

@Composable
fun HistoryScreen(
    state: AppState,
    onUpdateState: (AppState) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    var deletingTx by remember { mutableStateOf<TxItem?>(null) }

    val walletMap = remember(state.wallets) { state.wallets.associateBy { it.id } }

    val filtered = remember(state.transactions, query, filter) {
        state.transactions.filter { tx ->
            val matchFilter = when (filter) {
                HistoryFilter.ALL -> true
                HistoryFilter.NEEDS -> tx.type == TxType.EXPENSE && tx.bucket == BucketType.NEEDS
                HistoryFilter.WANTS -> tx.type == TxType.EXPENSE && tx.bucket == BucketType.WANTS
                HistoryFilter.INCOME -> tx.type == TxType.INCOME
                HistoryFilter.SAVING -> tx.type == TxType.SAVING
            }
            val matchQuery = query.isBlank() ||
                tx.category.contains(query, ignoreCase = true) ||
                tx.note.contains(query, ignoreCase = true) ||
                (walletMap[tx.walletId]?.name?.contains(query, ignoreCase = true) == true)
            matchFilter && matchQuery
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Riwayat Transaksi",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "${filtered.size} transaksi • Tap item untuk hapus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Cari kategori, catatan, atau dompet...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (f in HistoryFilter.entries) {
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(f.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                MinimalCard {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Tidak ada transaksi yang cocok",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            item {
                MinimalCard {
                    filtered.forEachIndexed { idx, tx ->
                        TransactionRow(
                            tx = tx,
                            walletName = walletMap[tx.walletId]?.name ?: "Dompet",
                            masked = state.hideBalance,
                            onClick = { deletingTx = tx }
                        )
                        if (idx < filtered.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }

    deletingTx?.let { tx ->
        AlertDialog(
            onDismissRequest = { deletingTx = null },
            title = { Text("Hapus Transaksi?", fontWeight = FontWeight.Bold) },
            text = {
                Text("${tx.category} (${formatRp(tx.amount)}) akan dihapus dan saldo dompet dikembalikan.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateState(FinanceEngine.deleteTransaction(state, tx))
                        deletingTx = null
                    }
                ) {
                    Text("Hapus", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTx = null }) { Text("Batal") }
            }
        )
    }
}
