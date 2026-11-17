package com.dompetku.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AppTab(val label: String, val icon: ImageVector) {
    HOME("Beranda", Icons.Rounded.Home),
    HISTORY("Riwayat", Icons.Rounded.ReceiptLong),
    DREAMS("Impian", Icons.Rounded.AutoAwesome),
    CONTROL("Kontrol", Icons.Rounded.CalendarMonth)
}

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
        enableEdgeToEdge()

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
            var currentTab by remember { mutableStateOf(AppTab.HOME) }
            var showQuickAdd by remember { mutableStateOf(false) }
            var quickAddType by remember { mutableStateOf(TxType.EXPENSE) }
            var quickAddGoalId by remember { mutableStateOf<String?>(null) }
            var showSettings by remember { mutableStateOf(false) }

            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()

            LaunchedEffect(appState.darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (appState.darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (appState.darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    }
                )
            }

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
                        Scaffold(
                            containerColor = MaterialTheme.colorScheme.background,
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            floatingActionButton = {
                                FloatingActionButton(
                                    onClick = {
                                        quickAddType = TxType.EXPENSE
                                        quickAddGoalId = null
                                        showQuickAdd = true
                                    },
                                    shape = CircleShape,
                                    containerColor = EmeraldPrimary,
                                    contentColor = Color.White
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = "Catat Transaksi",
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            },
                            bottomBar = {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 0.dp
                                ) {
                                    for (tab in AppTab.entries) {
                                        val selected = currentTab == tab
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { currentTab = tab },
                                            icon = {
                                                Icon(
                                                    imageVector = tab.icon,
                                                    contentDescription = tab.label
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = tab.label,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = EmeraldPrimary,
                                                selectedTextColor = EmeraldPrimary,
                                                indicatorColor = EmeraldSoft
                                            )
                                        )
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                when (currentTab) {
                                    AppTab.HOME -> HomeScreen(
                                        state = appState,
                                        onUpdateState = ::updateState,
                                        onOpenQuickAdd = { type ->
                                            quickAddType = type
                                            quickAddGoalId = null
                                            showQuickAdd = true
                                        },
                                        onOpenSettings = { showSettings = true },
                                        onGoToHistory = { currentTab = AppTab.HISTORY }
                                    )
                                    AppTab.HISTORY -> HistoryScreen(
                                        state = appState,
                                        onUpdateState = ::updateState
                                    )
                                    AppTab.DREAMS -> DreamPotsScreen(
                                        state = appState,
                                        onUpdateState = ::updateState,
                                        onTopUpGoal = { gId ->
                                            quickAddType = TxType.SAVING
                                            quickAddGoalId = gId
                                            showQuickAdd = true
                                        }
                                    )
                                    AppTab.CONTROL -> ControlScreen(
                                        state = appState,
                                        onUpdateState = ::updateState
                                    )
                                }
                            }
                        }

                        if (showQuickAdd) {
                            QuickAddSheet(
                                state = appState,
                                initialType = quickAddType,
                                initialGoalId = quickAddGoalId,
                                onDismiss = { showQuickAdd = false },
                                onSave = { tx, goalId ->
                                    val prevState = appState
                                    val nextState = FinanceEngine.applyTransaction(appState, tx, goalId)
                                    updateState(nextState)
                                    showQuickAdd = false
                                    scope.launch {
                                        val res = snackbarHostState.showSnackbar(
                                            message = "Tersimpan: ${tx.category} (${formatRp(tx.amount)})",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (res == SnackbarResult.ActionPerformed) {
                                            updateState(prevState)
                                        }
                                    }
                                }
                            )
                        }

                        if (showSettings) {
                            SettingsDialog(
                                state = appState,
                                onDismiss = { showSettings = false },
                                onUpdateState = ::updateState,
                                onExportJson = { exportJsonFile(this@MainActivity, appState) },
                                onImportJson = { importJsonLauncher.launch("application/json") },
                                onResetAll = {
                                    FinanceRepo.clearAll(this@MainActivity)
                                    updateState(AppState())
                                    showSettings = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun exportJsonFile(context: Context, state: AppState) {
        try {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val file = File(dir, "dompetku_backup_$stamp.json")
            file.writeText(FinanceRepo.toJson(state))
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Simpan Cadangan DompetKu"))
        } catch (_: Exception) {
            Toast.makeText(context, "Gagal mengekspor cadangan", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun PinLockScreen(expectedPin: String, onUnlocked: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(EmeraldSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Masukkan PIN DompetKu", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (i in 0 until 4) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                error -> DangerRed
                                i < input.length -> EmeraldPrimary
                                else -> MaterialTheme.colorScheme.outline
                            }
                        )
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        CustomNumpad(
            onDigit = { d ->
                error = false
                if (input.length < 4) {
                    val next = input + d
                    input = next
                    if (next.length == 4) {
                        if (next == expectedPin) onUnlocked()
                        else {
                            error = true
                            input = ""
                        }
                    }
                }
            },
            onTripleZero = {},
            onBackspace = {
                if (input.isNotEmpty()) input = input.dropLast(1)
            }
        )
    }
}

@Composable
fun SettingsDialog(
    state: AppState,
    onDismiss: () -> Unit,
    onUpdateState: (AppState) -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
    onResetAll: () -> Unit
) {
    val context = LocalContext.current
    var cycleInput by remember { mutableStateOf(state.cycleStartDay.toString()) }
    var targetSaveInput by remember { mutableStateOf(state.monthlySavingTarget.toString()) }
    var pinInput by remember { mutableStateOf(state.pin ?: "") }
    var confirmReset by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pengaturan", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tema Gelap (Dark Mode)", fontSize = 13.sp)
                    Switch(
                        checked = state.darkTheme,
                        onCheckedChange = { onUpdateState(state.copy(darkTheme = it)) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Notifikasi Bar Harian", fontSize = 13.sp)
                    Switch(
                        checked = state.ongoingBar,
                        onCheckedChange = { enabled ->
                            onUpdateState(state.copy(ongoingBar = enabled))
                            if (!enabled) ReminderReceiver.cancelOngoingBudgetNotification(context)
                        }
                    )
                }

                OutlinedTextField(
                    value = cycleInput,
                    onValueChange = { cycleInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Tanggal Mulai Siklus Bulanan (1-28)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = targetSaveInput,
                    onValueChange = { targetSaveInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Target Nabung Bulanan (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { if (it.length <= 4) pinInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("PIN Kunci 4 Digit (kosong = nonaktif)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onExportJson, modifier = Modifier.weight(1f)) {
                        Text("Backup", fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = onImportJson, modifier = Modifier.weight(1f)) {
                        Text("Restore", fontSize = 12.sp)
                    }
                }

                TextButton(
                    onClick = { confirmReset = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Reset Semua Data", color = DangerRed, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cDay = (cycleInput.toIntOrNull() ?: state.cycleStartDay).coerceIn(1, 28)
                    val tSave = (targetSaveInput.toLongOrNull() ?: state.monthlySavingTarget).coerceAtLeast(0L)
                    val cleanPin = if (pinInput.length == 4) pinInput else null
                    onUpdateState(
                        state.copy(
                            cycleStartDay = cDay,
                            monthlySavingTarget = tSave,
                            pin = cleanPin
                        )
                    )
                    onDismiss()
                }
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset Semua Data?") },
            text = { Text("Semua dompet, transaksi, dan target tabungan akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    onResetAll()
                }) {
                    Text("Ya, Hapus", color = DangerRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Batal") }
            }
        )
    }
}
