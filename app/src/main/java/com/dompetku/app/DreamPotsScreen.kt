package com.dompetku.app

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID
import kotlin.math.ceil

@Composable
fun DreamPotsScreen(
    state: AppState,
    onUpdateState: (AppState) -> Unit,
    onTopUpGoal: (goalId: String) -> Unit
) {
    var showAddGoal by remember { mutableStateOf(false) }
    var selectedSimGoalId by remember(state.goals) {
        mutableStateOf(state.goals.firstOrNull()?.id)
    }
    var extraDaily by remember { mutableFloatStateOf(15_000f) }

    val simGoal = state.goals.firstOrNull { it.id == selectedSimGoalId } ?: state.goals.firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Pot Impian",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Pisahkan tabungan & simulasikan target barangmu",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = { showAddGoal = true },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Target", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Interactive Dream Simulator Card
        if (simGoal != null) {
            item {
                val remaining = (simGoal.target - simGoal.saved).coerceAtLeast(0L)
                val baseDaily = simGoal.dailyPlan.coerceAtLeast(1_000L)
                val extra = (extraDaily.toLong() / 1_000L) * 1_000L
                val simDaily = baseDaily + extra

                val baseDays = if (remaining == 0L) 0 else ceil(remaining.toDouble() / baseDaily).toInt()
                val simDays = if (remaining == 0L) 0 else ceil(remaining.toDouble() / simDaily).toInt()
                val savedDays = (baseDays - simDays).coerceAtLeast(0)

                MinimalCard(containerColor = MaterialTheme.colorScheme.surface) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(EmeraldSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Simulasi Percepatan Target",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Kurangi jajan (Wants) untuk beli lebih cepat",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (state.goals.size > 1) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (g in state.goals) {
                                FilterChip(
                                    selected = g.id == simGoal.id,
                                    onClick = { selectedSimGoalId = g.id },
                                    label = { Text(g.name, fontSize = 12.sp) }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Ekstra nabung/hari",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "+${formatRp(extra)} / hari",
                            style = MaterialTheme.typography.bodyMedium.tabular().copy(fontWeight = FontWeight.Bold),
                            color = EmeraldPrimary
                        )
                    }

                    Slider(
                        value = extraDaily,
                        onValueChange = { extraDaily = it },
                        valueRange = 0f..100_000f,
                        steps = 19,
                        colors = SliderDefaults.colors(
                            thumbColor = EmeraldPrimary,
                            activeTrackColor = EmeraldPrimary
                        )
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (remaining == 0L) {
                                Text(
                                    text = "Target \"${simGoal.name}\" sudah tercapai!",
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldPrimary,
                                    fontSize = 13.sp
                                )
                            } else {
                                Text(
                                    text = "Tercapai dalam $simDays hari (total ${formatRp(simDaily)}/hari)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = if (savedDays > 0)
                                        "Lebih cepat $savedDays hari dibanding rencana awal ($baseDays hari)."
                                    else
                                        "Geser slider untuk melihat percepatan waktu tercapai.",
                                    fontSize = 12.sp,
                                    color = if (savedDays > 0) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // List of Dream Pots
        items(state.goals, key = { it.id }) { goal ->
            val progress = if (goal.target > 0) (goal.saved.toFloat() / goal.target.toFloat()).coerceIn(0f, 1f) else 0f
            val pct = (progress * 100).toInt()
            val remaining = (goal.target - goal.saved).coerceAtLeast(0L)

            MinimalCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = goal.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (remaining == 0L) "Target tercapai!" else "Kurang ${formatRp(remaining, state.hideBalance)}",
                            fontSize = 12.sp,
                            color = if (remaining == 0L) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "$pct%",
                        style = MaterialTheme.typography.titleMedium.tabular().copy(fontWeight = FontWeight.ExtraBold),
                        color = EmeraldPrimary
                    )
                }

                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(99.dp)),
                    color = EmeraldPrimary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${formatRp(goal.saved, state.hideBalance)} / ${formatRp(goal.target, state.hideBalance)}",
                        style = MaterialTheme.typography.bodySmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (state.goals.size > 1) {
                            IconButton(
                                onClick = {
                                    onUpdateState(state.copy(goals = state.goals.filterNot { it.id == goal.id }))
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Hapus",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Button(
                            onClick = { onTopUpGoal(goal.id) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("+ Nabung", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (showAddGoal) {
        var name by remember { mutableStateOf("") }
        var target by remember { mutableStateOf("") }
        var dailyPlan by remember { mutableStateOf("20000") }

        AlertDialog(
            onDismissRequest = { showAddGoal = false },
            title = { Text("Tambah Target Impian", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama Barang / Target") },
                        placeholder = { Text("Misal: Headphone Sony") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = target,
                        onValueChange = { target = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Harga Target (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dailyPlan,
                        onValueChange = { dailyPlan = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Rencana Nabung/Hari (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val tVal = target.toLongOrNull() ?: 0L
                        if (name.isNotBlank() && tVal > 0L) {
                            val newGoal = SavingsGoal(
                                id = "goal_${UUID.randomUUID()}",
                                name = name.trim(),
                                target = tVal,
                                saved = 0L,
                                dailyPlan = (dailyPlan.toLongOrNull() ?: 20_000L).coerceAtLeast(1_000L)
                            )
                            onUpdateState(state.copy(goals = state.goals + newGoal))
                            showAddGoal = false
                        }
                    }
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddGoal = false }) { Text("Batal") }
            }
        )
    }
}
