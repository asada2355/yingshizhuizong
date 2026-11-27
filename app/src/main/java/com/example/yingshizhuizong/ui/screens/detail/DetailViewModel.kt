package com.example.yingshizhuizong.ui.screens.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.yingshizhuizong.YingshiApp
import com.example.yingshizhuizong.data.entity.Note
import com.example.yingshizhuizong.data.TmdbApi
import com.example.yingshizhuizong.data.entity.WatchItem
import com.example.yingshizhuizong.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 简介 / 演职员的加载状态 */
sealed interface DetailsUiState {
    data object Loading : DetailsUiState
    data class Ready(val details: TmdbApi.Details) : DetailsUiState
    data object Unavailable : DetailsUiState
}

class DetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repo = (application as YingshiApp).repository
    private val settings = SettingsRepository(application)
    private val itemId: Long = savedStateHandle["itemId"] ?: 0L

    /** 设置里开启后，点预告片封面直接跳到 YouTube App */
    val openTrailerExternally: StateFlow<Boolean> = settings.openTrailerExternallyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val item: StateFlow<WatchItem?> = repo.observeWatchItem(itemId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val notes: StateFlow<List<Note>> = repo.observeNotes(itemId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _trailerLoading = MutableStateFlow(true)
    val trailerLoading: StateFlow<Boolean> = _trailerLoading.asStateFlow()

    private val _trailerLookupFailed = MutableStateFlow(false)
    val trailerLookupFailed: StateFlow<Boolean> = _trailerLookupFailed.asStateFlow()

    private val _details = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val details: StateFlow<DetailsUiState> = _details.asStateFlow()

    init {
        viewModelScope.launch {
            val current = repo.observeWatchItem(itemId).first()
            if (current == null) {
                _trailerLoading.value = false
                _details.value = DetailsUiState.Unavailable
                return@launch
            }
            // 简介和演职员先开始拉（独立协程，不阻塞预告片查询）
            loadDetails(current)
            // 打开详情页时，若该条目还没有预告片，自动去 TMDB 找一次（找到会写库，之后不再重复查询）
            if (current.trailerKey.isBlank() && current.trailerUrl.isBlank()) {
                lookupTrailer(current)
            } else {
                _trailerLoading.value = false
            }
        }
    }

    /** 拉取简介 + 演职员（最多等 12 秒，超时显示「获取失败」） */
    private fun loadDetails(item: WatchItem) {
        viewModelScope.launch {
            _details.value = DetailsUiState.Loading
            val job = viewModelScope.async(Dispatchers.IO) { repo.fetchDetails(item) }
            val found = try {
                withTimeoutOrNull(12_000) { job.await() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (found != null) {
                // 老数据/手动添加的影片如果还没分类，用 TMDB 的类型自动补全
                repo.autoCategorize(item.id, found.category)
            }
            _details.value = if (found != null) DetailsUiState.Ready(found) else DetailsUiState.Unavailable
        }
    }

    fun retryDetails() {
        viewModelScope.launch {
            val current = repo.observeWatchItem(itemId).first() ?: return@launch
            loadDetails(current)
        }
    }

    /** 手动重试查找预告片 */
    fun retryTrailerLookup() {
        viewModelScope.launch {
            val current = repo.observeWatchItem(itemId).first() ?: return@launch
            if (current.trailerKey.isBlank() && current.trailerUrl.isBlank()) {
                lookupTrailer(current)
            } else {
                _trailerLoading.value = false
                _trailerLookupFailed.value = false
            }
        }
    }

    private suspend fun lookupTrailer(current: WatchItem) {
        _trailerLoading.value = true
        _trailerLookupFailed.value = false
        // HttpURLConnection 是阻塞调用，withTimeout 打断不了它；
        // 所以放到独立任务里，超时后直接不再等待（任务自己跑完会写库，无害）。
        val job = viewModelScope.async(Dispatchers.IO) { repo.fetchAndApplyTrailer(current) }
        val found = try {
            withTimeoutOrNull(10_000) { job.await() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        _trailerLoading.value = false
        _trailerLookupFailed.value = found == null
    }

    fun addNote(content: String, episode: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            repo.addNote(Note(watchItemId = itemId, content = content.trim(), episode = episode.trim()))
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch { repo.deleteNote(note) }
    }

    fun toggleReminder(enabled: Boolean) {
        viewModelScope.launch {
            val current = repo.observeWatchItem(itemId).first() ?: return@launch
            repo.saveWatchItem(current.copy(reminderEnabled = enabled))
        }
    }

    fun deleteItem(onDone: () -> Unit) {
        viewModelScope.launch {
            val current = repo.observeWatchItem(itemId).first() ?: return@launch
            repo.deleteWatchItem(current)
            onDone()
        }
    }
}
