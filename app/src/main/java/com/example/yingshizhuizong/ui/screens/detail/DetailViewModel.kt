package com.example.yingshizhuizong.ui.screens.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.yingshizhuizong.YingshiApp
import com.example.yingshizhuizong.data.EpisodeProgress
import com.example.yingshizhuizong.data.episodeProgressFor
import com.example.yingshizhuizong.data.entity.Note
import com.example.yingshizhuizong.data.TmdbApi
import com.example.yingshizhuizong.data.entity.WatchItem
import com.example.yingshizhuizong.data.entity.WatchStatus
import com.example.yingshizhuizong.data.entity.WatchType
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

/** 剧集追更的加载状态 */
sealed interface SeasonsUiState {
    data object Idle : SeasonsUiState
    data object Loading : SeasonsUiState
    data class Ready(val seasons: List<TmdbApi.Season>) : SeasonsUiState
    data object Unavailable : SeasonsUiState
}

/** 某一季集列表的加载状态 */
sealed interface EpisodesUiState {
    data object Idle : EpisodesUiState
    data object Loading : EpisodesUiState
    data class Ready(val episodes: List<TmdbApi.Episode>) : EpisodesUiState
    data object Unavailable : EpisodesUiState
}

/** 相似影片的加载状态 */
sealed interface RecommendationsUiState {
    data object Idle : RecommendationsUiState
    data object Loading : RecommendationsUiState
    data class Ready(val items: List<TmdbApi.Recommendation>) : RecommendationsUiState
    data object Unavailable : RecommendationsUiState
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

    // —— 剧集追更 ——
    private val _seasons = MutableStateFlow<SeasonsUiState>(SeasonsUiState.Idle)
    val seasons: StateFlow<SeasonsUiState> = _seasons.asStateFlow()

    private val _episodes = MutableStateFlow<EpisodesUiState>(EpisodesUiState.Idle)
    val episodes: StateFlow<EpisodesUiState> = _episodes.asStateFlow()

    private val _selectedSeason = MutableStateFlow(1)
    val selectedSeason: StateFlow<Int> = _selectedSeason.asStateFlow()

    private val episodesCache = mutableMapOf<Int, List<TmdbApi.Episode>>()

    // —— 相似影片 ——
    private val _recommendations = MutableStateFlow<RecommendationsUiState>(RecommendationsUiState.Idle)
    val recommendations: StateFlow<RecommendationsUiState> = _recommendations.asStateFlow()

    private val _addedRecommendationIds = MutableStateFlow<Set<Int>>(emptySet())
    val addedRecommendationIds: StateFlow<Set<Int>> = _addedRecommendationIds.asStateFlow()

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
            // 剧集：加载季列表；电影：加载相似推荐
            if (current.type == WatchType.TV_SERIES) {
                loadSeasons(current)
            }
            loadRecommendations(current)
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

    // —— 剧集追更 ——

    /** 加载季列表；已有进度时默认选到当前季 */
    fun loadSeasons(current: WatchItem? = null) {
        if (_seasons.value is SeasonsUiState.Ready || _seasons.value is SeasonsUiState.Loading) return
        viewModelScope.launch {
            val item = current ?: repo.observeWatchItem(itemId).first() ?: return@launch
            _seasons.value = SeasonsUiState.Loading
            val job = viewModelScope.async(Dispatchers.IO) { repo.fetchSeasons(item) }
            val found = try {
                withTimeoutOrNull(15_000) { job.await() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _seasons.value = if (found != null) SeasonsUiState.Ready(found) else SeasonsUiState.Unavailable
            if (found != null && found.isNotEmpty()) {
                // 有进度时定位到当前季，否则默认第一季
                val target = item.seasonNumber.coerceAtLeast(1)
                selectSeason(target)
            }
        }
    }

    fun retrySeasons() {
        _seasons.value = SeasonsUiState.Idle
        _episodes.value = EpisodesUiState.Idle
        loadSeasons()
    }

    /** 切换季，并加载这一季的集列表（带内存缓存） */
    fun selectSeason(seasonNumber: Int) {
        if (seasonNumber <= 0) return
        _selectedSeason.value = seasonNumber
        val cached = episodesCache[seasonNumber]
        if (cached != null) {
            _episodes.value = EpisodesUiState.Ready(cached)
            return
        }
        viewModelScope.launch {
            val item = repo.observeWatchItem(itemId).first() ?: return@launch
            _episodes.value = EpisodesUiState.Loading
            val job = viewModelScope.async(Dispatchers.IO) { repo.fetchEpisodes(item, seasonNumber) }
            val found = try {
                withTimeoutOrNull(15_000) { job.await() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (found != null) episodesCache[seasonNumber] = found
            // 如果不是当前选中的季，忽略（避免快速切季时错乱）
            if (_selectedSeason.value != seasonNumber) return@launch
            _episodes.value = if (found != null) EpisodesUiState.Ready(found) else EpisodesUiState.Unavailable
        }
    }

    /**
     * 标记/取消标记某一集看过。
     * 标记时：进度推进到这一集，状态改为「在看」（或最后一集时「已看」）。
     * 取消标记时：进度回退到这一集的前一集。
     */
    fun toggleEpisode(season: Int, episode: Int, watched: Boolean) {
        viewModelScope.launch {
            val current = repo.observeWatchItem(itemId).first() ?: return@launch
            val total = totalEpisodesOf(season) ?: 0
            val progress = episodeProgressFor(
                season = season,
                episode = episode,
                watched = watched,
                totalEpisodes = total,
                isLastSeason = isLastSeason(season),
                old = EpisodeProgress(current.seasonNumber, current.episodeNumber, current.status),
            )
            val now = System.currentTimeMillis()
            repo.saveWatchItem(
                current.copy(
                    seasonNumber = progress.seasonNumber,
                    episodeNumber = progress.episodeNumber,
                    status = progress.status,
                    watchedAt = if (progress.status == WatchStatus.WATCHED) {
                        current.watchedAt ?: now
                    } else {
                        null
                    },
                    updatedAt = now,
                )
            )
        }
    }

    /** 把整季标记为已看完 */
    fun markSeasonWatched(season: Int) {
        val total = totalEpisodesOf(season) ?: return
        toggleEpisode(season, total, true)
    }

    private fun totalEpisodesOf(season: Int): Int? =
        (_seasons.value as? SeasonsUiState.Ready)?.seasons
            ?.firstOrNull { it.seasonNumber == season }?.episodeCount

    private fun isLastSeason(season: Int): Boolean {
        val ready = _seasons.value as? SeasonsUiState.Ready ?: return false
        return ready.seasons.maxOfOrNull { it.seasonNumber } == season
    }

    // —— 相似影片 ——

    fun loadRecommendations(current: WatchItem? = null) {
        if (_recommendations.value is RecommendationsUiState.Ready ||
            _recommendations.value is RecommendationsUiState.Loading
        ) {
            return
        }
        viewModelScope.launch {
            val item = current ?: repo.observeWatchItem(itemId).first() ?: return@launch
            _recommendations.value = RecommendationsUiState.Loading
            val job = viewModelScope.async(Dispatchers.IO) { repo.fetchRecommendations(item) }
            val found = try {
                withTimeoutOrNull(15_000) { job.await() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _recommendations.value = if (found != null) {
                // 过滤掉当前这一部
                RecommendationsUiState.Ready(found.filter { it.tmdbId != item.tmdbId })
            } else {
                RecommendationsUiState.Unavailable
            }
        }
    }

    fun retryRecommendations() {
        _recommendations.value = RecommendationsUiState.Idle
        loadRecommendations()
    }

    /** 相似影片一键加入看单 */
    fun addRecommendation(rec: TmdbApi.Recommendation, onResult: (String) -> Unit = {}) {
        viewModelScope.launch {
            if (rec.tmdbId > 0 && repo.containsTmdbId(rec.tmdbId)) {
                _addedRecommendationIds.value = _addedRecommendationIds.value + rec.tmdbId
                onResult("「${rec.title}」已经在看单里了")
                return@launch
            }
            repo.addRecommendation(rec)
            _addedRecommendationIds.value = _addedRecommendationIds.value + rec.tmdbId
            onResult("已添加「${rec.title}」")
        }
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
