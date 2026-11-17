package com.dompetku.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {

    private var onJsonImported: ((String) -> Unit)? = null

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ReminderReceiver.scheduleDailyReminders(this)
            ReminderReceiver.updateOngoingBudgetNotification(this, FinanceRepo.load(this))
        }
    }

    private val importJsonLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (!text.isNullOrBlank()) {
                onJsonImported?.invoke(text)
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Gagal membaca file", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ReminderReceiver.ensureChannel(this)
        val initialState = FinanceRepo.load(this)
        val prefs = getSharedPreferences(ReminderReceiver.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(ReminderReceiver.KEY_ENABLED, true)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    ReminderReceiver.scheduleDailyReminders(this)
                    ReminderReceiver.updateOngoingBudgetNotification(this, initialState)
                } else {
                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                ReminderReceiver.scheduleDailyReminders(this)
                ReminderReceiver.updateOngoingBudgetNotification(this, initialState)
            }
        }

        setContent {
            var appState by remember { mutableStateOf(initialState) }
            var unlocked by remember { mutableStateOf(initialState.pin == null) }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        appState = FinanceRepo.load(this@MainActivity)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            fun updateState(newState: AppState) {
                appState = newState
                FinanceRepo.save(this@MainActivity, newState)
                ReminderReceiver.updateOngoingBudgetNotification(this@MainActivity, newState)
            }

            onJsonImported = { raw ->
                val parsed = FinanceRepo.parseJson(raw)
                if (parsed != null) {
                    updateState(parsed)
                    Toast.makeText(this, "Data berhasil dipulihkan", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "File cadangan tidak valid", Toast.LENGTH_SHORT).show()
                }
            }

            DompetKuTheme(darkTheme = appState.darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!unlocked && appState.pin != null) {
                        PinLockScreen(
                            expectedPin = appState.pin!!,
                            onUnlocked = { unlocked = true }
                        )
                    } else {
                        DompetKuApp(
                            state = appState,
                            onUpdateState = ::updateState,
                            onExportJson = {
                                exportBackupFile(FinanceRepo.toJson(appState))
                            },
                            onImportJson = {
                                importJsonLauncher.launch("*/*")
                            },
                            onResetAll = {
                                FinanceRepo.clearAll(this@MainActivity)
                                val fresh = AppState(
                                    darkTheme = appState.darkTheme,
                                    dailyLimit = 20_000L,
                                    lunchLimit = 17_000L,
                                    goals = emptyList(),
                                    bans = emptyList()
                                )
                                updateState(fresh)
                                Toast.makeText(this, "Semua data dihapus bersih", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun exportBackupFile(jsonContent: String) {
        try {
            val dir = File(cacheDir, "backups").apply { mkdirs() }
            val file = File(dir, "dompetku-backup-${todayISO()}.json")
            file.writeText(jsonContent, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Simpan atau Bagikan Cadangan"))
        } catch (_: Exception) {
            Toast.makeText(this, "Gagal mengekspor data", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun PinLockScreen(expectedPin: String, onUnlocked: () -> Unit) {
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text("Masukkan PIN DompetKu", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Lindungi catatan keuanganmu", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { idx ->
                val filled = idx < entered.length
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(
                            if (filled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("C", "0", "⌫")
        )
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                row.forEach { key ->
                    FilledTonalButton(
                        onClick = {
                            when (key) {
                                "C" -> { entered = ""; error = "" }
                                "⌫" -> if (entered.isNotEmpty()) entered = entered.dropLast(1)
                                else -> if (entered.length < 4) {
                                    entered += key
                                    if (entered.length == 4) {
                                        if (entered == expectedPin) {
                                            onUnlocked()
                                        } else {
                                            error = "PIN salah, coba lagi"
                                            entered = ""
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.size(72.dp, 56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(key, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DompetKuApp(
    state: AppState,
    onUpdateState: (AppState) -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
    onResetAll: () -> Unit
) {
    val context = LocalContext.current
    var currentTab by remember { mutableIntStateOf(0) }
    var showAddTxSheet by remember { mutableStateOf(false) }
    var addTxInitialType by remember { mutableStateOf(TxType.EXPENSE) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    // Pending transaction blocked by strict daily limit or ban
    var blockedTxPrompt by remember { mutableStateOf<Pair<TxItem, String>?>(null) }

    val today = todayISO()
    val todaySpent = state.transactions
        .filter { it.type == TxType.EXPENSE && it.date == today }
        .sumOf { it.amount }
    val balance = state.transactions.sumOf { if (it.type == TxType.INCOME) it.amount else -it.amount }

    fun handleTrySaveTx(tx: TxItem) {
        if (tx.type == TxType.EXPENSE) {
            val reasons = mutableListOf<String>()
            if (state.dailyLimit > 0 && tx.date == today && todaySpent + tx.amount > state.dailyLimit) {
                reasons.add("Pengeluaran hari ini akan melebihi batas maksimal ${formatRp(state.dailyLimit)}.")
            }
            for (ban in state.bans.filter { it.active }) {
                val text = "${tx.note} ${tx.category}".lowercase()
                val matches = (ban.category.isNotEmpty() && tx.category == ban.category) ||
                    ban.keywords.any { it.isNotBlank() && it.lowercase() in text }
                if (matches) {
                    if (ban.dailyMax == 0L) {
                        reasons.add("Melanggar larangan: \"${ban.name}\".")
                    } else {
                        val banSpentToday = state.transactions
                            .filter { t ->
                                t.type == TxType.EXPENSE && t.date == tx.date && (
                                    (ban.category.isNotEmpty() && t.category == ban.category) ||
                                        ban.keywords.any { kw -> kw.isNotBlank() && kw.lowercase() in "${t.note} ${t.category}".lowercase() }
                                )
                            }
                            .sumOf { it.amount }
                        if (banSpentToday + tx.amount > ban.dailyMax) {
                            reasons.add("Melebihi batas \"${ban.name}\" (${formatRp(ban.dailyMax)}/hari).")
                        }
                    }
                }
            }
            if (reasons.isNotEmpty()) {
                blockedTxPrompt = tx to reasons.joinToString("\n")
                return
            }
        }
        onUpdateState(state.copy(transactions = state.transactions + tx))
        showAddTxSheet = false
        Toast.makeText(context, "Transaksi disimpan", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("DompetKu", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Text(
                            SimpleDateFormat("EEEE, d MMM yyyy", Locale("id", "ID")).format(Date()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onUpdateState(state.copy(hideBalance = !state.hideBalance)) }) {
                        Icon(
                            if (state.hideBalance) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "Mode Privasi"
                        )
                    }
                    IconButton(onClick = { onUpdateState(state.copy(darkTheme = !state.darkTheme)) }) {
                        Icon(
                            if (state.darkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                            contentDescription = "Tema"
                        )
                    }
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Pengaturan")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                val items = listOf(
                    Triple(0, "Beranda", Icons.Rounded.Home),
                    Triple(1, "Riwayat", Icons.Rounded.ReceiptLong),
                    Triple(2, "Target", Icons.Rounded.Savings),
                    Triple(3, "Kontrol", Icons.Rounded.Shield)
                )
                items.forEach { (idx, label, icon) ->
                    NavigationBarItem(
                        selected = currentTab == idx,
                        onClick = { currentTab = idx },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    addTxInitialType = TxType.EXPENSE
                    showAddTxSheet = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Catat Transaksi")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (currentTab) {
                0 -> HomeScreen(
                    state = state,
                    balance = balance,
                    todaySpent = todaySpent,
                    onQuickAdd = { type ->
                        addTxInitialType = type
                        showAddTxSheet = true
                    },
                    onCookToday = {
                        if (today !in state.cookDays) {
                            onUpdateState(
                                state.copy(
                                    cookDays = state.cookDays + today,
                                    cookSavedTotal = state.cookSavedTotal + 25_000L
                                )
                            )
                            Toast.makeText(context, "Mantap! Hemat Rp 25.000 hari ini", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSaveLeftover = { amount ->
                        val goal = state.goals.firstOrNull() ?: return@HomeScreen
                        val updatedGoals = state.goals.map {
                            if (it.id == goal.id) it.copy(saved = it.saved + amount) else it
                        }
                        val tx = TxItem(
                            id = UUID.randomUUID().toString(),
                            type = TxType.SAVING,
                            amount = amount,
                            category = "Tabungan",
                            note = "Sisa jatah harian ke ${goal.name}",
                            date = today,
                            createdAt = System.currentTimeMillis()
                        )
                        onUpdateState(state.copy(goals = updatedGoals, transactions = state.transactions + tx))
                        Toast.makeText(context, "Sisa ${formatRp(amount)} ditabung!", Toast.LENGTH_SHORT).show()
                    },
                    onOpenTab = { currentTab = it }
                )
                1 -> HistoryScreen(
                    state = state,
                    onDeleteTx = { id ->
                        onUpdateState(state.copy(transactions = state.transactions.filterNot { it.id == id }))
                    }
                )
                2 -> GoalsScreen(
                    state = state,
                    onAddGoal = { name, target ->
                        val g = SavingsGoal(UUID.randomUUID().toString(), name, target, 0L)
                        onUpdateState(state.copy(goals = state.goals + g))
                    },
                    onDeposit = { goalId, amount, deduct ->
                        val goal = state.goals.firstOrNull { it.id == goalId } ?: return@GoalsScreen
                        val updatedGoals = state.goals.map {
                            if (it.id == goalId) it.copy(saved = it.saved + amount) else it
                        }
                        val newTxs = if (deduct) {
                            state.transactions + TxItem(
                                id = UUID.randomUUID().toString(),
                                type = TxType.SAVING,
                                amount = amount,
                                category = "Tabungan",
                                note = "Setor ke ${goal.name}",
                                date = today,
                                createdAt = System.currentTimeMillis()
                            )
                        } else state.transactions
                        onUpdateState(state.copy(goals = updatedGoals, transactions = newTxs))
                        Toast.makeText(context, "Berhasil menabung ${formatRp(amount)}", Toast.LENGTH_SHORT).show()
                    },
                    onDeleteGoal = { id ->
                        onUpdateState(state.copy(goals = state.goals.filterNot { it.id == id }))
                    }
                )
                3 -> ControlScreen(
                    state = state,
                    onSaveLimits = { daily, lunch, strict ->
                        onUpdateState(state.copy(dailyLimit = daily, lunchLimit = lunch, strictLock = strict))
                        Toast.makeText(context, "Batas harian diperbarui", Toast.LENGTH_SHORT).show()
                    },
                    onAddBan = { name, keywords, category, dailyMax ->
                        val b = SpendingBan(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            keywords = keywords,
                            category = category,
                            dailyMax = dailyMax,
                            active = true,
                            createdAt = today,
                            lastBrokenDate = null
                        )
                        onUpdateState(state.copy(bans = state.bans + b))
                    },
                    onToggleBan = { id ->
                        onUpdateState(
                            state.copy(bans = state.bans.map { if (it.id == id) it.copy(active = !it.active) else it })
                        )
                    },
                    onDeleteBan = { id ->
                        onUpdateState(state.copy(bans = state.bans.filterNot { it.id == id }))
                    }
                )
            }
        }
    }

    if (showAddTxSheet) {
        AddTransactionSheet(
            initialType = addTxInitialType,
            onDismiss = { showAddTxSheet = false },
            onSave = ::handleTrySaveTx
        )
    }

    if (showSettingsSheet) {
        SettingsDialog(
            state = state,
            onDismiss = { showSettingsSheet = false },
            onUpdateState = onUpdateState,
            onExportJson = onExportJson,
            onImportJson = onImportJson,
            onClearTx = {
                onUpdateState(state.copy(transactions = emptyList()))
                Toast.makeText(context, "Riwayat transaksi dihapus", Toast.LENGTH_SHORT).show()
            },
            onResetAll = {
                showSettingsSheet = false
                onResetAll()
            }
        )
    }

    blockedTxPrompt?.let { (tx, reason) ->
        AlertDialog(
            onDismissRequest = { blockedTxPrompt = null },
            icon = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = DangerRed) },
            title = { Text("Tahan Pengeluaran Ini!", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(reason)
                    if (state.strictLock) {
                        Text(
                            "Mode Kunci Ketat aktif. Lebih baik batalkan dan alihkan ${formatRp(tx.amount)} ke target tabunganmu.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val goal = state.goals.firstOrNull()
                        if (goal != null) {
                            val updatedGoals = state.goals.map {
                                if (it.id == goal.id) it.copy(saved = it.saved + tx.amount) else it
                            }
                            val saveTx = tx.copy(
                                type = TxType.SAVING,
                                category = "Tabungan",
                                note = "Dialihkan dari jajan ke ${goal.name}"
                            )
                            onUpdateState(state.copy(goals = updatedGoals, transactions = state.transactions + saveTx))
                            Toast.makeText(context, "${formatRp(tx.amount)} dialihkan ke ${goal.name}", Toast.LENGTH_SHORT).show()
                        }
                        blockedTxPrompt = null
                        showAddTxSheet = false
                    }
                ) {
                    Text("Batal & Tabungkan")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { blockedTxPrompt = null; showAddTxSheet = false }) {
                        Text("Batal")
                    }
                    if (!state.strictLock) {
                        TextButton(
                            onClick = {
                                onUpdateState(state.copy(transactions = state.transactions + tx))
                                blockedTxPrompt = null
                                showAddTxSheet = false
                            }
                        ) {
                            Text("Tetap Catat", color = DangerRed)
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun HomeScreen(
    state: AppState,
    balance: Long,
    todaySpent: Long,
    onQuickAdd: (TxType) -> Unit,
    onCookToday: () -> Unit,
    onSaveLeftover: (Long) -> Unit,
    onOpenTab: (Int) -> Unit
) {
    val today = todayISO()
    val monthPrefix = today.take(7)
    val monthIncome = state.transactions.filter { it.type == TxType.INCOME && it.date.startsWith(monthPrefix) }.sumOf { it.amount }
    val monthExpense = state.transactions.filter { it.type == TxType.EXPENSE && it.date.startsWith(monthPrefix) }.sumOf { it.amount }

    val dailyLimit = state.dailyLimit
    val remainingToday = (dailyLimit - todaySpent).coerceAtLeast(0L)
    val overLimit = dailyLimit > 0 && todaySpent > dailyLimit
    val progress = if (dailyLimit > 0) (todaySpent.toFloat() / dailyLimit.toFloat()).coerceIn(0f, 1f) else 0f

    val cookedToday = today in state.cookDays

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. HERO: SISA JATAH HARI INI
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (overLimit) Color(0xFF2B1215) else MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (overLimit) DangerRed.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Sisa Jatah Hari Ini",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (overLimit) DangerRed.copy(alpha = 0.18f) else EmeraldPrimary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                if (overLimit) "Batas Terlewati" else if (state.strictLock) "Kunci Ketat Aktif" else "Aman",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (overLimit) DangerRed else EmeraldPrimary
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (dailyLimit > 0) formatRp(remainingToday, state.hideBalance) else "Belum diatur",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (overLimit) DangerRed else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
                        color = if (overLimit) DangerRed else if (progress >= 0.8f) WarnAmber else EmeraldPrimary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "Terpakai ${formatRp(todaySpent, state.hideBalance)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Maks ${formatRp(dailyLimit)} • Siang ${formatRp(state.lunchLimit)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (dailyLimit > 0 && remainingToday > 0 && state.goals.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(
                            onClick = { onSaveLeftover(remainingToday) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Savings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Tabungkan Sisa ${formatRp(remainingToday)}")
                        }
                    }
                }
            }
        }

        // 2. WALLET BALANCE + QUICK ACTIONS
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Saldo Dompet", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(formatRp(balance, state.hideBalance), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Masuk bulan ini", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatRp(monthIncome, state.hideBalance), fontWeight = FontWeight.SemiBold, color = EmeraldPrimary)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Keluar bulan ini", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatRp(monthExpense, state.hideBalance), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(
                            onClick = { onQuickAdd(TxType.INCOME) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pemasukan")
                        }
                        Button(
                            onClick = { onQuickAdd(TxType.EXPENSE) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pengeluaran")
                        }
                    }
                }
            }
        }

        // 3. PRIMARY SAVINGS GOAL
        item {
            val goal = state.goals.firstOrNull()
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Target Tabungan Utama", fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { onOpenTab(2) }) { Text("Kelola") }
                    }
                    if (goal == null) {
                        Text(
                            "Belum ada target tabungan. Buat di tab Target.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val pct = ((goal.saved.toFloat() / goal.target.coerceAtLeast(1L)) * 100).toInt().coerceIn(0, 100)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(goal.name, fontWeight = FontWeight.Medium)
                            Text("$pct%", fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { pct / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
                            color = EmeraldPrimary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${formatRp(goal.saved, state.hideBalance)} dari ${formatRp(goal.target)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 4. COOKING CHALLENGE
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Masak Malam Ini", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Total hemat ${formatRp(state.cookSavedTotal, state.hideBalance)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldPrimary
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Jangan jajan malam! Masak sendiri di rumah dan simpan Rp 25.000.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = onCookToday,
                        enabled = !cookedToday,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            if (cookedToday) Icons.Rounded.CheckCircle else Icons.Rounded.Restaurant,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (cookedToday) "Sudah Masak Hari Ini" else "Saya Masak Hari Ini (+Hemat Rp 25rb)")
                    }
                }
            }
        }

        // 5. RECENT ACTIVITY
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Aktivitas Terakhir", fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { onOpenTab(1) }) { Text("Semua") }
                    }
                    val recent = state.transactions.sortedByDescending { it.createdAt }.take(5)
                    if (recent.isEmpty()) {
                        Text(
                            "Belum ada transaksi. Tekan + untuk mencatat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        recent.forEachIndexed { idx, tx ->
                            TxRow(tx = tx, masked = state.hideBalance, onDelete = null)
                            if (idx < recent.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TxRow(tx: TxItem, masked: Boolean, onDelete: (() -> Unit)?) {
    val icon = when (tx.type) {
        TxType.INCOME -> Icons.Rounded.ArrowDownward
        TxType.EXPENSE -> Icons.Rounded.ArrowUpward
        TxType.SAVING -> Icons.Rounded.Savings
    }
    val tint = when (tx.type) {
        TxType.INCOME -> EmeraldPrimary
        TxType.EXPENSE -> MaterialTheme.colorScheme.onSurface
        TxType.SAVING -> InfoBlue
    }
    val sign = if (tx.type == TxType.INCOME) "+" else "-"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tx.note.ifBlank { tx.category },
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${tx.category} • ${tx.date}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "$sign${formatRp(tx.amount, masked)}",
            fontWeight = FontWeight.SemiBold,
            color = tint
        )
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun HistoryScreen(state: AppState, onDeleteTx: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<TxType?>(null) }

    val filtered = state.transactions
        .filter { tx ->
            (filter == null || tx.type == filter) &&
                (query.isBlank() || tx.note.contains(query, true) || tx.category.contains(query, true))
        }
        .sortedByDescending { it.createdAt }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Cari transaksi...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Semua") })
                FilterChip(selected = filter == TxType.INCOME, onClick = { filter = TxType.INCOME }, label = { Text("Masuk") })
                FilterChip(selected = filter == TxType.EXPENSE, onClick = { filter = TxType.EXPENSE }, label = { Text("Keluar") })
                FilterChip(selected = filter == TxType.SAVING, onClick = { filter = TxType.SAVING }, label = { Text("Tabungan") })
            }
        }
        if (filtered.isEmpty()) {
            item {
                Text(
                    "Belum ada transaksi yang cocok.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        } else {
            items(filtered, key = { it.id }) { tx ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 14.dp)) {
                        TxRow(tx = tx, masked = state.hideBalance, onDelete = { onDeleteTx(tx.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun GoalsScreen(
    state: AppState,
    onAddGoal: (String, Long) -> Unit,
    onDeposit: (String, Long, Boolean) -> Unit,
    onDeleteGoal: (String) -> Unit
) {
    var showNewGoal by remember { mutableStateOf(false) }
    var depositGoal by remember { mutableStateOf<SavingsGoal?>(null) }

    val totalSaved = state.goals.sumOf { it.saved }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Total Tabungan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatRp(totalSaved, state.hideBalance), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                    }
                    Button(onClick = { showNewGoal = true }, shape = RoundedCornerShape(12.dp)) {
                        Text("+ Target")
                    }
                }
            }
        }

        items(state.goals, key = { it.id }) { g ->
            val pct = ((g.saved.toFloat() / g.target.coerceAtLeast(1L)) * 100).toInt().coerceIn(0, 100)
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(g.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("$pct%", fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { pct / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
                        color = EmeraldPrimary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatRp(g.saved, state.hideBalance), fontWeight = FontWeight.Medium)
                        Text("Target ${formatRp(g.target)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { depositGoal = g }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                            Text("Nabung")
                        }
                        OutlinedButton(onClick = { onDeleteGoal(g.id) }, shape = RoundedCornerShape(10.dp)) {
                            Text("Hapus")
                        }
                    }
                }
            }
        }
    }

    if (showNewGoal) {
        var name by remember { mutableStateOf("") }
        var targetStr by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewGoal = false },
            title = { Text("Target Tabungan Baru") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Target") }, singleLine = true)
                    OutlinedTextField(
                        value = targetStr,
                        onValueChange = { targetStr = it.filter(Char::isDigit) },
                        label = { Text("Nominal Target (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val t = targetStr.toLongOrNull() ?: 0L
                    if (name.isNotBlank() && t > 0) {
                        onAddGoal(name.trim(), t)
                        showNewGoal = false
                    }
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showNewGoal = false }) { Text("Batal") } }
        )
    }

    depositGoal?.let { g ->
        var amtStr by remember { mutableStateOf("") }
        var deduct by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { depositGoal = null },
            title = { Text("Nabung: ${g.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = amtStr,
                        onValueChange = { amtStr = it.filter(Char::isDigit) },
                        label = { Text("Jumlah Setoran (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { deduct = !deduct }) {
                        Checkbox(checked = deduct, onCheckedChange = { deduct = it })
                        Text("Potong dari saldo utama", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val a = amtStr.toLongOrNull() ?: 0L
                    if (a > 0) {
                        onDeposit(g.id, a, deduct)
                        depositGoal = null
                    }
                }) { Text("Setor") }
            },
            dismissButton = { TextButton(onClick = { depositGoal = null }) { Text("Batal") } }
        )
    }
}

@Composable
fun ControlScreen(
    state: AppState,
    onSaveLimits: (Long, Long, Boolean) -> Unit,
    onAddBan: (String, List<String>, String, Long) -> Unit,
    onToggleBan: (String) -> Unit,
    onDeleteBan: (String) -> Unit
) {
    var dailyStr by remember(state.dailyLimit) { mutableStateOf(state.dailyLimit.toString()) }
    var lunchStr by remember(state.lunchLimit) { mutableStateOf(state.lunchLimit.toString()) }
    var strict by remember(state.strictLock) { mutableStateOf(state.strictLock) }
    var showNewBan by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Kontrol Pengeluaran Harian", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = dailyStr,
                            onValueChange = { dailyStr = it.filter(Char::isDigit) },
                            label = { Text("Maks Harian (Rp)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = lunchStr,
                            onValueChange = { lunchStr = it.filter(Char::isDigit) },
                            label = { Text("Maks Siang (Rp)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mode Kunci Ketat", fontWeight = FontWeight.Medium)
                            Text(
                                "Blokir pencatatan pengeluaran jika melewati batas harian/larangan",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = strict, onCheckedChange = { strict = it })
                    }
                    Button(
                        onClick = {
                            onSaveLimits(
                                dailyStr.toLongOrNull() ?: 0L,
                                lunchStr.toLongOrNull() ?: 0L,
                                strict
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Simpan Aturan Batas")
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Daftar Larangan Jajan", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                TextButton(onClick = { showNewBan = true }) { Text("+ Larangan") }
            }
        }

        items(state.bans, key = { it.id }) { b ->
            val streak = daysBetweenISO(b.lastBrokenDate ?: b.createdAt, todayISO())
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(b.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Switch(checked = b.active, onCheckedChange = { onToggleBan(b.id) })
                    }
                    Text(
                        if (b.dailyMax == 0L) "Blokir Total • Streak $streak hari"
                        else "Maks ${formatRp(b.dailyMax)}/hari • Streak $streak hari",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldPrimary
                    )
                    if (b.keywords.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Kata kunci: ${b.keywords.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { onDeleteBan(b.id) }, contentPadding = PaddingValues(0.dp)) {
                        Text("Hapus Larangan", color = DangerRed)
                    }
                }
            }
        }
    }

    if (showNewBan) {
        var name by remember { mutableStateOf("") }
        var kws by remember { mutableStateOf("") }
        var maxStr by remember { mutableStateOf("0") }
        AlertDialog(
            onDismissRequest = { showNewBan = false },
            title = { Text("Tambah Larangan Jajan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Larangan") }, singleLine = true)
                    OutlinedTextField(value = kws, onValueChange = { kws = it }, label = { Text("Kata kunci (pisahkan koma)") }, singleLine = true)
                    OutlinedTextField(
                        value = maxStr,
                        onValueChange = { maxStr = it.filter(Char::isDigit) },
                        label = { Text("Maks/hari (0 = blokir total)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        onAddBan(
                            name.trim(),
                            kws.split(",").map { it.trim() }.filter { it.isNotBlank() },
                            "",
                            maxStr.toLongOrNull() ?: 0L
                        )
                        showNewBan = false
                    }
                }) { Text("Aktifkan") }
            },
            dismissButton = { TextButton(onClick = { showNewBan = false }) { Text("Batal") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(
    initialType: TxType,
    onDismiss: () -> Unit,
    onSave: (TxItem) -> Unit
) {
    var type by remember { mutableStateOf(initialType) }
    var amountStr by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(if (initialType == TxType.INCOME) "Gaji / Uang Saku" else "Makan & Minum") }
    var note by remember { mutableStateOf("") }

    val categories = if (type == TxType.INCOME) {
        listOf("Gaji / Uang Saku", "Bonus / Hadiah", "Freelance", "Lainnya")
    } else {
        listOf("Makan & Minum", "Jajan & Minuman", "Transportasi", "Belanja Online", "Tagihan & Pulsa", "Lainnya")
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Catat Transaksi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == TxType.EXPENSE,
                    onClick = { type = TxType.EXPENSE; category = "Makan & Minum" },
                    label = { Text("Pengeluaran") }
                )
                FilterChip(
                    selected = type == TxType.INCOME,
                    onClick = { type = TxType.INCOME; category = "Gaji / Uang Saku" },
                    label = { Text("Pemasukan") }
                )
            }
            OutlinedTextField(
                value = amountStr,
                onValueChange = { amountStr = it.filter(Char::isDigit) },
                label = { Text("Nominal (Rp)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10_000L to "10rb", 15_000L to "15rb", 17_000L to "17rb", 20_000L to "20rb").forEach { (amt, lbl) ->
                    SuggestionChip(
                        onClick = { amountStr = amt.toString() },
                        label = { Text(lbl) }
                    )
                }
            }
            // Category chips
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.take(3).forEach { cat ->
                    FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat, fontSize = 11.sp) })
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.drop(3).forEach { cat ->
                    FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat, fontSize = 11.sp) })
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Catatan (misal: makan siang warteg)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val amt = amountStr.toLongOrNull() ?: 0L
                    if (amt > 0) {
                        onSave(
                            TxItem(
                                id = UUID.randomUUID().toString(),
                                type = type,
                                amount = amt,
                                category = category,
                                note = note.trim(),
                                date = todayISO(),
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Simpan Transaksi")
            }
        }
    }
}

@Composable
fun SettingsDialog(
    state: AppState,
    onDismiss: () -> Unit,
    onUpdateState: (AppState) -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
    onClearTx: () -> Unit,
    onResetAll: () -> Unit
) {
    val context = LocalContext.current
    var showPinInput by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pengaturan & Data", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Status Jatah di Panel Notif", fontWeight = FontWeight.Medium)
                        Text("Tampilkan sisa Rp 20rb di bar notifikasi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = state.ongoingBar,
                        onCheckedChange = { onUpdateState(state.copy(ongoingBar = it)) }
                    )
                }

                OutlinedButton(
                    onClick = { ReminderReceiver.testNotification(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tes Notifikasi Sekarang")
                }

                HorizontalDivider()

                OutlinedButton(onClick = { showPinInput = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.pin == null) "Pasang PIN 4 Digit" else "Ubah / Hapus PIN")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onExportJson, modifier = Modifier.weight(1f)) { Text("Ekspor") }
                    OutlinedButton(onClick = onImportJson, modifier = Modifier.weight(1f)) { Text("Impor") }
                }

                TextButton(onClick = onClearTx, modifier = Modifier.fillMaxWidth()) {
                    Text("Hapus Riwayat Transaksi Saja", color = WarnAmber)
                }
                TextButton(onClick = onResetAll, modifier = Modifier.fillMaxWidth()) {
                    Text("Hapus Semua Data (Reset Total)", color = DangerRed)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )

    if (showPinInput) {
        var newPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinInput = false },
            title = { Text("Atur PIN 4 Digit") },
            text = {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 4) newPin = it.filter(Char::isDigit) },
                    label = { Text("4 angka (kosongkan untuk matikan)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newPin.isEmpty()) {
                        onUpdateState(state.copy(pin = null))
                        showPinInput = false
                    } else if (newPin.length == 4) {
                        onUpdateState(state.copy(pin = newPin))
                        showPinInput = false
                    }
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showPinInput = false }) { Text("Batal") } }
        )
    }
}
