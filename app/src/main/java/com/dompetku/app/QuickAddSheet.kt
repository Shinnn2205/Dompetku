package com.dompetku.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    state: AppState,
    initialType: TxType = TxType.EXPENSE,
    initialGoalId: String? = null,
    onDismiss: () -> Unit,
    onSave: (tx: TxItem, goalId: String?) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var txType by remember { mutableStateOf(initialType) }
    var rawDigits by remember { mutableStateOf("") }
    var selectedWalletId by remember {
        mutableStateOf(
            state.wallets.firstOrNull { it.id == state.lastWalletId }?.id
                ?: state.wallets.firstOrNull()?.id
                ?: "wallet_ewallet"
        )
    }
    var showNoteField by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var banWarning by remember { mutableStateOf<Pair<SpendingBan, CategoryMeta>?>(null) }

    val amount = rawDigits.toLongOrNull() ?: 0L

    fun submitTx(categoryName: String, bucket: BucketType, goalId: String? = null) {
        if (amount <= 0L) return
        val tx = TxItem(
            id = UUID.randomUUID().toString(),
            type = txType,
            amount = amount,
            category = categoryName,
            note = note.trim(),
            date = todayISO(),
            createdAt = System.currentTimeMillis(),
            walletId = selectedWalletId,
            bucket = bucket
        )
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onSave(tx, goalId)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Segmented Pill: Keluar | Masuk | Tabung
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val tabs = listOf(
                    TxType.EXPENSE to "Keluar",
                    TxType.INCOME to "Masuk",
                    TxType.SAVING to "Tabung"
                )
                for ((type, label) in tabs) {
                    val active = txType == type
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { txType = type }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Amount Display + Optional Note Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Nominal",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatRp(amount),
                        style = MaterialTheme.typography.headlineMedium.tabular().copy(fontWeight = FontWeight.Bold),
                        color = if (amount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showNoteField = !showNoteField },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EditNote,
                            contentDescription = "Catatan",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (note.isNotBlank()) "Catatan ✓" else "Catatan",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (showNoteField) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("Misal: Nasi padang / Kopi susu", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(10.dp))

            // Quick Amount Chips (+10rb, +20rb, +50rb, +100rb)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val presets = listOf(10_000L to "10rb", 20_000L to "20rb", 50_000L to "50rb", 100_000L to "100rb")
                for ((presetVal, label) in presets) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                val next = (amount + presetVal).coerceAtMost(999_999_999L)
                                rawDigits = next.toString()
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "+$label",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                if (amount > 0L) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { rawDigits = "" },
                        shape = RoundedCornerShape(10.dp),
                        color = DangerRedSoft
                    ) {
                        Text(
                            text = "Clear",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DangerRed,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Wallet Selector Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (w in state.wallets) {
                    val selected = w.id == selectedWalletId
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedWalletId = w.id },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) SlateHero else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (selected) SlateHero else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = walletIcon(w.type),
                                contentDescription = w.name,
                                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = w.name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 1-Tap Category / Goal Grid (Auto-Save when amount > 0)
            Text(
                text = when (txType) {
                    TxType.EXPENSE -> if (amount > 0) "Tap kategori untuk langsung simpan:" else "Masukkan nominal lalu tap kategori:"
                    TxType.INCOME -> "Tap sumber pemasukan:"
                    TxType.SAVING -> "Tap target tabungan impian:"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            when (txType) {
                TxType.EXPENSE -> {
                    val cats = defaultCategories()
                    for (rowIdx in cats.indices step 4) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (colIdx in 0 until 4) {
                                val item = cats.getOrNull(rowIdx + colIdx)
                                if (item != null) {
                                    CategoryGridButton(
                                        meta = item,
                                        enabled = amount > 0,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            val violation = FinanceEngine.checkBanViolation(state, amount, item.name, note)
                                            if (violation != null) {
                                                banWarning = violation to item
                                            } else {
                                                submitTx(item.name, item.bucket)
                                            }
                                        }
                                    )
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                TxType.INCOME -> {
                    val cats = defaultIncomeCategories()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (item in cats) {
                            CategoryGridButton(
                                meta = item,
                                enabled = amount > 0,
                                showBucket = false,
                                modifier = Modifier.weight(1f),
                                onClick = { submitTx(item.name, item.bucket) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                TxType.SAVING -> {
                    if (state.goals.isEmpty()) {
                        Text(
                            text = "Belum ada pot tabungan. Buat dulu di tab Impian.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (g in state.goals) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable(enabled = amount > 0) {
                                            submitTx("Tabung: ${g.name}", BucketType.NEEDS, g.id)
                                        },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (g.id == initialGoalId) EmeraldSoft else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Rounded.Savings,
                                                contentDescription = null,
                                                tint = EmeraldPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = g.name,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Text(
                                            text = "${formatRpCompact(g.saved)} / ${formatRpCompact(g.target)}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            // Custom Numpad
            CustomNumpad(
                onDigit = { d ->
                    if (rawDigits.length < 9) {
                        rawDigits = if (rawDigits == "0") d else rawDigits + d
                    }
                },
                onTripleZero = {
                    if (rawDigits.isNotEmpty() && rawDigits.length <= 6) {
                        rawDigits += "000"
                    }
                },
                onBackspace = {
                    if (rawDigits.isNotEmpty()) {
                        rawDigits = rawDigits.dropLast(1)
                    }
                }
            )
        }
    }

    // Spending Ban Guardrail Dialog
    banWarning?.let { (ban, cat) ->
        AlertDialog(
            onDismissRequest = { banWarning = null },
            title = { Text("Pengingat Mode Disiplin", fontWeight = FontWeight.Bold) },
            text = {
                Text("Transaksi ini melanggar aturan \"${ban.name}\". Tetap lanjut catat pengeluaran ini?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        banWarning = null
                        submitTx(cat.name, cat.bucket)
                    }
                ) {
                    Text("Tetap Catat", color = DangerRed)
                }
            },
            dismissButton = {
                Button(onClick = { banWarning = null }) {
                    Text("Batalkan (Hemat!)")
                }
            }
        )
    }
}

@Composable
private fun CategoryGridButton(
    meta: CategoryMeta,
    enabled: Boolean,
    showBucket: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isNeeds = meta.bucket == BucketType.NEEDS
    val bg = when {
        !showBucket -> EmeraldSoft.copy(alpha = 0.65f)
        isNeeds -> NeedsBlueSoft.copy(alpha = 0.6f)
        else -> WantsAmberSoft.copy(alpha = 0.7f)
    }
    val fg = when {
        !showBucket -> EmeraldPrimary
        isNeeds -> NeedsBlue
        else -> WantsAmber
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(bg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon(meta.name),
                    contentDescription = meta.name,
                    tint = fg,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = meta.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (showBucket) {
                Text(
                    text = meta.bucket.label,
                    fontSize = 9.sp,
                    color = fg,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
