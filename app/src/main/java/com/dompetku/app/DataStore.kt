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

data class TxItem(
    val id: String,
    val type: TxType,
    val amount: Long,
    val category: String,
    val note: String,
    val date: String,
    val createdAt: Long
)

data class SavingsGoal(
    val id: String,
    val name: String,
    val target: Long,
    val saved: Long
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
    val darkTheme: Boolean = true,
    val hideBalance: Boolean = false,
    val strictLock: Boolean = true,
    val ongoingBar: Boolean = true,
    val dailyLimit: Long = 20_000L,
    val lunchLimit: Long = 17_000L,
    val pin: String? = null,
    val cookDays: List<String> = emptyList(),
    val cookSavedTotal: Long = 0L,
    val transactions: List<TxItem> = emptyList(),
    val goals: List<SavingsGoal> = defaultGoals(),
    val bans: List<SpendingBan> = defaultBans()
)

fun defaultGoals() = listOf(
    SavingsGoal(
        id = "goal_default_1",
        name = "Dana Darurat Pertama",
        target = 1_000_000L,
        saved = 0L
    )
)

fun defaultBans() = listOf(
    SpendingBan(
        id = "ban_lunch_17k",
        name = "Makan Siang Maks Rp 17.000",
        keywords = listOf("siang", "makan siang", "nasi", "warteg", "ayam", "mie"),
        category = "Makan & Minum",
        dailyMax = 17_000L,
        active = true,
        createdAt = todayISO(),
        lastBrokenDate = null
    ),
    SpendingBan(
        id = "ban_snack_strict",
        name = "Stop Jajan & Minuman Kekinian",
        keywords = listOf("boba", "kopi", "es", "jajan", "snack", "cimol", "seblak", "gofood"),
        category = "Jajan & Minuman",
        dailyMax = 0L,
        active = true,
        createdAt = todayISO(),
        lastBrokenDate = null
    )
)

fun todayISO(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

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
            put("dailyLimit", state.dailyLimit)
            put("lunchLimit", state.lunchLimit)
            put("pin", state.pin ?: JSONObject.NULL)
            put("cookSavedTotal", state.cookSavedTotal)
            put("cookDays", JSONArray(state.cookDays))

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
                })
            }
            put("goals", gArr)

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

            val txs = mutableListOf<TxItem>()
            o.optJSONArray("transactions")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val t = arr.getJSONObject(i)
                    val type = runCatching { TxType.valueOf(t.getString("type")) }.getOrDefault(TxType.EXPENSE)
                    txs.add(
                        TxItem(
                            id = t.optString("id", UUID.randomUUID().toString()),
                            type = type,
                            amount = t.getLong("amount"),
                            category = t.optString("category", "Lainnya"),
                            note = t.optString("note", ""),
                            date = t.optString("date", todayISO()),
                            createdAt = t.optLong("createdAt", System.currentTimeMillis())
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
                            saved = g.optLong("saved", 0L)
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
                darkTheme = o.optBoolean("darkTheme", true),
                hideBalance = o.optBoolean("hideBalance", false),
                strictLock = o.optBoolean("strictLock", true),
                ongoingBar = o.optBoolean("ongoingBar", true),
                dailyLimit = o.optLong("dailyLimit", 20_000L),
                lunchLimit = o.optLong("lunchLimit", 17_000L),
                pin = if (o.isNull("pin")) null else o.optString("pin"),
                cookDays = cookDays,
                cookSavedTotal = o.optLong("cookSavedTotal", 0L),
                transactions = txs,
                goals = goals,
                bans = bans
            )
        } catch (_: Exception) {
            null
        }
    }
}
