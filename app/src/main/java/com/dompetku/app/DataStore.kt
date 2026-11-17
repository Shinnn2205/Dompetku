package com.dompetku.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class TxType { INCOME, EXPENSE, SAVING }

enum class BucketType(val label: String) {
    NEEDS("Needs"),
    WANTS("Wants")
}

enum class WalletType(val label: String) {
    BANK("Bank"),
    EWALLET("E-Wallet"),
    CASH("Tunai")
}

data class WalletItem(
    val id: String,
    val name: String,
    val type: WalletType,
    val balance: Long,
    val includeInBudget: Boolean = true,
    val colorHex: Long = 0xFF0F172A
)

data class CategoryMeta(
    val name: String,
    val bucket: BucketType,
    val iconKey: String
)

data class TxItem(
    val id: String,
    val type: TxType,
    val amount: Long,
    val category: String,
    val note: String,
    val date: String,
    val createdAt: Long,
    val walletId: String = "wallet_ewallet",
    val bucket: BucketType = BucketType.NEEDS,
    val recurringRuleId: String? = null
)

data class SavingsGoal(
    val id: String,
    val name: String,
    val target: Long,
    val saved: Long,
    val dailyPlan: Long = 20_000L
)

data class RecurringRule(
    val id: String,
    val title: String,
    val amount: Long,
    val walletId: String,
    val category: String,
    val dueDay: Int,
    val autoReserve: Boolean = true,
    val lastPaidCycle: String? = null // format "yyyy-MM"
)

data class SpendingBan(
    val id: String,
    val name: String,
    val keywords: List<String>,
    val category: String,
    val dailyMax: Long, // 0 = strict block
    val active: Boolean,
    val createdAt: String,
    val lastBrokenDate: String?
)

data class AppState(
    val darkTheme: Boolean = false,
    val hideBalance: Boolean = false,
    val strictLock: Boolean = false,
    val ongoingBar: Boolean = true,
    val appBlockerEnabled: Boolean = false,
    val dailyLimit: Long = 35_000L,
    val lunchLimit: Long = 17_000L,
    val cycleStartDay: Int = 1,
    val monthlySavingTarget: Long = 300_000L,
    val lastWalletId: String = "wallet_ewallet",
    val pin: String? = null,
    val cookDays: List<String> = emptyList(),
    val cookSavedTotal: Long = 0L,
    val wallets: List<WalletItem> = defaultWallets(),
    val transactions: List<TxItem> = emptyList(),
    val goals: List<SavingsGoal> = defaultGoals(),
    val recurringRules: List<RecurringRule> = defaultRecurringRules(),
    val bans: List<SpendingBan> = defaultBans()
)

fun defaultWallets() = listOf(
    WalletItem(
        id = "wallet_bank",
        name = "BCA / Bank",
        type = WalletType.BANK,
        balance = 0L,
        includeInBudget = true,
        colorHex = 0xFF0F172A
    ),
    WalletItem(
        id = "wallet_ewallet",
        name = "GoPay / QRIS",
        type = WalletType.EWALLET,
        balance = 0L,
        includeInBudget = true,
        colorHex = 0xFF059669
    ),
    WalletItem(
        id = "wallet_cash",
        name = "Uang Tunai",
        type = WalletType.CASH,
        balance = 0L,
        includeInBudget = true,
        colorHex = 0xFF334155
    )
)

fun defaultCategories(): List<CategoryMeta> = listOf(
    CategoryMeta("Makan", BucketType.NEEDS, "restaurant"),
    CategoryMeta("Transport", BucketType.NEEDS, "commute"),
    CategoryMeta("Kos & Tagihan", BucketType.NEEDS, "home"),
    CategoryMeta("Kebutuhan", BucketType.NEEDS, "shopping_bag"),
    CategoryMeta("Kopi & Jajan", BucketType.WANTS, "coffee"),
    CategoryMeta("Nongkrong", BucketType.WANTS, "groups"),
    CategoryMeta("Belanja", BucketType.WANTS, "local_mall"),
    CategoryMeta("Hiburan", BucketType.WANTS, "sports_esports")
)

fun defaultIncomeCategories(): List<CategoryMeta> = listOf(
    CategoryMeta("Uang Bulanan", BucketType.NEEDS, "account_balance_wallet"),
    CategoryMeta("Gaji / Part-time", BucketType.NEEDS, "work"),
    CategoryMeta("Freelance", BucketType.NEEDS, "laptop"),
    CategoryMeta("Hadiah / Bonus", BucketType.WANTS, "card_giftcard")
)

fun inferBucket(category: String): BucketType {
    val match = defaultCategories().firstOrNull { it.name.equals(category, ignoreCase = true) }
    if (match != null) return match.bucket
    val lower = category.lowercase(Locale.getDefault())
    return if (lower.contains("kopi") || lower.contains("jajan") || lower.contains("hiburan") ||
        lower.contains("nongkrong") || lower.contains("game") || lower.contains("belanja")
    ) {
        BucketType.WANTS
    } else {
        BucketType.NEEDS
    }
}

fun defaultGoals() = listOf(
    SavingsGoal(
        id = "goal_default_1",
        name = "Dana Darurat Pertama",
        target = 1_500_000L,
        saved = 0L,
        dailyPlan = 20_000L
    ),
    SavingsGoal(
        id = "goal_dream_laptop",
        name = "Upgrade Gadget / Laptop",
        target = 5_000_000L,
        saved = 0L,
        dailyPlan = 35_000L
    )
)

fun defaultRecurringRules() = listOf(
    RecurringRule(
        id = "rec_kos",
        title = "Bayar Kos Bulanan",
        amount = 800_000L,
        walletId = "wallet_bank",
        category = "Kos & Tagihan",
        dueDay = 5,
        autoReserve = false,
        lastPaidCycle = null
    ),
    RecurringRule(
        id = "rec_kuota",
        title = "Paket Data & WiFi",
        amount = 100_000L,
        walletId = "wallet_ewallet",
        category = "Kos & Tagihan",
        dueDay = 10,
        autoReserve = false,
        lastPaidCycle = null
    )
)

fun defaultBans() = listOf(
    SpendingBan(
        id = "ban_lunch_17k",
        name = "Makan Siang Hemat Maks Rp 17.000",
        keywords = listOf("siang", "makan siang", "nasi", "warteg", "ayam", "mie"),
        category = "Makan",
        dailyMax = 17_000L,
        active = false,
        createdAt = todayISO(),
        lastBrokenDate = null
    ),
    SpendingBan(
        id = "ban_snack_strict",
        name = "Stop Jajan & Kopi Kekinian",
        keywords = listOf("boba", "kopi", "es", "jajan", "snack", "cimol", "seblak", "gofood"),
        category = "Kopi & Jajan",
        dailyMax = 0L,
        active = false,
        createdAt = todayISO(),
        lastBrokenDate = null
    )
)

fun todayISO(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

fun currentMonthKey(): String =
    SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

fun offsetDateISO(daysAgo: Int): String {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
}

fun formatRp(amount: Long, masked: Boolean = false): String {
    if (masked) return "Rp ••••••"
    val nf = NumberFormat.getNumberInstance(Locale("id", "ID"))
    val sign = if (amount < 0) "-" else ""
    return "${sign}Rp ${nf.format(kotlin.math.abs(amount))}"
}

fun formatRpCompact(amount: Long): String {
    val abs = kotlin.math.abs(amount)
    val sign = if (amount < 0) "-" else ""
    return when {
        abs >= 1_000_000L -> {
            val jt = abs / 1_000_000.0
            "${sign}Rp ${String.format(Locale("id", "ID"), "%.1f", jt)}jt"
        }
        abs >= 1_000L -> "${sign}Rp ${abs / 1_000L}rb"
        else -> "${sign}Rp $abs"
    }
}

fun daysBetweenISO(start: String, end: String): Int {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val a = sdf.parse(start)?.time ?: return 0
        val b = sdf.parse(end)?.time ?: return 0
        ((b - a) / 86_400_000L).toInt().coerceAtLeast(0)
    } catch (_: Exception) {
        0
    }
}

object FinanceRepo {
    private const val PREFS = "dompetku_native_v1"
    private const val KEY_STATE = "state_json"

    fun load(context: Context): AppState {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_STATE, null) ?: return AppState()
        return parseJson(raw) ?: AppState()
    }

    fun save(context: Context, state: AppState) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_STATE, toJson(state)).apply()
    }

    fun clearAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_STATE).apply()
    }

    fun toJson(state: AppState): String {
        val root = JSONObject().apply {
            put("darkTheme", state.darkTheme)
            put("hideBalance", state.hideBalance)
            put("strictLock", state.strictLock)
            put("ongoingBar", state.ongoingBar)
            put("appBlockerEnabled", state.appBlockerEnabled)
            put("dailyLimit", state.dailyLimit)
            put("lunchLimit", state.lunchLimit)
            put("cycleStartDay", state.cycleStartDay)
            put("monthlySavingTarget", state.monthlySavingTarget)
            put("lastWalletId", state.lastWalletId)
            put("pin", state.pin ?: JSONObject.NULL)
            put("cookSavedTotal", state.cookSavedTotal)
            put("cookDays", JSONArray(state.cookDays))

            val wArr = JSONArray()
            for (w in state.wallets) {
                wArr.put(JSONObject().apply {
                    put("id", w.id)
                    put("name", w.name)
                    put("type", w.type.name)
                    put("balance", w.balance)
                    put("includeInBudget", w.includeInBudget)
                    put("colorHex", w.colorHex)
                })
            }
            put("wallets", wArr)

            val txArr = JSONArray()
            for (t in state.transactions) {
                txArr.put(JSONObject().apply {
                    put("id", t.id)
                    put("type", t.type.name)
                    put("amount", t.amount)
                    put("category", t.category)
                    put("note", t.note)
                    put("date", t.date)
                    put("createdAt", t.createdAt)
                    put("walletId", t.walletId)
                    put("bucket", t.bucket.name)
                    put("recurringRuleId", t.recurringRuleId ?: JSONObject.NULL)
                })
            }
            put("transactions", txArr)

            val gArr = JSONArray()
            for (g in state.goals) {
                gArr.put(JSONObject().apply {
                    put("id", g.id)
                    put("name", g.name)
                    put("target", g.target)
                    put("saved", g.saved)
                    put("dailyPlan", g.dailyPlan)
                })
            }
            put("goals", gArr)

            val rArr = JSONArray()
            for (r in state.recurringRules) {
                rArr.put(JSONObject().apply {
                    put("id", r.id)
                    put("title", r.title)
                    put("amount", r.amount)
                    put("walletId", r.walletId)
                    put("category", r.category)
                    put("dueDay", r.dueDay)
                    put("autoReserve", r.autoReserve)
                    put("lastPaidCycle", r.lastPaidCycle ?: JSONObject.NULL)
                })
            }
            put("recurringRules", rArr)

            val bArr = JSONArray()
            for (b in state.bans) {
                bArr.put(JSONObject().apply {
                    put("id", b.id)
                    put("name", b.name)
                    put("keywords", JSONArray(b.keywords))
                    put("category", b.category)
                    put("dailyMax", b.dailyMax)
                    put("active", b.active)
                    put("createdAt", b.createdAt)
                    put("lastBrokenDate", b.lastBrokenDate ?: JSONObject.NULL)
                })
            }
            put("bans", bArr)
        }
        return root.toString(2)
    }

    fun parseJson(raw: String): AppState? {
        return try {
            val o = JSONObject(raw)
            val cookDays = mutableListOf<String>()
            o.optJSONArray("cookDays")?.let { arr ->
                for (i in 0 until arr.length()) cookDays.add(arr.getString(i))
            }

            val wallets = mutableListOf<WalletItem>()
            o.optJSONArray("wallets")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val w = arr.getJSONObject(i)
                    val type = runCatching { WalletType.valueOf(w.getString("type")) }.getOrDefault(WalletType.CASH)
                    wallets.add(
                        WalletItem(
                            id = w.optString("id", UUID.randomUUID().toString()),
                            name = w.getString("name"),
                            type = type,
                            balance = w.optLong("balance", 0L),
                            includeInBudget = w.optBoolean("includeInBudget", true),
                            colorHex = w.optLong("colorHex", 0xFF0F172A)
                        )
                    )
                }
            }

            val txs = mutableListOf<TxItem>()
            o.optJSONArray("transactions")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val t = arr.getJSONObject(i)
                    val type = runCatching { TxType.valueOf(t.getString("type")) }.getOrDefault(TxType.EXPENSE)
                    val cat = t.optString("category", "Lainnya")
                    val bucket = runCatching { BucketType.valueOf(t.optString("bucket", "")) }
                        .getOrElse { inferBucket(cat) }
                    txs.add(
                        TxItem(
                            id = t.optString("id", UUID.randomUUID().toString()),
                            type = type,
                            amount = t.getLong("amount"),
                            category = cat,
                            note = t.optString("note", ""),
                            date = t.optString("date", todayISO()),
                            createdAt = t.optLong("createdAt", System.currentTimeMillis()),
                            walletId = t.optString("walletId", "wallet_ewallet"),
                            bucket = bucket,
                            recurringRuleId = if (t.isNull("recurringRuleId")) null else t.optString("recurringRuleId")
                        )
                    )
                }
            }

            val goals = mutableListOf<SavingsGoal>()
            o.optJSONArray("goals")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val g = arr.getJSONObject(i)
                    goals.add(
                        SavingsGoal(
                            id = g.optString("id", UUID.randomUUID().toString()),
                            name = g.getString("name"),
                            target = g.getLong("target"),
                            saved = g.optLong("saved", 0L),
                            dailyPlan = g.optLong("dailyPlan", 20_000L)
                        )
                    )
                }
            }

            val rules = mutableListOf<RecurringRule>()
            val hasRules = o.has("recurringRules")
            o.optJSONArray("recurringRules")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val r = arr.getJSONObject(i)
                    rules.add(
                        RecurringRule(
                            id = r.optString("id", UUID.randomUUID().toString()),
                            title = r.getString("title"),
                            amount = r.getLong("amount"),
                            walletId = r.optString("walletId", "wallet_bank"),
                            category = r.optString("category", "Kos & Tagihan"),
                            dueDay = r.optInt("dueDay", 1),
                            autoReserve = r.optBoolean("autoReserve", true),
                            lastPaidCycle = if (r.isNull("lastPaidCycle")) null else r.optString("lastPaidCycle")
                        )
                    )
                }
            }

            val bans = mutableListOf<SpendingBan>()
            o.optJSONArray("bans")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val b = arr.getJSONObject(i)
                    val kws = mutableListOf<String>()
                    b.optJSONArray("keywords")?.let { kArr ->
                        for (j in 0 until kArr.length()) kws.add(kArr.getString(j))
                    }
                    bans.add(
                        SpendingBan(
                            id = b.optString("id", UUID.randomUUID().toString()),
                            name = b.getString("name"),
                            keywords = kws,
                            category = b.optString("category", ""),
                            dailyMax = b.optLong("dailyMax", 0L),
                            active = b.optBoolean("active", true),
                            createdAt = b.optString("createdAt", todayISO()),
                            lastBrokenDate = if (b.isNull("lastBrokenDate")) null else b.optString("lastBrokenDate")
                        )
                    )
                }
            }

            AppState(
                darkTheme = o.optBoolean("darkTheme", false),
                hideBalance = o.optBoolean("hideBalance", false),
                strictLock = o.optBoolean("strictLock", false),
                ongoingBar = o.optBoolean("ongoingBar", true),
                appBlockerEnabled = o.optBoolean("appBlockerEnabled", false),
                dailyLimit = o.optLong("dailyLimit", 35_000L),
                lunchLimit = o.optLong("lunchLimit", 17_000L),
                cycleStartDay = o.optInt("cycleStartDay", 1).coerceIn(1, 28),
                monthlySavingTarget = o.optLong("monthlySavingTarget", 300_000L),
                lastWalletId = o.optString("lastWalletId", "wallet_ewallet"),
                pin = if (o.isNull("pin")) null else o.optString("pin"),
                cookDays = cookDays,
                cookSavedTotal = o.optLong("cookSavedTotal", 0L),
                wallets = if (wallets.isEmpty()) defaultWallets() else wallets,
                transactions = txs,
                goals = goals,
                recurringRules = if (hasRules) rules else defaultRecurringRules(),
                bans = bans
            )
        } catch (_: Exception) {
            null
        }
    }
}
