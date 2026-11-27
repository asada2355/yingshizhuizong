package com.example.yingshizhuizong.ui.screens.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yingshizhuizong.YingshiApp
import com.example.yingshizhuizong.data.entity.WatchItem
import com.example.yingshizhuizong.data.stats.WatchStats
import com.example.yingshizhuizong.data.stats.WatchStatsCalculator
import com.example.yingshizhuizong.data.stats.YearReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as YingshiApp).repository

    private val items: StateFlow<List<WatchItem>> = repo.observeWatchItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stats: StateFlow<WatchStats> = items
        .map { WatchStatsCalculator.overview(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WatchStats())

    /** 有数据的年份，默认当前年份 */
    val years: StateFlow<List<Int>> = items
        .map { WatchStatsCalculator.availableYears(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(LocalDate.now().year))

    private val _selectedYear = MutableStateFlow(LocalDate.now().year)
    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()

    val yearReport: StateFlow<YearReport> = kotlinx.coroutines.flow.combine(items, _selectedYear) { list, year ->
        WatchStatsCalculator.yearReport(list, year)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), YearReport())

    fun selectYear(year: Int) {
        _selectedYear.value = year
    }

    /** 年份列表变化后，如果当前选中的年份不再存在，退回最新年份 */
    fun ensureSelectedYear() {
        val available = years.value
        if (available.isNotEmpty() && _selectedYear.value !in available) {
            _selectedYear.value = available.first()
        }
    }
}