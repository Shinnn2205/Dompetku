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
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_ENABLED, true)) {
                scheduleDailyReminders(context)
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
        const val PREFS_NAME = "dompetku_notif_prefs"
        const val KEY_ENABLED = "reminders_enabled"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_ID = "extra_id"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "Pengingat Masak & Nabung",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Pengingat harian agar rajin masak, tahan jajan, dan menabung"
                    }
                    nm.createNotificationChannel(channel)
                }
            }
        }

        fun showNotification(context: Context, id: Int, title: String, message: String) {
            ensureChannel(context)
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingOpen = PendingIntent.getActivity(
                context,
                id,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingOpen)
                .build()

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(id, notif)
        }

        fun scheduleDailyReminders(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_ENABLED, true).apply()

            scheduleAlarm(
                context, 101, 10, 45,
                "🍳 Waktunya Masak Sendiri!",
                "Tahan pesan GoFood/ShopeeFood hari ini. Masak sendiri lebih sehat dan bikin target tabunganmu cepat tercapai!"
            )
            scheduleAlarm(
                context, 102, 16, 30,
                "🧋 Awas Jam Rawan Jajan!",
                "Ingat larangan jajanmu di DompetKu. Pertahankan streak hematmu hari ini!"
            )
            scheduleAlarm(
                context, 103, 20, 30,
                "💰 Rekap & Nabung Hari Ini",
                "Sudah catat pengeluaran GoPay/tunai hari ini? Cek sisa jatah harian & setor ke target tabunganmu."
            )
        }

        fun cancelDailyReminders(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_ENABLED, false).apply()

            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            for (id in listOf(101, 102, 103)) {
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
                context,
                requestCode,
                intent,
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
