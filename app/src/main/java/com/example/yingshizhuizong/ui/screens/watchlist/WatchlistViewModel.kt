package com.example.yingshizhuizong.ui.screens.watchlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yingshizhuizong.YingshiApp
import com.example.yingshizhuizong.data.entity.WatchCategory
import com.example.yingshizhuizong.data.entity.WatchItem
import com.example.yingshizhuizong.data.entity.WatchStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WatchlistViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as YingshiApp).repository

    private val allItems: StateFlow<List<WatchItem>> = repo.observeWatchItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val selectedCategoryId = MutableStateFlow<WatchCategory?>(null)

    /** 列表里实际出现过的分类，筛选项只展示这些，避免一排空分类 */
    val categories: StateFlow<List<WatchCategory>> = allItems
        .map { items ->
            WatchCategory.entries.filter { category -> items.any { it.category == category } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedCategory: StateFlow<WatchCategory?> = selectedCategoryId.asStateFlow()

    val items: StateFlow<List<WatchItem>> = combine(allItems, selectedCategoryId) { items, category ->
        if (category == null) items else items.filter { it.category == category }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectCategory(category: WatchCategory?) {
        selectedCategoryId.value = category
    }

    private var lastTopItemId: Long? = null

    /**
     * 列表顶部出现新条目时返回 true，用于自动滚回顶部。
     * 这样新添加的影片不会因为 LazyColumn 的滚动锚定而藏在屏幕上方。
     */
    fun shouldScrollToTop(items: List<WatchItem>): Boolean {
        val topId = items.firstOrNull()?.id ?: return false
        val changed = lastTopItemId != null && lastTopItemId != topId
        lastTopItemId = topId
        return changed
    }

    fun setStatus(item: WatchItem, status: WatchStatus) {
        viewModelScope.launch {
            repo.saveWatchItem(item.copy(status = status, updatedAt = System.currentTimeMillis()))
        }
    }

    fun delete(item: WatchItem) {
        viewModelScope.launch { repo.deleteWatchItem(item) }
    }

    /** 手动触发：为缺失海报的条目补抓（结果自动刷新到列表） */
    fun refreshPosters() {
        getApplication<YingshiApp>().appScope.launch { repo.enrichAllMissingPosters() }
    }
}
