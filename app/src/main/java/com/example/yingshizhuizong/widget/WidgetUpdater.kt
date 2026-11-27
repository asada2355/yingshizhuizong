package com.example.yingshizhuizong.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent

/** 数据变更后触发小组件刷新 */
object WidgetUpdater {

    fun notifyUpdate(context: Context) {
        val intent = Intent(context, WatchlistWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        }
        context.sendBroadcast(intent)
    }
}
