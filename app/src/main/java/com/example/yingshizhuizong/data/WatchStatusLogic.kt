package com.example.yingshizhuizong.data

import com.example.yingshizhuizong.data.entity.WatchStatus

/**
 * 状态变更时同步维护「看完时间」。
 *
 * - 变成「已看」：如果之前不是已看，记录当前时间；本来就是已看则保留原时间。
 * - 从「已看」改回其他状态：清空 watchedAt。
 * - 其他状态之间互改：watchedAt 保持原样（一般是 null）。
 */
fun watchedAtFor(newStatus: WatchStatus, oldStatus: WatchStatus?, oldWatchedAt: Long?, now: Long): Long? {
    return if (newStatus == WatchStatus.WATCHED) {
        if (oldStatus == WatchStatus.WATCHED) oldWatchedAt ?: now else now
    } else {
        null
    }
}

/** 剧集追更进度与状态 */
data class EpisodeProgress(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val status: WatchStatus,
)

/**
 * 点击某一集时的追更结果。
 *
 * @param season 当前选中的季
 * @param episode 点击的集号
 * @param watched 点击后是否要标为看过（true=标为看过，false=取消）
 * @param totalEpisodes 这一季总集数（0 表示未知）
 * @param isLastSeason 这一季是否是最后一季
 */
fun episodeProgressFor(
    season: Int,
    episode: Int,
    watched: Boolean,
    totalEpisodes: Int,
    isLastSeason: Boolean,
    old: EpisodeProgress,
): EpisodeProgress {
    return if (watched) {
        // 标记看过：进度推进到这一集；如果是在最后一季看完了最后一集，整部剧算「已看」
        val finishedAll = totalEpisodes > 0 && episode >= totalEpisodes && isLastSeason
        EpisodeProgress(
            seasonNumber = season,
            episodeNumber = episode,
            status = if (finishedAll) WatchStatus.WATCHED else WatchStatus.WATCHING,
        )
    } else {
        // 取消标记：后退一集；已经退到 0 集就回到「在看」状态的开头
        EpisodeProgress(
            seasonNumber = season,
            episodeNumber = (episode - 1).coerceAtLeast(0),
            status = WatchStatus.WATCHING,
        )
    }
}