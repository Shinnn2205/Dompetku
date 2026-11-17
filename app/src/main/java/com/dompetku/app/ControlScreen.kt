package com.dompetku.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID

@Composable
fun ControlScreen(
    state: AppState,
    onUpdateState: (AppState) -> Unit
) {
    val cycleKey = remember(state.cycleStartDay) { FinanceEngine.currentCycleKey(state.cycleStartDay) }
    val walletMap = remember(state.wallets) { state.wallets.associateBy { it.id } }
    var showAddRule by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Tagihan & Kontrol",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Tagihan tetap otomatis dikunci dari Safe-to-Spend",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = { showAddRule = true },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tagihan", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Recurring Bills List
        if (state.recurringRules.isEmpty()) {
            item {
                MinimalCard {
                    Text(
                        text = "Belum ada tagihan tetap. Tap '+ Tagihan' untuk menambahkan uang kos, kuota, atau langganan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(state.recurringRules, key = { it.id }) { rule ->
                val isPaid = rule.lastPaidCycle == cycleKey
                val walletName = walletMap[rule.walletId]?.name ?: "Dompet"

                MinimalCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = rule.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Tgl ${rule.dueDay} • $walletName",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formatRp(rule.amount, state.hideBalance),
                            style = MaterialTheme.typography.titleSmall.tabular().copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                val updated = state.recurringRules.map {
                                    if (it.id == rule.id) it.copy(autoReserve = !it.autoReserve) else it
                                }
                                onUpdateState(state.copy(recurringRules = updated))
                            }
                        ) {
                            Checkbox(
                                checked = rule.autoReserve,
                                onCheckedChange = { checked ->
                                    val updated = state.recurringRules.map {
                                        if (it.id == rule.id) it.copy(autoReserve = checked) else it
                                    }
                                    onUpdateState(state.copy(recurringRules = updated))
                                },
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Kunci di Safe-to-Spend",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    onUpdateState(state.copy(recurringRules = state.recurringRules.filterNot { it.id == rule.id }))
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Hapus",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (isPaid) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = EmeraldSoft
                                ) {
                                    Text(
                                        text = "Lunas ✓",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldPrimary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            } else {
                                Button(
                                    onClick = { onUpdateState(FinanceEngine.payRecurringRule(state, rule)) },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Bayar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Streak Masak Sendiri
        item {
            val today = todayISO()
            val cookedToday = today in state.cookDays

            MinimalCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(WantsAmberSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SoupKitchen,
                                contentDescription = null,
                                tint = WantsAmber,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Streak Masak Sendiri (${state.cookDays.size} hari)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Estimasi hemat: ${formatRp(state.cookSavedTotal, state.hideBalance)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    FilledTonalButton(
                        enabled = !cookedToday,
                        onClick = {
                            onUpdateState(
                                state.copy(
                                    cookDays = state.cookDays + today,
                                    cookSavedTotal = state.cookSavedTotal + 25_000L
                                )
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(if (cookedToday) "Sudah ✓" else "Masak!", fontSize = 12.sp)
                    }
                }
            }
        }

        // Spending Bans (Mode Disiplin)
        item {
            MinimalCard {
                Text(
                    text = "Guardrail Anti-Boros",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Aktifkan pengingat otomatis saat mencatat pengeluaran tertentu",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                state.bans.forEachIndexed { idx, ban ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ban.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (ban.dailyMax == 0L) "Blokir pengingat penuh" else "Batas ${formatRp(ban.dailyMax)}/hari",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = ban.active,
                            onCheckedChange = { checked ->
                                val updated = state.bans.map {
                                    if (it.id == ban.id) it.copy(active = checked) else it
                                }
                                onUpdateState(state.copy(bans = updated))
                            }
                        )
                    }
                    if (idx < state.bans.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }

    if (showAddRule) {
        var title by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var dueDay by remember { mutableStateOf("5") }
        var selectedWalletId by remember { mutableStateOf(state.wallets.firstOrNull()?.id ?: "wallet_bank") }

        AlertDialog(
            onDismissRequest = { showAddRule = false },
            title = { Text("Tambah Tagihan Tetap", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Nama Tagihan (misal: Kosan)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Nominal (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dueDay,
                        onValueChange = { dueDay = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Tanggal Jatuh Tempo (1-28)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amount.toLongOrNull() ?: 0L
                        val day = (dueDay.toIntOrNull() ?: 1).coerceIn(1, 28)
                        if (title.isNotBlank() && amt > 0L) {
                            val newRule = RecurringRule(
                                id = "rec_${UUID.randomUUID()}",
                                title = title.trim(),
                                amount = amt,
                                walletId = selectedWalletId,
                                category = "Kos & Tagihan",
                                dueDay = day,
                                autoReserve = true,
                                lastPaidCycle = null
                            )
                            onUpdateState(state.copy(recurringRules = state.recurringRules + newRule))
                            showAddRule = false
                        }
                    }
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRule = false }) { Text("Batal") }
            }
        )
    }
}
