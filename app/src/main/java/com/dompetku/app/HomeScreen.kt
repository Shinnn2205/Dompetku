package com.dompetku.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID

@Composable
fun HomeScreen(
    state: AppState,
    onUpdateState: (AppState) -> Unit,
    onOpenQuickAdd: (TxType) -> Unit,
    onOpenSettings: () -> Unit,
    onGoToHistory: () -> Unit
) {
    val sts = remember(state) { FinanceEngine.computeSafeToSpend(state) }
    var showFormulaBreakdown by remember { mutableStateOf(false) }
    var editingWallet by remember { mutableStateOf<WalletItem?>(null) }
    var showAddWallet by remember { mutableStateOf(false) }

    val walletMap = remember(state.wallets) { state.wallets.associateBy { it.id } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Clean Top Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "DompetKu",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Siklus bulanan • ${sts.daysRemaining} hari tersisa",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = { onUpdateState(state.copy(hideBalance = !state.hideBalance)) },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Icon(
                            imageVector = if (state.hideBalance) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "Sembunyikan Saldo",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Pengaturan",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 2. Hero Card: Safe-to-Spend Daily (Apple Wallet / Jago Style)
        item {
            val statusColor: Color
            val statusBg: Color
            val statusLabel: String
            when {
                sts.safeToSpendToday < 0L -> {
                    statusColor = Color(0xFFFCA5A5)
                    statusBg = Color(0xFF7F1D1D)
                    statusLabel = "Over Limit"
                }
                sts.dailyAllowance > 0 && sts.safeToSpendToday <= (sts.dailyAllowance * 0.25f) -> {
                    statusColor = Color(0xFFFCD34D)
                    statusBg = Color(0xFF78350F)
                    statusLabel = "Hampir Habis"
                }
                else -> {
                    statusColor = Color(0xFF6EE7B7)
                    statusBg = Color(0xFF064E3B)
                    statusLabel = "Aman"
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(SlateHero, SlateHeroEnd)
                        )
                    )
                    .clickable { showFormulaBreakdown = !showFormulaBreakdown }
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SAFE TO SPEND HARI INI",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(statusBg)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = statusLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = formatRp(sts.safeToSpendToday, state.hideBalance),
                        style = MaterialTheme.typography.headlineLarge.tabular().copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 32.sp
                        ),
                        color = Color.White
                    )

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Jatah harian: ${formatRp(sts.dailyAllowance, state.hideBalance)} • Tap untuk rincian",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )

                    AnimatedVisibility(visible = showFormulaBreakdown) {
                        Column(
                            modifier = Modifier
                                .padding(top = 14.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BreakdownLine("Saldo Likuid (Budget)", formatRp(sts.liquidBalance, state.hideBalance))
                            BreakdownLine("Tagihan Tetap Tertahan", "-${formatRp(sts.unpaidRecurring, state.hideBalance)}")
                            BreakdownLine("Sisa Target Nabung Bulan Ini", "-${formatRp(sts.unmetSavingTarget, state.hideBalance)}")
                            BreakdownLine("Dibagi ${sts.daysRemaining} Hari Sisa Siklus", formatRp(sts.dailyAllowance, state.hideBalance))
                            BreakdownLine("Terpakai Hari Ini", "-${formatRp(sts.spentToday, state.hideBalance)}")
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HeroMetric("Saldo Likuid", formatRp(sts.liquidBalance, state.hideBalance))
                        HeroMetric("Tagihan Tertahan", formatRp(sts.unpaidRecurring, state.hideBalance))
                        HeroMetric("Keluar Hari Ini", formatRp(sts.spentToday, state.hideBalance))
                    }
                }
            }
        }

        // 3. Dompet Saya (Horizontal Cards)
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dompet Saya",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    TextButton(
                        onClick = { showAddWallet = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Dompet", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (w in state.wallets) {
                        Surface(
                            modifier = Modifier
                                .width(156.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { editingWallet = w },
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(Color(w.colorHex).copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = walletIcon(w.type),
                                            contentDescription = w.name,
                                            tint = Color(w.colorHex),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(
                                        text = w.type.label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = w.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = formatRp(w.balance, state.hideBalance),
                                    style = MaterialTheme.typography.titleSmall.tabular().copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Needs vs Wants Bulan Ini
        item {
            val totalSpentCycle = (sts.needsSpentCycle + sts.wantsSpentCycle).coerceAtLeast(1L)
            val needsRatio = sts.needsSpentCycle.toFloat() / totalSpentCycle.toFloat()
            val hasSpending = (sts.needsSpentCycle + sts.wantsSpentCycle) > 0L

            MinimalCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Needs vs Wants Siklus Ini",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    if (hasSpending) {
                        val needsPct = (needsRatio * 100).toInt()
                        Text(
                            text = "$needsPct% / ${100 - needsPct}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                // Sleek dual-segment bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (hasSpending) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            if (needsRatio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(needsRatio.coerceAtLeast(0.02f))
                                        .fillMaxHeight()
                                        .background(NeedsBlue)
                                )
                            }
                            val wantsRatio = 1f - needsRatio
                            if (wantsRatio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(wantsRatio.coerceAtLeast(0.02f))
                                        .fillMaxHeight()
                                        .background(WantsAmber)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(NeedsBlue)
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text("Kebutuhan (Needs)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = formatRp(sts.needsSpentCycle, state.hideBalance),
                                style = MaterialTheme.typography.bodyMedium.tabular().copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(WantsAmber)
                        )
                        Spacer(Modifier.width(6.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Keinginan (Wants)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = formatRp(sts.wantsSpentCycle, state.hideBalance),
                                style = MaterialTheme.typography.bodyMedium.tabular().copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }

        // 5. Aktivitas Terbaru
        item {
            MinimalCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Aktivitas Terbaru",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    if (state.transactions.isNotEmpty()) {
                        Text(
                            text = "Lihat Semua",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = EmeraldPrimary,
                            modifier = Modifier.clickable(onClick = onGoToHistory)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))

                if (state.transactions.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Belum ada transaksi tercatat",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Tap tombol + di bawah untuk catat dalam 4 detik",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                } else {
                    val recent = state.transactions.take(5)
                    recent.forEachIndexed { idx, tx ->
                        val wName = walletMap[tx.walletId]?.name ?: "Dompet"
                        TransactionRow(
                            tx = tx,
                            walletName = wName,
                            masked = state.hideBalance
                        )
                        if (idx < recent.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }

    // Dialog: Edit Saldo / Pengaturan Dompet
    editingWallet?.let { w ->
        var nameInput by remember(w.id) { mutableStateOf(w.name) }
        var balanceInput by remember(w.id) { mutableStateOf(w.balance.toString()) }
        var includeBudget by remember(w.id) { mutableStateOf(w.includeInBudget) }

        AlertDialog(
            onDismissRequest = { editingWallet = null },
            title = { Text("Atur Dompet", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Nama Dompet") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = balanceInput,
                        onValueChange = { balanceInput = it.filter { ch -> ch.isDigit() || ch == '-' } },
                        label = { Text("Saldo Saat Ini (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Hitung ke Safe-to-Spend", fontSize = 13.sp)
                        Switch(checked = includeBudget, onCheckedChange = { includeBudget = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newBal = balanceInput.toLongOrNull() ?: w.balance
                        val updated = state.wallets.map {
                            if (it.id == w.id) it.copy(
                                name = nameInput.trim().ifBlank { w.name },
                                balance = newBal,
                                includeInBudget = includeBudget
                            ) else it
                        }
                        onUpdateState(state.copy(wallets = updated))
                        editingWallet = null
                    }
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                if (state.wallets.size > 1) {
                    TextButton(
                        onClick = {
                            onUpdateState(state.copy(wallets = state.wallets.filterNot { it.id == w.id }))
                            editingWallet = null
                        }
                    ) {
                        Text("Hapus", color = DangerRed)
                    }
                }
            }
        )
    }

    // Dialog: Tambah Dompet Baru
    if (showAddWallet) {
        var name by remember { mutableStateOf("") }
        var bal by remember { mutableStateOf("0") }
        var type by remember { mutableStateOf(WalletType.EWALLET) }

        AlertDialog(
            onDismissRequest = { showAddWallet = false },
            title = { Text("Tambah Dompet Baru", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama (misal: SeaBank, Dana)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bal,
                        onValueChange = { bal = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Saldo Awal (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (wt in WalletType.entries) {
                            FilterChip(
                                selected = type == wt,
                                onClick = { type = wt },
                                label = { Text(wt.label, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            val newW = WalletItem(
                                id = "w_${UUID.randomUUID()}",
                                name = name.trim(),
                                type = type,
                                balance = bal.toLongOrNull() ?: 0L,
                                includeInBudget = true,
                                colorHex = when (type) {
                                    WalletType.BANK -> 0xFF0F172A
                                    WalletType.EWALLET -> 0xFF059669
                                    WalletType.CASH -> 0xFF334155
                                }
                            )
                            onUpdateState(state.copy(wallets = state.wallets + newW))
                            showAddWallet = false
                        }
                    }
                ) {
                    Text("Tambah")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddWallet = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun HeroMetric(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 10.sp, color = Color(0xFF94A3B8))
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.tabular().copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
    }
}

@Composable
private fun BreakdownLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 11.sp, color = Color(0xFFCBD5E1))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.tabular().copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
            color = Color.White
        )
    }
}
