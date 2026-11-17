package com.dompetku.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import java.util.Calendar

class AppBlockerService : AccessibilityService() {

    private val blockedApps = mapOf(
        "com.gojek.app" to "Gojek / GoFood",
        "com.shopee.id" to "Shopee / ShopeeFood",
        "com.grabtaxi.passenger" to "Grab / GrabFood",
        "com.tokopedia.tkpd" to "Tokopedia",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.ss.android.ugc.trill" to "TikTok",
        "id.dana" to "DANA",
        "ovo.id" to "OVO"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        val appName = blockedApps[pkg] ?: return

        val prefs = getSharedPreferences(PREFS_BLOCKER, Context.MODE_PRIVATE)
        val bypassUntil = prefs.getLong(KEY_BYPASS_UNTIL, 0L)
        if (System.currentTimeMillis() < bypassUntil) return

        val state = FinanceRepo.load(applicationContext)
        if (!state.appBlockerEnabled) return

        val today = todayISO()
        val spentToday = state.transactions
            .filter { it.type == TxType.EXPENSE && it.date == today }
            .sumOf { it.amount }

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isOverBudget = state.dailyLimit > 0L && spentToday >= state.dailyLimit
        val isEveningCookTime = (hour >= 18 || hour < 4) && today !in state.cookDays

        if (!isOverBudget && !isEveningCookTime) return

        val reason = when {
            isOverBudget -> "Jatah harian ${formatRp(state.dailyLimit)} sudah habis (terpakai ${formatRp(spentToday)})."
            else -> "Sudah jam 18:00+. Malam ini wajib masak sendiri, jangan jajan di $appName!"
        }

        val intent = Intent(this, BlockOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(BlockOverlayActivity.EXTRA_APP_NAME, appName)
            putExtra(BlockOverlayActivity.EXTRA_REASON, reason)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {}

    companion object {
        const val PREFS_BLOCKER = "dompetku_blocker_prefs"
        const val KEY_BYPASS_UNTIL = "bypass_until"

        fun grantTemporaryBypass(context: Context, durationMs: Long = 120_000L) {
            context.getSharedPreferences(PREFS_BLOCKER, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_BYPASS_UNTIL, System.currentTimeMillis() + durationMs)
                .apply()
        }
    }
}
