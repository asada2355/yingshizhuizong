package com.example.yingshizhuizong.data

import android.content.Context
import com.example.yingshizhuizong.data.dao.NoteDao
import com.example.yingshizhuizong.data.dao.WatchItemDao
import com.example.yingshizhuizong.data.entity.Note
import com.example.yingshizhuizong.data.entity.WatchCategory
import com.example.yingshizhuizong.data.entity.WatchItem
import com.example.yingshizhuizong.data.entity.WatchStatus
import com.example.yingshizhuizong.data.entity.WatchType
import com.example.yingshizhuizong.util.yearOf
import com.example.yingshizhuizong.widget.WidgetUpdater
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/** 数据仓库：统一封装 DAO 操作，并在数据变更后刷新桌面小组件 */
class Repository(context: Context, private val db: AppDatabase) {

    private val appContext = context.applicationContext

    /** 作品详情（简介/演职员）进程内缓存，避免每次进详情页都请求 TMDB */
    private val detailsCache = ConcurrentHashMap<Int, TmdbApi.Details>()

    val watchItems: WatchItemDao get() = db.watchItemDao()
    val notes: NoteDao get() = db.noteDao()

    fun observeWatchItems(): Flow<List<WatchItem>> = watchItems.observeAll()
    fun observeWatchItem(id: Long): Flow<WatchItem?> = watchItems.observeById(id)
    fun observeNotes(itemId: Long): Flow<List<Note>> = notes.observeForItem(itemId)

    /** 全库搜索：不限于当前列表/筛选条件，直接查数据库 */
    fun searchWatchItems(query: String): Flow<List<WatchItem>> = watchItems.observeSearch(query)

    /** 在线搜索添加前判重 */
    suspend fun watchItemExists(title: String, type: WatchType): Boolean =
        watchItems.countByTitle(title, type) > 0

    /** 解析并缓存 TMDB id（老数据第一次会查一次并写库） */
    suspend fun resolveTmdbId(item: WatchItem): Int {
        if (item.tmdbId > 0) return item.tmdbId
        val titles = listOf(item.originalTitle, item.title)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val id = TmdbApi.findId(item.type, titles, yearOf(item.releaseDate)) ?: return 0
        watchItems.updateTmdbId(item.id, id)
        return id
    }

    /** 作品简介 + 演职员；网络失败返回 null */
    suspend fun fetchDetails(item: WatchItem): TmdbApi.Details? {
        val id = resolveTmdbId(item)
        if (id <= 0) return null
        detailsCache[id]?.let { return it }
        val details = TmdbApi.fetchDetails(item.type, id) ?: return null
        detailsCache[id] = details
        return details
    }

    /** 把 TMDB 在线搜索结果加入看单（默认「想看」，海报/上映日期一并写入） */
    suspend fun addFromTmdb(item: TmdbApi.SearchItem): Long {
        val now = System.currentTimeMillis()
        return saveWatchItem(
            WatchItem(
                title = item.title,
                originalTitle = item.originalTitle,
                type = item.type,
                status = WatchStatus.WANT,
                category = item.category,
                posterUrl = item.posterUrl,
                releaseDate = item.releaseDate,
                tmdbId = item.tmdbId,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    suspend fun saveWatchItem(item: WatchItem): Long {
        val id = if (item.id == 0L) {
            watchItems.insert(item)
        } else {
            watchItems.update(item)
            item.id
        }
        refreshWidget()
        return id
    }

    suspend fun deleteWatchItem(item: WatchItem) {
        watchItems.delete(item)
        refreshWidget()
    }

    suspend fun addNote(note: Note) = notes.insert(note)

    suspend fun deleteNote(note: Note) = notes.delete(note)

    /** 修改单部影片的分类 */
    suspend fun updateCategory(itemId: Long, category: WatchCategory) {
        watchItems.updateCategory(itemId, category)
    }

    /**
     * 详情页拿到 TMDB 类型后，若影片还是「其他」就自动补全分类。
     * 用户手动选过的分类不会被覆盖。
     */
    suspend fun autoCategorize(itemId: Long, category: WatchCategory) {
        if (category == WatchCategory.OTHER) return
        // 重新读一次，避免用详情页打开时的旧数据覆盖用户刚选的分类
        val current = watchItems.getById(itemId) ?: return
        if (current.category == WatchCategory.OTHER) {
            watchItems.updateCategory(itemId, category)
        }
    }

    /** 为单个条目自动推断分类（只在还是「其他」时写入，失败静默跳过） */
    suspend fun fetchAndApplyCategory(item: WatchItem) {
        if (TmdbApi.API_KEY.isBlank()) return
        val current = watchItems.getById(item.id) ?: return
        if (current.category != WatchCategory.OTHER) return
        val titles = listOf(item.originalTitle, item.title)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val category = TmdbApi.searchCategory(item.type, titles, yearOf(item.releaseDate)) ?: return
        watchItems.updateCategory(item.id, category)
    }

    /** 为单个条目补抓海报（失败静默跳过，不影响使用） */
    suspend fun fetchAndApplyPoster(item: WatchItem) {
        if (TmdbApi.API_KEY.isBlank()) return
        val title = item.originalTitle.ifBlank { item.title }
        val url = TmdbApi.searchPoster(item.type, title, yearOf(item.releaseDate))
        if (url != null) watchItems.updatePoster(item.id, url)
    }

    /**
     * 为单个条目查找预告片并写入数据库；找不到返回 null。
     * 先按原名查，失败再按中文名查。
     */
    suspend fun fetchAndApplyTrailer(item: WatchItem): TmdbApi.Trailer? {
        if (TmdbApi.API_KEY.isBlank()) return null
        val titles = listOf(item.originalTitle, item.title)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val trailer = withTimeoutOrNull(12_000) {
            TmdbApi.fetchTrailer(item.type, titles, yearOf(item.releaseDate))
        } ?: return null
        watchItems.updateTrailer(item.id, trailer.key, trailer.name)
        return trailer
    }

    /** 为所有缺失海报的条目补抓（幂等，可从设置/看单页手动触发） */
    suspend fun enrichAllMissingPosters() = SeedData.enrichMissingPosters(db)

    private fun refreshWidget() = WidgetUpdater.notifyUpdate(appContext)
}
