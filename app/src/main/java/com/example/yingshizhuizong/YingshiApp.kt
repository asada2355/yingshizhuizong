package com.example.yingshizhuizong

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.yingshizhuizong.data.AppDatabase
import com.example.yingshizhuizong.data.Repository
import com.example.yingshizhuizong.data.SeedData
import com.example.yingshizhuizong.reminder.Notifications
import com.example.yingshizhuizong.reminder.ReleaseReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class YingshiApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val repository: Repository by lazy { Repository(this, database) }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        scheduleReminderWorker()
        seedIfNeeded()
    }

    /** 每次启动：填充示例影片（若空），并补抓缺失的海报 */
    private fun seedIfNeeded() {
        appScope.launch {
            SeedData.seedIfEmpty(database)
            SeedData.enrichMissingCategories(database)
            SeedData.enrichMissingPosters(database)
        }
    }

    private fun scheduleReminderWorker() {
        val request = PeriodicWorkRequestBuilder<ReleaseReminderWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "release_reminder_worker",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
