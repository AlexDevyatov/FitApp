package ru.fitapp.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ru.fitapp.R
import ru.fitapp.core.LogTags
import ru.fitapp.ui.MainActivity

/**
 * Уведомления вечернего отчёта: канал создаётся в Application, показ — из воркера.
 *
 * Текст собирает воркер: диапазон ккал и главный совет дня. Иконка — отдельная
 * монохромная (`ic_stat_report`), иначе на статус-баре пустой квадрат.
 *
 * Логи (tag `FitAppPush`): зарегистрирован ли канал, выдано ли разрешение
 * POST_NOTIFICATIONS и показано ли уведомление. Ключ и текст отчёта не логируются.
 */
object ReportNotifier {

    const val CHANNEL_ID = "daily_report"
    const val NOTIFICATION_ID = 1001

    private const val TAG = LogTags.PUSH

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager == null) {
            Log.e(TAG, "push: NotificationManager недоступен — уведомления не покажутся")
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.report_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.report_channel_description)
        }
        manager.createNotificationChannel(channel)
        Log.i(TAG, "push: канал $CHANNEL_ID зарегистрирован")
    }

    /** true, если система разрешает показывать уведомления (на API < 33 — всегда). */
    fun canPostNotifications(context: Context): Boolean {
        // Разрешение появилось только в Android 13; на старых версиях проверять нечего.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun notifyReportReady(context: Context, summary: String? = null, bigText: String? = null) {
        if (!canPostNotifications(context)) {
            Log.w(TAG, "push: нет разрешения POST_NOTIFICATIONS — уведомление не показано")
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_report)
            .setContentTitle(context.getString(R.string.report_notification_title))
            .setContentText(summary ?: context.getString(R.string.report_notification_text))
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
        if (bigText != null) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
        }

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // Разрешение могли отозвать между проверкой и показом.
            Log.e(TAG, "push: система отклонила уведомление $NOTIFICATION_ID", e)
            return
        }
        Log.i(TAG, "push: уведомление $NOTIFICATION_ID показано")
    }
}
