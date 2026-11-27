package com.example.yingshizhuizong.ui.screens.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yingshizhuizong.YingshiApp
import com.example.yingshizhuizong.data.TmdbApi
import com.example.yingshizhuizong.data.entity.WatchItem
import com.example.yingshizhuizong.util.Pinyin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 搜索范围：本地看单 / TMDB 在线 */
enum class SearchMode { LOCAL, ONLINE }

/** 在线搜索状态 */
sealed interface OnlineState {
    data object Idle : OnlineState
    data object Loading : OnlineState
    data object Empty : OnlineState
    data class Results(val items: List<TmdbApi.SearchItem>) : OnlineState
    data class Error(val message: String) : OnlineState
}

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as YingshiApp).repository

    private val _mode = MutableStateFlow(SearchMode.LOCAL)
    val mode: StateFlow<SearchMode> = _mode.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** 全库影片（本地搜索在这个列表上过滤，支持中文与拼音） */
    private val allItems: StateFlow<List<WatchItem>> = repo.observeWatchItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCount: StateFlow<Int> = allItems
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 空状态里展示几个片名，点一下就能搜，不用打字 */
    val suggestions: StateFlow<List<String>> = allItems
        .map { list -> list.map { it.title }.distinct().take(12) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 本地搜索结果 */
    val localResults: StateFlow<List<WatchItem>> = combine(_query, allItems) { raw, items ->
        val keyword = raw.trim()
        if (keyword.isEmpty()) emptyList() else items.filter { matches(it, keyword) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _online = MutableStateFlow<OnlineState>(OnlineState.Idle)
    val online: StateFlow<OnlineState> = _online.asStateFlow()

    /** 本次会话里已经加入看单的 TMDB id，用于把按钮显示成「已添加」 */
    private val _addedIds = MutableStateFlow<Set<Int>>(emptySet())
    val addedIds: StateFlow<Set<Int>> = _addedIds.asStateFlow()

    private var onlineJob: Job? = null

    /** 拼音缓存：同一个片名只转一次 */
    private val pinyinCache = HashMap<String, String?>()

    fun setMode(mode: SearchMode) {
        if (_mode.value == mode) return
        _mode.value = mode
        if (mode == SearchMode.ONLINE) {
            startOnlineSearch(_query.value)
        } else {
            onlineJob?.cancel()
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
        if (_mode.value == SearchMode.ONLINE) startOnlineSearch(value)
    }

    fun retryOnline() {
        startOnlineSearch(_query.value)
    }

    /**
     * 本地匹配：片名 / 原名 / 备注（中文子串、忽略空格与大小写），
     * 再补一条拼音匹配（全拼 liulang、首字母 lldq），方便只用英文键盘输入。
     */
    private fun matches(item: WatchItem, keyword: String): Boolean {
        if (containsLoose(item.title, keyword)) return true
        if (containsLoose(item.originalTitle, keyword)) return true
        if (containsLoose(item.comment, keyword)) return true
        // 支持直接搜「科幻」「喜剧」等分类
        if (containsLoose(item.category.label, keyword)) return true

        val ascii = keyword.lowercase().replace(" ", "")
        if (ascii.length < 2 || !ascii.all { it in 'a'..'z' || it in '0'..'9' }) return false
        val pinyin = pinyinOf(item.title) ?: return false
        return pinyin.contains(ascii)
    }

    private fun containsLoose(haystack: String, needle: String): Boolean {
        if (haystack.isEmpty()) return false
        val a = haystack.replace(" ", "").lowercase()
        val b = needle.replace(" ", "").lowercase()
        return b.isNotEmpty() && a.contains(b)
    }

    private fun pinyinOf(title: String): String? {
        if (pinyinCache.containsKey(title)) return pinyinCache[title]
        return Pinyin.of(title).also { pinyinCache[title] = it }
    }

    /** 在线搜索（带 400ms 防抖，避免每个字都打一次 TMDB） */
    private fun startOnlineSearch(raw: String) {
        onlineJob?.cancel()
        val keyword = raw.trim()
        if (keyword.isEmpty()) {
            _online.value = OnlineState.Idle
            return
        }
        onlineJob = viewModelScope.launch {
            delay(400)
            _online.value = OnlineState.Loading
            // 阻塞式 HTTP 不能靠 withTimeout 打断，放到独立任务里，超时就不再等
            val job = viewModelScope.async(Dispatchers.IO) { TmdbApi.searchMulti(keyword) }
            val found = try {
                withTimeoutOrNull(10_000) { job.await() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _online.value = when {
                found == null -> OnlineState.Error("查询失败：网络无法访问 TMDB")
                found.isEmpty() -> OnlineState.Empty
                else -> OnlineState.Results(found)
            }
        }
    }

    /** 把在线搜索结果加入看单，onResult 回传提示文案 */
    fun addToWatchlist(item: TmdbApi.SearchItem, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val exists = repo.watchItemExists(item.title, item.type)
            if (exists) {
                _addedIds.value = _addedIds.value + item.tmdbId
                onResult("「${item.title}」已经在看单里了")
                return@launch
            }
            repo.addFromTmdb(item)
            _addedIds.value = _addedIds.value + item.tmdbId
            onResult("已添加「${item.title}」")
        }
    }
}
