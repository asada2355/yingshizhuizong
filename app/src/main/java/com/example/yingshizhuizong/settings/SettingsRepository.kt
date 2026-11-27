package com.example.yingshizhuizong.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** 应用设置（DataStore 持久化） */
class SettingsRepository(private val context: Context) {

    companion object {
        val KEY_REMINDER_DAYS = intPreferencesKey("reminder_days")
        val KEY_OPEN_TRAILER_EXTERNALLY = booleanPreferencesKey("open_trailer_externally")
    }

    /** 提前提醒天数（默认 1 天） */
    val reminderDaysFlow: Flow<Int> =
        context.dataStore.data.map { it[KEY_REMINDER_DAYS] ?: 1 }

    suspend fun reminderDays(): Int = reminderDaysFlow.first()

    suspend fun setReminderDays(days: Int) {
        context.dataStore.edit { it[KEY_REMINDER_DAYS] = days }
    }

    /** 预告片是否直接用 YouTube App 打开（默认关闭：优先应用内播放） */
    val openTrailerExternallyFlow: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_OPEN_TRAILER_EXTERNALLY] ?: false }

    suspend fun setOpenTrailerExternally(value: Boolean) {
        context.dataStore.edit { it[KEY_OPEN_TRAILER_EXTERNALLY] = value }
    }
}
