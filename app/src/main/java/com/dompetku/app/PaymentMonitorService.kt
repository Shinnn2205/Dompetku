package com.dompetku.app

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class PaymentMonitorService : NotificationListenerService() {

    private var lastAlertTime = 0L

    private val watchedPackages = setOf(
        "com.gojek.app",
        "com.gopay.app",
        "id.dana",
        "ovo.id",
        "com.shopee.id",
        "com.bca",
        "com.bankmandiri.livin",
        "id.co.bri.brimo",
        "src.com.bni"
    )

    private val paymentKeywords = listOf(
        "pembayaran", "bayar", "berhasil", "transaksi", "gopay", "qris", "transfer", "debit", "keluar", "rp"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        if (pkg !in watchedPackages) return

        val extras = sbn.notification.extras
        val title = extras.getString("android.title")?.lowercase() ?: ""
        val text = extras.getCharSequence("android.text")?.toString()?.lowercase() ?: ""
        val combined = "$title $text"

        if (paymentKeywords.none { it in combined }) return

        val now = System.currentTimeMillis()
        if (now - lastAlertTime < 15_000L) return
        lastAlertTime = now

        val state = FinanceRepo.load(applicationContext)
        val today = todayISO()
        val spentToday = state.transactions
            .filter { it.type == TxType.EXPENSE && it.date == today }
            .sumOf { it.amount }
        val remaining = state.dailyLimit - spentToday

        val msg = if (state.dailyLimit > 0) {
            if (remaining > 0) {
                "Terdeteksi transaksi e-wallet/bank. Sisa jatah hari ini tinggal ${formatRp(remaining)}. Ketuk untuk catat di DompetKu!"
            } else {
                "Terdeteksi transaksi! Jatah harian ${formatRp(state.dailyLimit)} sudah habis. Segera catat & tahan pengeluaran!"
            }
        } else {
            "Terdeteksi transaksi e-wallet/bank. Ketuk untuk mencatatnya di DompetKu."
        }

        ReminderReceiver.showNotification(
            applicationContext,
            505,
            "Catat Transaksi Sekarang",
            msg
        )
    }
}
