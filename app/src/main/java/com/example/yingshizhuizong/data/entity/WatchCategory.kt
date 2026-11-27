package com.example.yingshizhuizong.data.entity

/**
 * 影片分类：每部影片只属于一个分类，用来看单里按类型快速筛选。
 *
 * [tmdbGenreIds] 是 TMDB 的类型 id（电影与剧集两种 id 都收在这里），
 * 用于在线搜索 / 详情页自动识别分类。
 */
enum class WatchCategory(
    val label: String,
    val tmdbGenreIds: Set<Int> = emptySet(),
) {
    ACTION("动作", setOf(28, 10759)),          // 10759：剧集「动作冒险」
    COMEDY("喜剧", setOf(35)),
    ROMANCE("爱情", setOf(10749)),
    SCI_FI("科幻", setOf(878, 10765)),          // 10765：剧集「科幻奇幻」
    MYSTERY("悬疑", setOf(9648)),
    THRILLER("惊悚", setOf(53)),
    HORROR("恐怖", setOf(27)),
    CRIME("犯罪", setOf(80)),
    DRAMA("剧情", setOf(18, 10766, 10770)),     // 10766：肥皂剧；10770：电视电影
    ANIMATION("动画", setOf(16)),
    FANTASY("奇幻", setOf(14)),
    ADVENTURE("冒险", setOf(12)),
    WAR("战争", setOf(10752, 10768)),           // 10768：剧集「战争政治」
    HISTORY("历史", setOf(36)),
    FAMILY("家庭", setOf(10751, 10762)),        // 10762：儿童
    MUSIC("音乐", setOf(10402)),
    DOCUMENTARY("纪录", setOf(99, 10763)),      // 10763：新闻
    WESTERN("西部", setOf(37)),
    OTHER("其他"),
    ;

    companion object {
        /**
         * 一部影片在 TMDB 往往有多个类型，按「辨识度」从高到低取一个主分类：
         * 例如「动作 + 剧情」归到动作，而不是泛泛的剧情。
         */
        private val detectionOrder = listOf(
            ANIMATION, DOCUMENTARY, HORROR, MUSIC, WAR, WESTERN,
            CRIME, MYSTERY, SCI_FI, FANTASY, ROMANCE, THRILLER,
            ACTION, ADVENTURE, COMEDY, FAMILY, HISTORY, DRAMA,
        )

        /** 按 TMDB 类型 id 推断分类；没有匹配时返回「其他」 */
        fun fromTmdbIds(ids: Collection<Int>): WatchCategory =
            detectionOrder.firstOrNull { category ->
                category.tmdbGenreIds.any { it in ids }
            } ?: OTHER

        /** 从数据库/旧版本里的枚举名恢复；未知值退回「其他」，避免升级时崩溃 */
        fun fromName(value: String?): WatchCategory =
            entries.firstOrNull { it.name == value } ?: OTHER
    }
}
