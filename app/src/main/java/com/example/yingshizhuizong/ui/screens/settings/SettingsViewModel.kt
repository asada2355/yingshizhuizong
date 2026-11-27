package com.example.yingshizhuizong.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yingshizhuizong.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsRepository(application)

    val reminderDays: StateFlow<Int> = settings.reminderDaysFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    val openTrailerExternally: StateFlow<Boolean> = settings.openTrailerExternallyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setReminderDays(days: Int) {
        viewModelScope.launch { settings.setReminderDays(days) }
    }

    fun setOpenTrailerExternally(value: Boolean) {
        viewModelScope.launch { settings.setOpenTrailerExternally(value) }
    }
}
