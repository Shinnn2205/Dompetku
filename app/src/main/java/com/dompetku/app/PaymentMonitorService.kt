package com.dompetku.app

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.UUID

class PaymentMonitorService : NotificationListenerService() {

    private var lastAlertTime = 0L
    private var lastSignature = ""

    private val watchedPackages = mapOf(
        "com.gojek.app" to "GoPay/Gojek",
        "com.gopay.app" to "GoPay",
        "id.dana" to "DANA",
        "ovo.id" to "OVO",
        "com.shopee.id" to "Shopee",
        "com.bca" to "BCA",
        "com.bankmandiri.livin" to "Livin' Mandiri",
        "id.co.bri.brimo" to "BRImo",
        "src.com.bni" to "BNI"
    )

    private val outgoingKeywords = listOf(
        "pembayaran", "bayar", "berhasil", "qris", "transfer", "debit", "transaksi", "checkout"
    )

    private val incomingIgnoreKeywords = listOf(
        "masuk", "diterima", "top up berhasil", "topup berhasil", "cashback", "refund"
    )

    private val rpRegex = Regex("""(?:rp|idr)\s*([0-9][0-9.,]*)""", RegexOption.IGNORE_CASE)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        val appLabel = watchedPackages[pkg] ?: return

        val extras = sbn.notification.extras
        val title = extras.getString("android.title")?.trim() ?: ""
        val text = extras.getCharSequence("android.text")?.toString()?.trim() ?: ""
        val combined = "$title $text"
        val lower = combined.lowercase()

        if (incomingIgnoreKeywords.any { it in lower }) return
        if (outgoingKeywords.none { it in lower }) return

        val match = rpRegex.find(combined) ?: return
        val amount = parseRupiahAmount(match.groupValues[1]) ?: return
        if (amount < 500L || amount > 50_000_000L) return

        val sig = "$pkg|$amount|${lower.take(40)}"
        val now = System.currentTimeMillis()
        if (sig == lastSignature && now - lastAlertTime < 20_000L) return
        lastSignature = sig
        lastAlertTime = now

        // Auto-record expense so the user doesn't have to type anything manually!
        val state = FinanceRepo.load(applicationContext)
        val today = todayISO()
        val tx = TxItem(
            id = UUID.randomUUID().toString(),
            type = TxType.EXPENSE,
            amount = amount,
            category = "Jajan & E-Wallet",
            note = "Auto ($appLabel)",
            date = today,
            createdAt = now
        )
        val updatedState = state.copy(transactions = state.transactions + tx)
        FinanceRepo.save(applicationContext, updatedState)
        ReminderReceiver.updateOngoingBudgetNotification(applicationContext, updatedState)

        val spentToday = updatedState.transactions
            .filter { it.type == TxType.EXPENSE && it.date == today }
            .sumOf { it.amount }

        if (updatedState.dailyLimit > 0L) {
            val remaining = updatedState.dailyLimit - spentToday
            if (remaining < 0) {
                ReminderReceiver.showNotification(
                    applicationContext,
                    505,
                    "STOP! Jatah Hari Ini Habis",
                    "Tercatat otomatis ${formatRp(amount)} ($appLabel). Total hari ini ${formatRp(spentToday)} sudah lewat batas ${formatRp(updatedState.dailyLimit)}!"
                )
            } else {
                ReminderReceiver.showNotification(
                    applicationContext,
                    505,
                    "Tercatat Otomatis: ${formatRp(amount)}",
                    "Pengeluaran $appLabel masuk otomatis. Sisa jatah hari ini tinggal ${formatRp(remaining)}."
                )
            }
        } else {
            ReminderReceiver.showNotification(
                applicationContext,
                505,
                "Tercatat Otomatis: ${formatRp(amount)}",
                "Pengeluaran $appLabel dicatat otomatis ke DompetKu."
            )
        }
    }

    private fun parseRupiahAmount(raw: String): Long? {
        var s = raw.trim()
        // Strip trailing decimal cents like ,00 or .00
        if (s.endsWith(",00") || s.endsWith(".00")) {
            s = s.dropLast(3)
        }
        val digits = s.replace(".", "").replace(",", "")
        return digits.toLongOrNull()
    }
}
