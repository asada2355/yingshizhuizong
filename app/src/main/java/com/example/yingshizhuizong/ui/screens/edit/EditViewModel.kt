package com.example.yingshizhuizong.ui.screens.edit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.yingshizhuizong.YingshiApp
import com.example.yingshizhuizong.data.watchedAtFor
import com.example.yingshizhuizong.data.entity.WatchCategory
import com.example.yingshizhuizong.data.entity.WatchItem
import com.example.yingshizhuizong.data.entity.WatchStatus
import com.example.yingshizhuizong.data.entity.WatchType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class EditViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repo = (application as YingshiApp).repository
    private val itemId: Long = savedStateHandle["itemId"] ?: -1L
    val isEdit = itemId > 0

    val title = MutableStateFlow("")
    val originalTitle = MutableStateFlow("")
    val type = MutableStateFlow(WatchType.MOVIE)
    val status = MutableStateFlow(WatchStatus.WANT)
    val category = MutableStateFlow(WatchCategory.OTHER)
    val releaseDate = MutableStateFlow<Long?>(null)
    val rating = MutableStateFlow(0f)
    val comment = MutableStateFlow("")
    val reminderEnabled = MutableStateFlow(false)
    /** 用户手动填写的预告片链接（可留空，留空则保存后自动查找） */
    val trailerUrl = MutableStateFlow("")

    init {
        if (isEdit) {
            viewModelScope.launch {
                val item = repo.observeWatchItem(itemId).first() ?: return@launch
                title.value = item.title
                originalTitle.value = item.originalTitle
                type.value = item.type
                status.value = item.status
                category.value = item.category
                releaseDate.value = item.releaseDate
                rating.value = item.rating
                comment.value = item.comment
                reminderEnabled.value = item.reminderEnabled
                trailerUrl.value = item.trailerUrl
            }
        }
    }

    fun setReleaseDate(date: LocalDate?) {
        releaseDate.value = date
            ?.atStartOfDay(ZoneId.systemDefault())
            ?.toInstant()
            ?.toEpochMilli()
    }

    fun save(onDone: () -> Unit) {
        val trimmedTitle = title.value.trim()
        if (trimmedTitle.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val old = if (isEdit) repo.observeWatchItem(itemId).first() else null
            val trailerUrlValue = trailerUrl.value.trim()
            // 标题/类型没变且没手动链接时，沿用之前自动抓到的预告片
            val keepTrailer = old != null &&
                old.title == trimmedTitle &&
                old.type == type.value &&
                trailerUrlValue.isEmpty()
            val item = WatchItem(
                id = if (isEdit) itemId else 0,
                title = trimmedTitle,
                originalTitle = originalTitle.value.trim(),
                type = type.value,
                status = status.value,
                category = category.value,
                releaseDate = releaseDate.value,
                rating = rating.value,
                comment = comment.value,
                reminderEnabled = reminderEnabled.value,
                posterUrl = old?.posterUrl ?: "",
                // 若上映日期未变化则保留已提醒标记，否则重新提醒
                notifiedForDate = if (old != null && old.releaseDate == releaseDate.value) {
                    old.notifiedForDate
                } else null,
                watchedAt = watchedAtFor(
                    newStatus = status.value,
                    oldStatus = old?.status,
                    oldWatchedAt = old?.watchedAt,
                    now = now
                ),
                trailerKey = if (keepTrailer) old?.trailerKey.orEmpty() else "",
                trailerName = if (keepTrailer) old?.trailerName.orEmpty() else "",
                trailerUrl = trailerUrlValue,
                // 片名/类型没变就沿用之前解析到的 TMDB id，避免重新搜索
                tmdbId = if (old != null && old.title == trimmedTitle && old.type == type.value) {
                    old.tmdbId
                } else {
                    0
                },
                createdAt = old?.createdAt ?: now,
                updatedAt = now
            )
            val savedId = repo.saveWatchItem(item)
            // 新条目没选分类时，后台让 TMDB 自动识别一个（不阻塞保存）
            if (old == null && category.value == WatchCategory.OTHER) {
                val app = getApplication<YingshiApp>()
                app.appScope.launch {
                    repo.fetchAndApplyCategory(item.copy(id = savedId))
                }
            }
            // 新条目，或标题/类型变化时，后台补抓海报（不阻塞保存）
            val needPoster = old == null ||
                old.title != trimmedTitle ||
                old.type != type.value
            if (needPoster) {
                val app = getApplication<YingshiApp>()
                app.appScope.launch {
                    repo.fetchAndApplyPoster(item.copy(id = savedId))
                }
            }
            // 还没有预告片时后台自动查找（不阻塞保存）
            if (item.trailerKey.isBlank() && trailerUrlValue.isEmpty()) {
                val app = getApplication<YingshiApp>()
                app.appScope.launch {
                    repo.fetchAndApplyTrailer(item.copy(id = savedId))
                }
            }
            onDone()
        }
    }
}
