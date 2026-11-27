package com.example.yingshizhuizong.ui.screens.trailer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.yingshizhuizong.YingshiApp
import com.example.yingshizhuizong.data.TrailerLink
import com.example.yingshizhuizong.data.entity.WatchItem
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

/** 预告片播放源 */
sealed interface TrailerSource {
    /** YouTube 视频（用内嵌播放器播放） */
    data class YouTube(val videoId: String) : TrailerSource

    /** 直链视频（mp4 / m3u8 等，用 <video> 播放） */
    data class Direct(val url: String) : TrailerSource

    /** 普通网页链接（站外播放页等，直接用 WebView 打开） */
    data class Web(val url: String) : TrailerSource
}

/** 播放页状态 */
sealed interface TrailerState {
    data object Loading : TrailerState

    data class Ready(
        val source: TrailerSource,
        val title: String,
        val externalUrl: String,
    ) : TrailerState

    data class NotFound(val message: String) : TrailerState
}

class TrailerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val repo = (application as YingshiApp).repository
    private val itemId: Long = savedStateHandle["itemId"] ?: 0L

    val item: StateFlow<WatchItem?> = repo.observeWatchItem(itemId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _state = MutableStateFlow<TrailerState>(TrailerState.Loading)
    val state: StateFlow<TrailerState> = _state.asStateFlow()

    init {
        resolve()
    }

    /** 解析播放源：手动链接 > 已缓存的 YouTube key > 现场去 TMDB 查一次 */
    fun resolve() {
        viewModelScope.launch {
            _state.value = TrailerState.Loading
            val current = repo.observeWatchItem(itemId).first()
            if (current == null) {
                _state.value = TrailerState.NotFound("这部影片已经被删除了")
                return@launch
            }
            val manualUrl = current.trailerUrl.trim()
            if (manualUrl.isNotEmpty()) {
                val youtubeId = TrailerLink.youtubeId(manualUrl)
                _state.value = TrailerState.Ready(
                    source = when {
                        youtubeId != null -> TrailerSource.YouTube(youtubeId)
                        TrailerLink.isDirectVideo(manualUrl) -> TrailerSource.Direct(manualUrl)
                        else -> TrailerSource.Web(manualUrl)
                    },
                    title = current.trailerName.ifBlank { "预告片" },
                    externalUrl = if (youtubeId != null) TrailerLink.watchUrl(youtubeId) else manualUrl,
                )
                return@launch
            }
            val cachedKey = current.trailerKey.trim()
            if (cachedKey.isNotEmpty()) {
                _state.value = TrailerState.Ready(
                    source = TrailerSource.YouTube(cachedKey),
                    title = current.trailerName.ifBlank { "预告片" },
                    externalUrl = TrailerLink.watchUrl(cachedKey),
                )
                return@launch
            }
            // 同详情页：阻塞式请求放到独立任务，超时就不再等
            val job = viewModelScope.async(Dispatchers.IO) { repo.fetchAndApplyTrailer(current) }
            val found = try {
                withTimeoutOrNull(10_000) { job.await() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _state.value = if (found != null) {
                TrailerState.Ready(
                    source = TrailerSource.YouTube(found.key),
                    title = found.name.ifBlank { "预告片" },
                    externalUrl = TrailerLink.watchUrl(found.key),
                )
            } else {
                TrailerState.NotFound(
                    "没有找到这部影片的预告片。\n可能是 TMDB 未收录，或网络无法访问 TMDB。\n可以在编辑页手动填写视频链接。"
                )
            }
        }
    }
}
