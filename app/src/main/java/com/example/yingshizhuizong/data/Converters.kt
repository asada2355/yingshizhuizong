package com.example.yingshizhuizong.data

import androidx.room.TypeConverter
import com.example.yingshizhuizong.data.entity.WatchCategory
import com.example.yingshizhuizong.data.entity.WatchStatus
import com.example.yingshizhuizong.data.entity.WatchType

/** Room 枚举类型转换器 */
class Converters {
    @TypeConverter
    fun watchTypeToString(type: WatchType): String = type.name

    @TypeConverter
    fun stringToWatchType(value: String): WatchType = WatchType.valueOf(value)

    @TypeConverter
    fun watchStatusToString(status: WatchStatus): String = status.name

    @TypeConverter
    fun stringToWatchStatus(value: String): WatchStatus = WatchStatus.valueOf(value)

    @TypeConverter
    fun watchCategoryToString(category: WatchCategory): String = category.name

    @TypeConverter
    fun stringToWatchCategory(value: String): WatchCategory = WatchCategory.fromName(value)
}
