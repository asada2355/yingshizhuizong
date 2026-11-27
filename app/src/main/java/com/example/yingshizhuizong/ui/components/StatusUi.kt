package com.example.yingshizhuizong.ui.components

import androidx.compose.ui.graphics.Color
import com.example.yingshizhuizong.data.entity.WatchStatus
import com.example.yingshizhuizong.data.entity.WatchType

fun WatchStatus.label(): String = when (this) {
    WatchStatus.WANT -> "想看"
    WatchStatus.WATCHING -> "在看"
    WatchStatus.WATCHED -> "已看"
    WatchStatus.DROPPED -> "弃看"
}

fun WatchStatus.color(): Color = when (this) {
    WatchStatus.WANT -> Color(0xFF6750A4)
    WatchStatus.WATCHING -> Color(0xFF0061A4)
    WatchStatus.WATCHED -> Color(0xFF2E7D32)
    WatchStatus.DROPPED -> Color(0xFF757575)
}

fun WatchType.label(): String = if (this == WatchType.MOVIE) "电影" else "剧集"
