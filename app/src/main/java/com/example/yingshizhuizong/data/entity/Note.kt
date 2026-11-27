package com.example.yingshizhuizong.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 观影笔记 */
@Entity(
    tableName = "notes",
    foreignKeys = [ForeignKey(
        entity = WatchItem::class,
        parentColumns = ["id"],
        childColumns = ["watchItemId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("watchItemId")]
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val watchItemId: Long,
    val content: String,
    /** 集数 / 进度 */
    val episode: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
