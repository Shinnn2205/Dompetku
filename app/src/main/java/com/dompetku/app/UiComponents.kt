package com.dompetku.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MinimalCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(shape).clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

fun walletIcon(type: WalletType): ImageVector = when (type) {
    WalletType.BANK -> Icons.Rounded.AccountBalance
    WalletType.EWALLET -> Icons.Rounded.QrCode2
    WalletType.CASH -> Icons.Rounded.Payments
}

fun categoryIcon(category: String): ImageVector {
    val lower = category.lowercase()
    return when {
        lower.contains("makan") -> Icons.Rounded.Restaurant
        lower.contains("kopi") || lower.contains("jajan") -> Icons.Rounded.LocalCafe
        lower.contains("transport") -> Icons.Rounded.DirectionsBus
        lower.contains("kos") || lower.contains("tagihan") -> Icons.Rounded.Home
        lower.contains("kebutuhan") -> Icons.Rounded.ShoppingBag
        lower.contains("nongkrong") -> Icons.Rounded.Groups
        lower.contains("belanja") -> Icons.Rounded.LocalMall
        lower.contains("hiburan") -> Icons.Rounded.SportsEsports
        lower.contains("bulanan") -> Icons.Rounded.AccountBalanceWallet
        lower.contains("gaji") -> Icons.Rounded.Work
        lower.contains("freelance") -> Icons.Rounded.LaptopMac
        lower.contains("hadiah") || lower.contains("bonus") -> Icons.Rounded.CardGiftcard
        lower.contains("tabung") -> Icons.Rounded.Savings
        else -> Icons.Rounded.ReceiptLong
    }
}

@Composable
fun CategoryIconBadge(
    category: String,
    type: TxType,
    bucket: BucketType,
    size: Dp = 42.dp
) {
    val (bg, fg) = when {
        type == TxType.INCOME -> EmeraldSoft to EmeraldPrimary
        type == TxType.SAVING -> Color(0xFFE0F2FE) to InfoBlue
        bucket == BucketType.WANTS -> WantsAmberSoft to WantsAmber
        else -> NeedsBlueSoft to NeedsBlue
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (type == TxType.SAVING) Icons.Rounded.Savings else categoryIcon(category),
            contentDescription = category,
            tint = fg,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

@Composable
fun BucketPill(bucket: BucketType) {
    val isNeeds = bucket == BucketType.NEEDS
    val bg = if (isNeeds) NeedsBlueSoft else WantsAmberSoft
    val fg = if (isNeeds) NeedsBlue else WantsAmber
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = bucket.label,
            color = fg,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun TransactionRow(
    tx: TxItem,
    walletName: String,
    masked: Boolean,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryIconBadge(category = tx.category, type = tx.type, bucket = tx.bucket)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tx.note.ifBlank { tx.category },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (tx.type == TxType.EXPENSE) {
                    Spacer(Modifier.width(6.dp))
                    BucketPill(tx.bucket)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${tx.category} • $walletName • ${tx.date}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        val amountColor = when (tx.type) {
            TxType.INCOME -> EmeraldPrimary
            TxType.SAVING -> InfoBlue
            TxType.EXPENSE -> MaterialTheme.colorScheme.onSurface
        }
        val prefix = when (tx.type) {
            TxType.INCOME -> "+"
            TxType.EXPENSE -> "-"
            TxType.SAVING -> ""
        }
        Text(
            text = if (masked) "Rp ••••" else "$prefix${formatRp(tx.amount)}",
            style = MaterialTheme.typography.bodyMedium.tabular().copy(fontWeight = FontWeight.Bold),
            color = amountColor
        )
    }
}

@Composable
fun CustomNumpad(
    onDigit: (String) -> Unit,
    onTripleZero: () -> Unit,
    onBackspace: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("000", "0", "DEL")
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        for (row in rows) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                for (key in row) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                when (key) {
                                    "DEL" -> onBackspace()
                                    "000" -> onTripleZero()
                                    else -> onDigit(key)
                                }
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (key == "DEL") {
                                Icon(
                                    imageVector = Icons.Rounded.Backspace,
                                    contentDescription = "Hapus",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Text(
                                    text = key,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
