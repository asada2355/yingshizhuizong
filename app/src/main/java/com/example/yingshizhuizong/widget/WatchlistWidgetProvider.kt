package com.example.yingshizhuizong.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.yingshizhuizong.MainActivity
import com.example.yingshizhuizong.R
import com.example.yingshizhuizong.data.AppDatabase
import com.example.yingshizhuizong.data.entity.WatchItem
import com.example.yingshizhuizong.data.entity.WatchStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val ACTION_MARK_WATCHED = "com.example.yingshizhuizong.widget.ACTION_MARK_WATCHED"
private const val EXTRA_ITEM_ID = "com.example.yingshizhuizong.widget.EXTRA_ITEM_ID"
private const val WIDGET_LIMIT = 5

/** 桌面小组件：显示即将上映的影片，并支持在桌面标记「已看」 */
class WatchlistWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MARK_WATCHED -> {
                val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
                if (itemId <= 0L) return
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getInstance(context)
                        val item = db.watchItemDao().getById(itemId)
                        if (item != null && item.status != WatchStatus.WATCHED) {
                            db.watchItemDao().update(
                                item.copy(
                                    status = WatchStatus.WATCHED,
                                    reminderEnabled = false,
                                    updatedAt = System.currentTimeMillis(),
                                )
                            )
                        }
                        val manager = AppWidgetManager.getInstance(context)
                        val ids = manager.getAppWidgetIds(
                            ComponentName(context, WatchlistWidgetProvider::class.java)
                        )
                        render(context, manager, ids)
                    } catch (_: Throwable) {
                        // 忽略，避免影响桌面
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, WatchlistWidgetProvider::class.java)
                )
                onUpdate(context, manager, ids)
            }

            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                render(context, appWidgetManager, appWidgetIds)
            } catch (_: Throwable) {
                // 忽略，避免影响主流程
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** 从数据库读取即将上映的影片并刷新所有小组件实例 */
    private suspend fun render(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val db = AppDatabase.getInstance(context)
        val todayStart = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val upcoming = db.watchItemDao().getUpcoming(todayStart, WIDGET_LIMIT)
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildViews(context, upcoming))
        }
    }

    private fun buildViews(context: Context, upcoming: List<WatchItem>): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.watchlist_widget)

        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, openIntent)
        views.setOnClickPendingIntent(R.id.widget_header, openIntent)

        val container = R.id.widget_items
        views.removeAllViews(container)

        if (upcoming.isEmpty()) {
            val empty = RemoteViews(context.packageName, R.layout.watchlist_widget_item)
            empty.setTextViewText(R.id.widget_item_title, "暂无即将上映的影片")
            empty.setViewVisibility(R.id.widget_item_date, View.GONE)
            empty.setViewVisibility(R.id.widget_item_icon, View.GONE)
            empty.setViewVisibility(R.id.widget_item_action, View.GONE)
            views.addView(container, empty)
        } else {
            val formatter = DateTimeFormatter.ofPattern("M月d日")
            upcoming.forEachIndexed { index, item ->
                val row = RemoteViews(context.packageName, R.layout.watchlist_widget_item)
                row.setTextViewText(R.id.widget_item_title, item.title)

                val dateText = item.releaseDate?.let {
                    formatter.format(
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    )
                }.orEmpty()
                row.setTextViewText(R.id.widget_item_date, dateText)
                if (dateText.isBlank()) row.setViewVisibility(R.id.widget_item_date, View.GONE)

                val itemIntent = Intent(context, MainActivity::class.java)
                val pending = PendingIntent.getActivity(
                    context, index + 1, itemIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                row.setOnClickPendingIntent(R.id.widget_item_row, pending)

                // 桌面直接写主 App 数据：标记已看，并关闭该片的上映提醒
                val markIntent = Intent(context, WatchlistWidgetProvider::class.java).apply {
                    action = ACTION_MARK_WATCHED
                    putExtra(EXTRA_ITEM_ID, item.id)
                }
                val markPending = PendingIntent.getBroadcast(
                    context, 2000 + index, markIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                row.setOnClickPendingIntent(R.id.widget_item_action, markPending)
                views.addView(container, row)
            }
        }
        return views
    }
}
