package com.example.yingshizhuizong.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 直接交给 YouTube App 播放（vnd.youtube:视频id）。
 * 设备上没装 YouTube 时回退到浏览器打开 watch 链接。
 */
fun openYouTube(context: Context, videoId: String) {
    val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(appIntent)
        return
    } catch (e: Exception) {
        // 没装 YouTube App，退回浏览器
    }
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        // 连浏览器都没有，静默忽略
    }
}
