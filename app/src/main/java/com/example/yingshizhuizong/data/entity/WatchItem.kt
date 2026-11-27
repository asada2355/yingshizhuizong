package com.example.yingshizhuizong.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 看单条目：一部影视作品 */
@Entity(tableName = "watch_items")
data class WatchItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val originalTitle: String = "",
    val type: WatchType = WatchType.MOVIE,
    val status: WatchStatus = WatchStatus.WANT,
    /** 影片分类（每部影片一个分类，便于按类型筛选） */
    @ColumnInfo(defaultValue = "'OTHER'")
    val category: WatchCategory = WatchCategory.OTHER,
    val posterUrl: String = "",
    /** 上映日期（当天 0 点的 epoch 毫秒） */
    val releaseDate: Long? = null,
    val rating: Float = 0f,
    val comment: String = "",
    /** 是否开启上映提醒 */
    val reminderEnabled: Boolean = false,
    /** 已对哪个上映日期发过提醒（避免重复） */
    val notifiedForDate: Long? = null,
    /** TMDB 的作品 id（用于拉取简介、演职员、预告片），0 表示尚未解析 */
    @ColumnInfo(defaultValue = "0")
    val tmdbId: Int = 0,
    /** 自动抓取到的预告片（YouTube 视频 id），空表示还没找到 */
    @ColumnInfo(defaultValue = "''")
    val trailerKey: String = "",
    /** 预告片名称，例如「官方预告片」 */
    @ColumnInfo(defaultValue = "''")
    val trailerName: String = "",
    /** 用户手动填写的预告片链接（YouTube 页面 / mp4 直链），优先于 trailerKey */
    @ColumnInfo(defaultValue = "''")
    val trailerUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
