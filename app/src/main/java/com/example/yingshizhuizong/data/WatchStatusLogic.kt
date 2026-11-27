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