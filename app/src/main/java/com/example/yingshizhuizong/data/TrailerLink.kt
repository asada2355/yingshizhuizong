package com.example.yingshizhuizong.data

/** 预告片链接工具：识别 YouTube 链接 / 直接视频地址，并生成播放器需要的 URL */
object TrailerLink {

    private val YOUTUBE_PATTERNS = listOf(
        Regex("""youtube\.com/watch\?(?:[^#\s]*?&)?v=([A-Za-z0-9_-]{11})"""),
        Regex("""youtu\.be/([A-Za-z0-9_-]{11})"""),
        Regex("""youtube\.com/embed/([A-Za-z0-9_-]{11})"""),
        Regex("""youtube\.com/shorts/([A-Za-z0-9_-]{11})"""),
        Regex("""youtube\.com/live/([A-Za-z0-9_-]{11})"""),
    )

    private val PLAIN_ID = Regex("""[A-Za-z0-9_-]{11}""")

    /** 从各种 YouTube 链接（或裸视频 id）里取出 11 位视频 id，取不到返回 null */
    fun youtubeId(url: String): String? {
        val text = url.trim()
        if (text.isEmpty()) return null
        YOUTUBE_PATTERNS.forEach { pattern ->
            pattern.find(text)?.let { return it.groupValues[1] }
        }
        return if (PLAIN_ID.matches(text)) text else null
    }

    fun isYouTube(url: String): Boolean = youtubeId(url) != null

    /** 是否是可以直接交给 <video> 播放的地址（mp4 / m3u8 / webm 等） */
    fun isDirectVideo(url: String): Boolean {
        val lower = url.trim().lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        val path = lower.substringBefore("?").substringBefore("#")
        return listOf(".mp4", ".m3u8", ".webm", ".mov", ".mkv", ".mpd").any { path.endsWith(it) }
    }

    /** YouTube 内嵌播放地址（自动播放 + 移动端内联播放） */
    fun embedUrl(videoId: String): String =
        "https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&rel=0&modestbranding=1"

    fun watchUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"

    /** YouTube 官方缩略图（不需要额外后端服务） */
    fun thumbnail(videoId: String): String = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
}
