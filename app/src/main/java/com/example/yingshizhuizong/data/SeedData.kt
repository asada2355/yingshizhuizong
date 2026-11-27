package com.example.yingshizhuizong.data

import com.example.yingshizhuizong.data.entity.WatchCategory
import com.example.yingshizhuizong.data.entity.WatchItem
import com.example.yingshizhuizong.data.entity.WatchStatus
import com.example.yingshizhuizong.data.entity.WatchType
import com.example.yingshizhuizong.util.yearOf
import java.time.LocalDate
import java.time.ZoneId

/**
 * 首次启动的示例数据：填充一批真实的影视作品，
 * 让看单、上映提醒等页面开箱即有内容。
 */
object SeedData {

    /** 仅在数据库为空时填充一次 */
    suspend fun seedIfEmpty(db: AppDatabase) {
        if (db.watchItemDao().count() > 0) return
        val now = System.currentTimeMillis()
        val items = entries.mapIndexed { i, e -> e.toWatchItem(now - i * 1000L) }
        db.watchItemDao().insertAll(items)
    }

    /**
     * 为还没分类的条目自动补全分类（幂等，每次启动可调用）。
     * 只处理「其他」，用户手动选过的分类不会被覆盖；网络失败时留到下次启动再试。
     */
    suspend fun enrichMissingCategories(db: AppDatabase) {
        if (TmdbApi.API_KEY.isBlank()) return
        val missing = db.watchItemDao().getUncategorized()
        if (missing.isEmpty()) return
        // 一次启动最多处理 50 部，避免片库很大时启动后台任务跑太久
        missing.take(50).forEach { item ->
            val titles = listOf(item.originalTitle, item.title)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            val category = TmdbApi.searchCategory(item.type, titles, yearOf(item.releaseDate))
                ?: return@forEach
            db.watchItemDao().updateCategory(item.id, category)
        }
    }

    /** 为缺失海报的条目补抓海报（幂等，每次启动都可调用） */
    suspend fun enrichMissingPosters(db: AppDatabase) {
        if (TmdbApi.API_KEY.isBlank()) return
        val missing = db.watchItemDao().getWithoutPoster()
        if (missing.isEmpty()) return
        missing.forEach { item ->
            val title = item.originalTitle.ifBlank { item.title }
            val url = TmdbApi.searchPoster(item.type, title, yearOf(item.releaseDate))
            if (url != null) db.watchItemDao().updatePoster(item.id, url)
        }
    }

    private data class Entry(
        val title: String,
        val originalTitle: String,
        val type: WatchType,
        val status: WatchStatus,
        val category: WatchCategory,
        val releaseDate: Long?,
        val rating: Float,
        val comment: String = "",
        val reminderEnabled: Boolean = false,
    )

    private fun Entry.toWatchItem(timestamp: Long) = WatchItem(
        title = title,
        originalTitle = originalTitle,
        type = type,
        status = status,
        category = category,
        releaseDate = releaseDate,
        rating = rating,
        comment = comment,
        reminderEnabled = reminderEnabled,
        createdAt = timestamp,
        updatedAt = timestamp,
    )

    private val entries = listOf(
        // —— 电影 · 已看 ——
        Entry("流浪地球2", "The Wandering Earth II", WatchType.MOVIE, WatchStatus.WATCHED, WatchCategory.SCI_FI, date(2023, 1, 22), 8.3f, "国产科幻里程碑"),
        Entry("奥本海默", "Oppenheimer", WatchType.MOVIE, WatchStatus.WATCHED, WatchCategory.DRAMA, date(2023, 8, 30), 8.8f, "诺兰导演"),
        Entry("长安三万里", "Chang An", WatchType.MOVIE, WatchStatus.WATCHED, WatchCategory.ANIMATION, date(2023, 7, 8), 8.3f),
        Entry("满江红", "Full River Red", WatchType.MOVIE, WatchStatus.DROPPED, WatchCategory.MYSTERY, date(2023, 1, 22), 7.0f),
        Entry("热辣滚烫", "YOLO", WatchType.MOVIE, WatchStatus.WATCHED, WatchCategory.COMEDY, date(2024, 2, 10), 7.8f),
        Entry("第二十条", "Article 20", WatchType.MOVIE, WatchStatus.WATCHED, WatchCategory.DRAMA, date(2024, 2, 10), 7.7f),
        Entry("沙丘2", "Dune: Part Two", WatchType.MOVIE, WatchStatus.WATCHED, WatchCategory.SCI_FI, date(2024, 3, 8), 8.4f),
        Entry("头脑特工队2", "Inside Out 2", WatchType.MOVIE, WatchStatus.WATCHED, WatchCategory.ANIMATION, date(2024, 6, 21), 8.4f),
        Entry("抓娃娃", "Successor", WatchType.MOVIE, WatchStatus.WATCHED, WatchCategory.COMEDY, date(2024, 7, 16), 7.5f),
        Entry("哪吒之魔童闹海", "Ne Zha 2", WatchType.MOVIE, WatchStatus.WATCHED, WatchCategory.ANIMATION, date(2025, 1, 29), 8.3f),

        // —— 剧集 ——
        Entry("漫长的季节", "The Long Season", WatchType.TV_SERIES, WatchStatus.WATCHED, WatchCategory.MYSTERY, date(2023, 4, 22), 9.4f, "国产剧天花板"),
        Entry("狂飙", "The Knockout", WatchType.TV_SERIES, WatchStatus.WATCHED, WatchCategory.CRIME, date(2023, 1, 14), 8.5f),
        Entry("三体", "Three-Body", WatchType.TV_SERIES, WatchStatus.WATCHED, WatchCategory.SCI_FI, date(2023, 1, 15), 8.7f),
        Entry("繁花", "Blossoms Shanghai", WatchType.TV_SERIES, WatchStatus.WATCHED, WatchCategory.DRAMA, date(2023, 12, 27), 8.7f),
        Entry("我的阿勒泰", "To the Wonder", WatchType.TV_SERIES, WatchStatus.WATCHING, WatchCategory.DRAMA, date(2024, 5, 7), 8.9f),
        Entry("小巷人家", "Romance in the Alley", WatchType.TV_SERIES, WatchStatus.WATCHING, WatchCategory.DRAMA, date(2024, 10, 28), 8.2f),

        // —— 想看 · 即将上映 ——
        Entry("流浪地球3", "The Wandering Earth III", WatchType.MOVIE, WatchStatus.WANT, WatchCategory.SCI_FI, date(2027, 2, 6), 0f, reminderEnabled = true),
        Entry("复仇者联盟：秘密战争", "Avengers: Secret Wars", WatchType.MOVIE, WatchStatus.WANT, WatchCategory.ACTION, date(2027, 5, 7), 0f, reminderEnabled = true),
        Entry("冰雪奇缘3", "Frozen III", WatchType.MOVIE, WatchStatus.WANT, WatchCategory.ANIMATION, date(2027, 11, 24), 0f, reminderEnabled = true),
        Entry("阿凡达4：图鲲骑士", "Avatar 4", WatchType.MOVIE, WatchStatus.WANT, WatchCategory.SCI_FI, date(2029, 12, 21), 0f),
    )

    /** 上映日期当天 0 点的 epoch 毫秒，与 util.formatDate / daysUntil 的转换保持一致 */
    private fun date(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
