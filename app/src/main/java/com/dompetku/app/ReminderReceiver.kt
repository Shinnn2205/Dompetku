package com.dompetku.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_MARK_COOKED) {
            val state = FinanceRepo.load(context)
            val today = todayISO()
            if (today !in state.cookDays) {
                val updated = state.copy(
                    cookDays = state.cookDays + today,
                    cookSavedTotal = state.cookSavedTotal + 25_000L
                )
                FinanceRepo.save(context, updated)
                updateOngoingBudgetNotification(context, updated)
                showNotification(context, 778, "Mantap! Sudah Masak Hari Ini", "Streak masak bertambah & hemat Rp 25.000!")
            }
            return
        }

        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_ENABLED, true)) {
                scheduleDailyReminders(context)
            }
            val state = FinanceRepo.load(context)
            if (state.ongoingBar) {
                updateOngoingBudgetNotification(context, state)
            }
            return
        }

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "DompetKu"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Jangan lupa catat keuanganmu hari ini!"
        val notifId = intent.getIntExtra(EXTRA_ID, 100)

        showNotification(context, notifId, title, message)
    }

    companion object {
        const val CHANNEL_ID = "dompetku_reminders"
        const val CHANNEL_ONGOING_ID = "dompetku_ongoing_budget"
        const val ONGOING_ID = 777
        const val ACTION_MARK_COOKED = "com.dompetku.app.ACTION_MARK_COOKED"
        const val PREFS_NAME = "dompetku_notif_prefs"
        const val KEY_ENABLED = "reminders_enabled"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_ID = "extra_id"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(
                            CHANNEL_ID,
                            "Pengingat Masak & Nabung",
                            NotificationManager.IMPORTANCE_HIGH
                        ).apply {
                            description = "Pengingat harian agar rajin masak, tahan jajan, dan menabung"
                        }
                    )
                }
                if (nm.getNotificationChannel(CHANNEL_ONGOING_ID) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(
                            CHANNEL_ONGOING_ID,
                            "Status Jatah Harian",
                            NotificationManager.IMPORTANCE_LOW
                        ).apply {
                            description = "Menampilkan sisa jatah pengeluaran harian di panel notifikasi"
                            setShowBadge(false)
                        }
                    )
                }
            }
        }

        fun updateOngoingBudgetNotification(context: Context, state: AppState) {
            ensureChannel(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!state.ongoingBar) {
                nm.cancel(ONGOING_ID)
                return
            }

            val sts = FinanceEngine.computeSafeToSpend(state)
            val remaining = sts.safeToSpendToday

            val title = if (remaining >= 0) {
                "Safe to Spend Hari Ini: ${formatRp(remaining)}"
            } else {
                "Over Limit Hari Ini: ${formatRp(remaining)}"
            }
            val sub = "Jatah harian ${formatRp(sts.dailyAllowance)} • Sisa ${sts.daysRemaining} hari siklus"

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingOpen = PendingIntent.getActivity(
                context, ONGOING_ID, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val cookIntent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_MARK_COOKED
            }
            val pendingCook = PendingIntent.getBroadcast(
                context, 779, cookIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notif = NotificationCompat.Builder(context, CHANNEL_ONGOING_ID)
                .setSmallIcon(android.R.drawable.ic_menu_today)
                .setContentTitle(title)
                .setContentText(sub)
                .setOngoing(true)
                .setSilent(true)
                .addAction(0, "Saya Masak Hari Ini", pendingCook)
                .setContentIntent(pendingOpen)
                .build()

            nm.notify(ONGOING_ID, notif)
        }

        fun showNotification(context: Context, id: Int, title: String, message: String) {
            ensureChannel(context)
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingOpen = PendingIntent.getActivity(
                context, id, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingOpen)
                .build()

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(id, notif)
        }

        fun testNotification(context: Context) {
            showNotification(
                context,
                999,
                "Pengingat DompetKu Aktif",
                "Sisa jatah harian Rp 20.000, makan siang maks Rp 17.000, malam wajib masak!"
            )
        }

        fun scheduleDailyReminders(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_ENABLED, true).apply()

            scheduleAlarm(
                context, 101, 11, 30,
                "Makan Siang Hemat",
                "Boleh beli makan siang, tapi ingat maksimal Rp 17.000! Total jatah harianmu Rp 20.000."
            )
            scheduleAlarm(
                context, 102, 16, 30,
                "Awas Jam Rawan Jajan",
                "Total jatahmu hari ini maksimal Rp 20.000. Tahan jajan sore!"
            )
            scheduleAlarm(
                context, 104, 18, 0,
                "Malam Ini Wajib Masak",
                "Jangan beli jajan atau pesan GoFood malam ini! Masak sendiri di rumah dan simpan uangnya buat target tabunganmu."
            )
            scheduleAlarm(
                context, 103, 20, 30,
                "Rekap & Nabung Hari Ini",
                "Sudah catat makan siang & cek sisa jatah Rp 20.000 hari ini? Tabungkan sisanya sekarang."
            )
        }

        fun cancelDailyReminders(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_ENABLED, false).apply()

            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            for (id in listOf(101, 102, 103, 104)) {
                val intent = Intent(context, ReminderReceiver::class.java)
                val pi = PendingIntent.getBroadcast(
                    context, id, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                am.cancel(pi)
            }
        }

        private fun scheduleAlarm(
            context: Context,
            requestCode: Int,
            hour: Int,
            minute: Int,
            title: String,
            message: String
        ) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra(EXTRA_ID, requestCode)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
            }
            val pi = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                cal.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pi
            )
        }
    }
}
