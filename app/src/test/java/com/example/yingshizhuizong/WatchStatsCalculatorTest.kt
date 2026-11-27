package com.example.yingshizhuizong

import com.example.yingshizhuizong.data.entity.WatchCategory
import com.example.yingshizhuizong.data.entity.WatchItem
import com.example.yingshizhuizong.data.entity.WatchStatus
import com.example.yingshizhuizong.data.entity.WatchType
import com.example.yingshizhuizong.data.stats.WatchStatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WatchStatsCalculatorTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun millis(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun watched(
        id: Long,
        title: String,
        year: Int,
        month: Int,
        day: Int,
        rating: Float = 0f,
        category: WatchCategory = WatchCategory.DRAMA,
        type: WatchType = WatchType.MOVIE,
        createdAt: Long = millis(year, month, day),
    ) = WatchItem(
        id = id,
        title = title,
        type = type,
        status = WatchStatus.WATCHED,
        category = category,
        rating = rating,
        watchedAt = millis(year, month, day),
        createdAt = createdAt,
        updatedAt = millis(year, month, day),
    )

    @Test
    fun `overview counts status and type`() {
        val items = listOf(
            watched(1, "A", 2026, 1, 5, 8f, WatchCategory.SCI_FI),
            watched(2, "B", 2026, 3, 9, 6f, WatchCategory.DRAMA, WatchType.TV_SERIES),
            WatchItem(id = 3, title = "C", status = WatchStatus.WANT),
            WatchItem(id = 4, title = "D", status = WatchStatus.WATCHING),
            WatchItem(id = 5, title = "E", status = WatchStatus.DROPPED),
        )
        val stats = WatchStatsCalculator.overview(items, now = millis(2026, 11, 1), zone = zone)

        assertEquals(5, stats.total)
        assertEquals(2, stats.watchedCount)
        assertEquals(1, stats.wantCount)
        assertEquals(1, stats.watchingCount)
        assertEquals(1, stats.droppedCount)
        // 已看的 2 部里 1 部电影；另外 3 部未看条目默认也是电影
        assertEquals(4, stats.movieCount)
        assertEquals(1, stats.tvCount)
        assertEquals(7f, stats.averageRating, 0.001f)
        assertEquals(2, stats.watchedThisYear)
        assertEquals(2, stats.categoriesThisYear)
        assertEquals(5, stats.categories.sumOf { it.count })
    }

    @Test
    fun `categories sorted by count`() {
        val items = listOf(
            watched(1, "A", 2026, 1, 1, category = WatchCategory.SCI_FI),
            watched(2, "B", 2026, 1, 2, category = WatchCategory.SCI_FI),
            watched(3, "C", 2026, 1, 3, category = WatchCategory.COMEDY),
        )
        val stats = WatchStatsCalculator.overview(items, now = millis(2026, 6, 1), zone = zone)
        assertEquals(WatchCategory.SCI_FI, stats.categories.first().category)
        assertEquals(2, stats.categories.first().count)
    }

    @Test
    fun `months reflect watchedAt of current year only`() {
        val items = listOf(
            watched(1, "A", 2026, 1, 5),
            watched(2, "B", 2026, 1, 20),
            watched(3, "C", 2026, 7, 1),
            watched(4, "D", 2025, 12, 31),
        )
        val stats = WatchStatsCalculator.overview(items, now = millis(2026, 11, 1), zone = zone)
        assertEquals(2, stats.months.first { it.month == 1 }.count)
        assertEquals(1, stats.months.first { it.month == 7 }.count)
        assertEquals(0, stats.months.first { it.month == 12 }.count)
        assertEquals(3, stats.watchedThisYear)
    }

    @Test
    fun `rating buckets group watched items`() {
        val items = listOf(
            watched(1, "A", 2026, 1, 1, 8f),
            watched(2, "B", 2026, 1, 2, 8f),
            watched(3, "C", 2026, 1, 3, 5f),
            watched(4, "D", 2026, 1, 4, 0f), // 未评分
        )
        val stats = WatchStatsCalculator.overview(items, now = millis(2026, 6, 1), zone = zone)
        assertEquals(2, stats.ratingBuckets.first { it.score == 8 }.count)
        assertEquals(1, stats.ratingBuckets.first { it.score == 5 }.count)
        assertEquals(3, stats.ratedCount)
    }

    @Test
    fun `year report picks top category highest rating and longest wait`() {
        val items = listOf(
            watched(1, "早看", 2026, 2, 1, 9f, WatchCategory.SCI_FI, createdAt = millis(2026, 1, 1)),
            watched(2, "晚看", 2026, 3, 1, 7f, WatchCategory.DRAMA, createdAt = millis(2025, 1, 1)),
            watched(3, "去年", 2025, 4, 1, 10f, WatchCategory.COMEDY),
        )
        val report = WatchStatsCalculator.yearReport(items, year = 2026, zone = zone)

        assertEquals(2026, report.year)
        assertEquals(2, report.watchedCount)
        assertEquals(2, report.movieCount)
        assertEquals(8f, report.averageRating, 0.001f)
        assertEquals(WatchCategory.SCI_FI, report.topCategory?.category)
        assertEquals("早看", report.highestRated?.title)
        assertEquals("晚看", report.longestWait?.title)
        // 2025-01-01 → 2026-03-01 = 424 天
        assertEquals(424, report.longestWaitDays)
    }

    @Test
    fun `items without watchedAt fall back to updatedAt`() {
        val item = WatchItem(
            id = 1,
            title = "老数据",
            status = WatchStatus.WATCHED,
            watchedAt = null,
            updatedAt = millis(2026, 5, 1),
        )
        val report = WatchStatsCalculator.yearReport(listOf(item), year = 2026, zone = zone)
        assertEquals(1, report.watchedCount)
    }

    @Test
    fun `series in progress lists only watching tv with progress`() {
        val items = listOf(
            WatchItem(
                id = 1, title = "剧集A", type = WatchType.TV_SERIES,
                status = WatchStatus.WATCHING, seasonNumber = 2, episodeNumber = 5,
            ),
            WatchItem(
                id = 2, title = "剧集B", type = WatchType.TV_SERIES,
                status = WatchStatus.WATCHING, seasonNumber = 0, episodeNumber = 0,
            ),
            WatchItem(
                id = 3, title = "剧集C", type = WatchType.TV_SERIES,
                status = WatchStatus.WATCHED, seasonNumber = 1, episodeNumber = 10,
            ),
            WatchItem(
                id = 4, title = "电影", type = WatchType.MOVIE,
                status = WatchStatus.WATCHING, seasonNumber = 1, episodeNumber = 1,
            ),
        )
        val stats = WatchStatsCalculator.overview(items, zone = zone)
        assertEquals(1, stats.seriesInProgress.size)
        assertEquals("剧集A", stats.seriesInProgress.first().item.title)
        assertEquals(2, stats.seriesInProgress.first().seasonNumber)
        assertEquals(5, stats.seriesInProgress.first().episodeNumber)
    }

    @Test
    fun `empty list produces empty stats`() {
        val stats = WatchStatsCalculator.overview(emptyList(), zone = zone)
        assertEquals(0, stats.total)
        assertEquals(0f, stats.averageRating, 0.001f)
        assertNull(stats.topRated)
    }
}