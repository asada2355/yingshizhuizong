package com.example.yingshizhuizong.ui.screens.upcoming

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yingshizhuizong.YingshiApp
import com.example.yingshizhuizong.data.entity.WatchItem
import com.example.yingshizhuizong.data.entity.WatchStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

class UpcomingViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as YingshiApp).repository

    val items: StateFlow<List<WatchItem>> = repo.observeWatchItems()
        .map { list ->
            val todayStart = LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            list.filter {
                it.releaseDate != null &&
                    it.releaseDate!! >= todayStart &&
                    it.status != WatchStatus.WATCHED
            }.sortedBy { it.releaseDate }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
