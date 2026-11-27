package com.example.yingshizhuizong.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** 格式化完整日期：yyyy年M月d日 */
fun formatDate(epochMillis: Long?): String {
    if (epochMillis == null) return ""
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return date.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
}

/** 格式化短日期：M月d日 */
fun formatShortDate(epochMillis: Long?): String {
    if (epochMillis == null) return ""
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return date.format(DateTimeFormatter.ofPattern("M月d日"))
}

/** 距离今天还有多少天（可为负数） */
fun daysUntil(epochMillis: Long?): Long? {
    if (epochMillis == null) return null
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return ChronoUnit.DAYS.between(LocalDate.now(), date)
}

/** 取年份（用于 TMDB 按年份过滤搜索） */
fun yearOf(epochMillis: Long?): Int? =
    epochMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).year }
