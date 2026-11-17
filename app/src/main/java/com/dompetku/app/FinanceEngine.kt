package com.dompetku.app

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

data class SafeToSpendResult(
    val safeToSpendToday: Long,
    val dailyAllowance: Long,
    val liquidBalance: Long,
    val unpaidRecurring: Long,
    val unmetSavingTarget: Long,
    val spentToday: Long,
    val daysRemaining: Int,
    val cycleStartIso: String,
    val cycleEndIso: String,
    val needsSpentCycle: Long,
    val wantsSpentCycle: Long,
    val incomeCycle: Long,
    val savedCycle: Long
)

object FinanceEngine {

    fun currentCycleBounds(cycleStartDay: Int): Triple<String, String, Int> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dayOfMonth = today.get(Calendar.DAY_OF_MONTH)
        val clampedStart = cycleStartDay.coerceIn(1, 28)

        val startCal = (today.clone() as Calendar).apply {
            if (dayOfMonth < clampedStart) {
                add(Calendar.MONTH, -1)
            }
            set(Calendar.DAY_OF_MONTH, clampedStart)
        }

        val endCal = (startCal.clone() as Calendar).apply {
            add(Calendar.MONTH, 1)
            add(Calendar.DAY_OF_YEAR, -1)
        }

        val diffMillis = endCal.timeInMillis - today.timeInMillis
        val daysRemaining = ((diffMillis / 86_400_000L).toInt() + 1).coerceAtLeast(1)

        return Triple(sdf.format(startCal.time), sdf.format(endCal.time), daysRemaining)
    }

    fun currentCycleKey(cycleStartDay: Int): String {
        val (startIso, _, _) = currentCycleBounds(cycleStartDay)
        return startIso.substring(0, 7) // yyyy-MM
    }

    fun computeSafeToSpend(state: AppState): SafeToSpendResult {
        val today = todayISO()
        val (cycleStart, cycleEnd, daysRemaining) = currentCycleBounds(state.cycleStartDay)
        val cycleKey = cycleStart.substring(0, 7)

        val liquidBalance = state.wallets
            .filter { it.includeInBudget }
            .sumOf { it.balance }

        val unpaidRecurring = state.recurringRules
            .filter { it.autoReserve && it.lastPaidCycle != cycleKey }
            .sumOf { it.amount }

        val cycleTxs = state.transactions.filter { it.date in cycleStart..cycleEnd }

        val savedCycle = cycleTxs
            .filter { it.type == TxType.SAVING }
            .sumOf { it.amount }

        val unmetSavingTarget = (state.monthlySavingTarget - savedCycle).coerceAtLeast(0L)

        val spentToday = state.transactions
            .filter { it.date == today && it.type == TxType.EXPENSE && it.recurringRuleId == null }
            .sumOf { it.amount }

        // Saldo awal hari ini (sebelum pengeluaran non-tagihan hari ini)
        val startOfDayLiquid = liquidBalance + spentToday
        val discretionaryPool = (startOfDayLiquid - unpaidRecurring - unmetSavingTarget).coerceAtLeast(0L)
        val dailyAllowance = discretionaryPool / daysRemaining
        val safeToSpendToday = dailyAllowance - spentToday

        val needsSpentCycle = cycleTxs
            .filter { it.type == TxType.EXPENSE && it.bucket == BucketType.NEEDS }
            .sumOf { it.amount }

        val wantsSpentCycle = cycleTxs
            .filter { it.type == TxType.EXPENSE && it.bucket == BucketType.WANTS }
            .sumOf { it.amount }

        val incomeCycle = cycleTxs
            .filter { it.type == TxType.INCOME }
            .sumOf { it.amount }

        return SafeToSpendResult(
            safeToSpendToday = safeToSpendToday,
            dailyAllowance = dailyAllowance,
            liquidBalance = liquidBalance,
            unpaidRecurring = unpaidRecurring,
            unmetSavingTarget = unmetSavingTarget,
            spentToday = spentToday,
            daysRemaining = daysRemaining,
            cycleStartIso = cycleStart,
            cycleEndIso = cycleEnd,
            needsSpentCycle = needsSpentCycle,
            wantsSpentCycle = wantsSpentCycle,
            incomeCycle = incomeCycle,
            savedCycle = savedCycle
        )
    }

    fun applyTransaction(
        state: AppState,
        tx: TxItem,
        goalId: String? = null
    ): AppState {
        val updatedWallets = state.wallets.map { w ->
            if (w.id != tx.walletId) w
            else {
                val delta = when (tx.type) {
                    TxType.INCOME -> tx.amount
                    TxType.EXPENSE, TxType.SAVING -> -tx.amount
                }
                w.copy(balance = w.balance + delta)
            }
        }

        val updatedGoals = if (tx.type == TxType.SAVING && goalId != null) {
            state.goals.map { g ->
                if (g.id == goalId) g.copy(saved = g.saved + tx.amount) else g
            }
        } else {
            state.goals
        }

        return state.copy(
            lastWalletId = tx.walletId,
            wallets = updatedWallets,
            transactions = listOf(tx) + state.transactions,
            goals = updatedGoals
        )
    }

    fun deleteTransaction(state: AppState, tx: TxItem): AppState {
        val updatedWallets = state.wallets.map { w ->
            if (w.id != tx.walletId) w
            else {
                val reverseDelta = when (tx.type) {
                    TxType.INCOME -> -tx.amount
                    TxType.EXPENSE, TxType.SAVING -> tx.amount
                }
                w.copy(balance = w.balance + reverseDelta)
            }
        }
        return state.copy(
            wallets = updatedWallets,
            transactions = state.transactions.filterNot { it.id == tx.id }
        )
    }

    fun payRecurringRule(state: AppState, rule: RecurringRule): AppState {
        val cycleKey = currentCycleKey(state.cycleStartDay)
        val tx = TxItem(
            id = UUID.randomUUID().toString(),
            type = TxType.EXPENSE,
            amount = rule.amount,
            category = rule.category,
            note = "Tagihan: ${rule.title}",
            date = todayISO(),
            createdAt = System.currentTimeMillis(),
            walletId = rule.walletId,
            bucket = BucketType.NEEDS,
            recurringRuleId = rule.id
        )
        val updatedRules = state.recurringRules.map {
            if (it.id == rule.id) it.copy(lastPaidCycle = cycleKey) else it
        }
        return applyTransaction(state.copy(recurringRules = updatedRules), tx)
    }

    fun checkBanViolation(
        state: AppState,
        amount: Long,
        category: String,
        note: String
    ): SpendingBan? {
        val lowerNote = note.lowercase(Locale.getDefault())
        val lowerCat = category.lowercase(Locale.getDefault())
        val today = todayISO()
        for (ban in state.bans.filter { it.active }) {
            val catMatch = ban.category.isNotBlank() && lowerCat == ban.category.lowercase(Locale.getDefault())
            val kwMatch = ban.keywords.any { kw ->
                kw.isNotBlank() && (lowerNote.contains(kw.lowercase(Locale.getDefault())) ||
                    lowerCat.contains(kw.lowercase(Locale.getDefault())))
            }
            if (!catMatch && !kwMatch) continue

            if (ban.dailyMax == 0L) return ban

            val spentTodayOnCat = state.transactions
                .filter { it.date == today && it.type == TxType.EXPENSE && it.category.equals(ban.category, true) }
                .sumOf { it.amount }
            if (spentTodayOnCat + amount > ban.dailyMax) return ban
        }
        return null
    }
}
