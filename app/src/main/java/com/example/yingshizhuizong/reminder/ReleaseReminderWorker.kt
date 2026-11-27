package com.example.yingshizhuizong.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.yingshizhuizong.data.AppDatabase
import com.example.yingshizhuizong.settings.SettingsRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** 周期任务：检查即将上映的影片并发送提醒通知 */
class ReleaseReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val daysBefore = SettingsRepository(applicationContext).reminderDays()

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val horizon = todayStart + (daysBefore.toLong() + 1) * 24 * 60 * 60 * 1000L - 1L

        val candidates = db.watchItemDao().getReminderCandidates(todayStart, horizon)
        val formatter = DateTimeFormatter.ofPattern("yyyy年M月d日")

        candidates.forEach { item ->
            val release = item.releaseDate ?: return@forEach
            val date = Instant.ofEpochMilli(release).atZone(zone).toLocalDate()
            val daysLeft = ChronoUnit.DAYS.between(today, date)
            val message = when {
                daysLeft <= 0L -> "《${item.title}》今天上映啦！"
                daysLeft == 1L -> "《${item.title}》明天上映，记得观看～"
                else -> "《${item.title}》还有 ${daysLeft} 天上映（${formatter.format(date)}）"
            }
            Notifications.showReleaseReminder(
                applicationContext,
                item.id.toInt(),
                "上映提醒",
                message
            )
            db.watchItemDao().markReminded(item.id, release)
        }
        return Result.success()
    }
}
