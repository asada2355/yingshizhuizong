package com.example.yingshizhuizong.data.stats

import com.example.yingshizhuizong.data.entity.WatchCategory
import com.example.yingshizhuizong.data.entity.WatchItem
import com.example.yingshizhuizong.data.entity.WatchStatus
import com.example.yingshizhuizong.data.entity.WatchType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 某一分类的数量 */
data class CategoryCount(val category: WatchCategory, val count: Int)

/** 某一评分档的数量（1 分一档，0 分是「未评分」） */
data class RatingBucket(val score: Int, val count: Int)

/** 某个月的已看数量 */
data class MonthCount(val month: Int, val count: Int)

/** 正在追的剧集（有集进度） */
data class SeriesProgressItem(
    val item: WatchItem,
    val seasonNumber: Int,
    val episodeNumber: Int,
)

/** 观影统计的完整结果 */
data class WatchStats(
    val total: Int = 0,
    val wantCount: Int = 0,
    val watchingCount: Int = 0,
    val watchedCount: Int = 0,
    val droppedCount: Int = 0,
    val movieCount: Int = 0,
    val tvCount: Int = 0,
    val ratedCount: Int = 0,
    val averageRating: Float = 0f,
    /** 分类分布，按数量从多到少 */
    val categories: List<CategoryCount> = emptyList(),
    /** 评分分布 1..10，下标 0 对应 1 分 */
    val ratingBuckets: List<RatingBucket> = emptyList(),
    /** 当前年份 1..12 月各月的已看数量 */
    val months: List<MonthCount> = emptyList(),
    /** 今年已看数量 */
    val watchedThisYear: Int = 0,
    /** 今年看过的分类数量 */
    val categoriesThisYear: Int = 0,
    /** 评分最高的已看影片 */
    val topRated: WatchItem? = null,
    /** 正在追的剧集（有集进度且未看完） */
    val seriesInProgress: List<SeriesProgressItem> = emptyList(),
)

/** 年度报告：针对某个年份的汇总 */
data class YearReport(
    val year: Int = LocalDate.now().year,
    val watchedCount: Int = 0,
    val movieCount: Int = 0,
    val tvCount: Int = 0,
    val averageRating: Float = 0f,
    /** 这一年最常看的分类 */
    val topCategory: CategoryCount? = null,
    /** 这一年分类分布 */
    val categories: List<CategoryCount> = emptyList(),
    /** 评分最高的一部 */
    val highestRated: WatchItem? = null,
    /** 这一年里等待时间最久（添加→看完）的一部 */
    val longestWait: WatchItem? = null,
    val longestWaitDays: Long = 0,
)

/**
 * 观影统计计算。
 *
 * 纯函数、不依赖 Android，方便单元测试：
 * 传入看单列表和「当前时间」，返回统计结果。
 */
object WatchStatsCalculator {

    /** 某部影片「看完」的时间：优先用 watchedAt，老数据兜底 updatedAt */
    private fun watchedTimeOf(item: WatchItem): Long? {
        if (item.status != WatchStatus.WATCHED) return null
        return item.watchedAt ?: item.updatedAt
    }

    private fun yearOf(epochMillis: Long, zone: ZoneId): Int =
        Instant.ofEpochMilli(epochMillis).atZone(zone).year

    private fun monthOf(epochMillis: Long, zone: ZoneId): Int =
        Instant.ofEpochMilli(epochMillis).atZone(zone).monthValue

    /** 总览：状态、类型、评分、分类、当前年份月度、今年数据 */
    fun overview(
        items: List<WatchItem>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): WatchStats {
        val currentYear = Instant.ofEpochMilli(now).atZone(zone).year
        val watched = items.filter { it.status == WatchStatus.WATCHED }
        val rated = watched.filter { it.rating > 0f }

        val categories = items
            .groupingBy { it.category }
            .eachCount()
            .map { (category, count) -> CategoryCount(category, count) }
            .sortedWith(compareByDescending<CategoryCount> { it.count }.thenBy { it.category.ordinal })

        val ratingBuckets = (1..10).map { score ->
            RatingBucket(score, watched.count { it.rating.roundToInt() == score })
        }

        val watchedThisYear = watched.filter { item ->
            watchedTimeOf(item)?.let { t -> yearOf(t, zone) == currentYear } == true
        }

        val months = (1..12).map { month ->
            MonthCount(
                month = month,
                count = watchedThisYear.count { item ->
                    watchedTimeOf(item)?.let { monthOf(it, zone) == month } == true
                },
            )
        }

        val topRated = rated.maxWithOrNull(
            compareBy<WatchItem> { it.rating }.thenBy { it.watchedAt ?: it.updatedAt }
        )

        // 正在追的剧集：是在看的剧集，且有过集进度
        val seriesInProgress = items
            .filter {
                it.type == WatchType.TV_SERIES &&
                    it.status == WatchStatus.WATCHING &&
                    it.seasonNumber > 0
            }
            .sortedByDescending { it.updatedAt }
            .map { SeriesProgressItem(it, it.seasonNumber, it.episodeNumber) }

        return WatchStats(
            total = items.size,
            wantCount = items.count { it.status == WatchStatus.WANT },
            watchingCount = items.count { it.status == WatchStatus.WATCHING },
            watchedCount = watched.size,
            droppedCount = items.count { it.status == WatchStatus.DROPPED },
            movieCount = items.count { it.type == WatchType.MOVIE },
            tvCount = items.count { it.type == WatchType.TV_SERIES },
            ratedCount = rated.size,
            averageRating = if (rated.isEmpty()) 0f else rated.sumOf { it.rating.toDouble() }.toFloat() / rated.size,
            categories = categories,
            ratingBuckets = ratingBuckets,
            months = months,
            watchedThisYear = watchedThisYear.size,
            categoriesThisYear = watchedThisYear.map { it.category }.distinct().size,
            topRated = topRated,
            seriesInProgress = seriesInProgress,
        )
    }

    /** 年度报告：统计指定年份的已看影片 */
    fun yearReport(
        items: List<WatchItem>,
        year: Int = LocalDate.now().year,
        zone: ZoneId = ZoneId.systemDefault(),
    ): YearReport {
        val watchedInYear = items.filter { item ->
            watchedTimeOf(item)?.let { yearOf(it, zone) == year } == true
        }
        val rated = watchedInYear.filter { it.rating > 0f }

        val categories = watchedInYear
            .groupingBy { it.category }
            .eachCount()
            .map { (category, count) -> CategoryCount(category, count) }
            .sortedWith(compareByDescending<CategoryCount> { it.count }.thenBy { it.category.ordinal })

        val highestRated = rated.maxWithOrNull(compareBy { it.rating })

        // 等待最久：添加时间 → 看完时间，取天数最大的一部
        val longestWait = watchedInYear
            .mapNotNull { item ->
                val watchedAt = watchedTimeOf(item) ?: return@mapNotNull null
                val days = ChronoUnit.DAYS.between(
                    Instant.ofEpochMilli(item.createdAt).atZone(zone).toLocalDate(),
                    Instant.ofEpochMilli(watchedAt).atZone(zone).toLocalDate()
                )
                if (days >= 0) item to days else null
            }
            .maxByOrNull { it.second }

        return YearReport(
            year = year,
            watchedCount = watchedInYear.size,
            movieCount = watchedInYear.count { it.type == WatchType.MOVIE },
            tvCount = watchedInYear.count { it.type == WatchType.TV_SERIES },
            averageRating = if (rated.isEmpty()) 0f else rated.sumOf { it.rating.toDouble() }.toFloat() / rated.size,
            topCategory = categories.firstOrNull(),
            categories = categories,
            highestRated = highestRated,
            longestWait = longestWait?.first,
            longestWaitDays = longestWait?.second ?: 0,
        )
    }

    /** 有记录的年份，供年度报告切换；没有记录时返回当前年份 */
    fun availableYears(
        items: List<WatchItem>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Int> {
        val years = items.mapNotNull { watchedTimeOf(it)?.let { t -> yearOf(t, zone) } }.distinct()
        return (years + Instant.ofEpochMilli(now).atZone(zone).year).distinct().sortedDescending()
    }

    private fun Float.roundToInt(): Int = kotlin.math.round(this).toInt()
}