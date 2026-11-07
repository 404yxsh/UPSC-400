package app.upsc365.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import app.upsc365.MainActivity
import app.upsc365.R
import app.upsc365.data.ProgrammeLoader
import app.upsc365.data.Store
import app.upsc365.engine.Engine
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object Reminder {
    const val CHANNEL_ID = "daily_plan"
    private const val WORK_NAME = "daily_reminder"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(CHANNEL_ID, "Daily plan", NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "A reminder before your study window starts"
            nm.createNotificationChannel(ch)
        }
    }

    fun schedule(ctx: Context, hour: Int, minute: Int) {
        val now = ZonedDateTime.now()
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMillis()
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME)
    }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    @SuppressLint("MissingPermission")
    override fun doWork(): Result {
        val ctx = applicationContext
        val store = Store(ctx)
        if (store.getKv("rem_on") != "1") return Result.success()
        val hour = store.getKv("rem_h")?.toIntOrNull() ?: 18
        val minute = store.getKv("rem_m")?.toIntOrNull() ?: 45

        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            Reminder.ensureChannel(ctx)
            val text = todayText(ctx, store)
            val tap = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = NotificationCompat.Builder(ctx, Reminder.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("UPSC 365")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(ctx).notify(1001, n)
        }
        Reminder.schedule(ctx, hour, minute)
        return Result.success()
    }

    private fun todayText(ctx: Context, store: Store): String {
        return try {
            val prog = ProgrammeLoader.load(ctx)
            val start = store.getKv("start")?.let { LocalDate.parse(it) } ?: LocalDate.parse(prog.defaultStart)
            val cal = Engine.buildCalendar(start, prog.flexAfter, prog.spineDays)
            val c = Engine.find(cal, LocalDate.now())
            if (c == null) {
                "Open the app for today's plan."
            } else if (c.type == Engine.DayType.SPINE) {
                val plan = prog.day(c.spineDay)
                "Day ${c.spineDay}: ${plan.math.title} · ${plan.gs.title}"
            } else if (c.type == Engine.DayType.SUNDAY) {
                "Sunday: clear the recovery queue or consolidate the week."
            } else {
                "Flex day: catch up or rest."
            }
        } catch (e: Exception) {
            "Open the app for today's plan."
        }
    }
}
