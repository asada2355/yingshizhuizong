package com.example.yingshizhuizong.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.yingshizhuizong.data.entity.WatchCategory
import com.example.yingshizhuizong.data.entity.WatchItem
import com.example.yingshizhuizong.data.entity.WatchType
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchItemDao {

    @Insert
    suspend fun insert(item: WatchItem): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<WatchItem>): List<Long>

    @Update
    suspend fun update(item: WatchItem)

    @Delete
    suspend fun delete(item: WatchItem)

    @Query("SELECT * FROM watch_items ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<WatchItem>>

    @Query("SELECT COUNT(*) FROM watch_items")
    suspend fun count(): Int

    @Query("SELECT * FROM watch_items WHERE posterUrl = '' OR posterUrl IS NULL")
    suspend fun getWithoutPoster(): List<WatchItem>

    /** 还没分类的条目（迁移后的老数据默认是 OTHER） */
    @Query("SELECT * FROM watch_items WHERE category = 'OTHER'")
    suspend fun getUncategorized(): List<WatchItem>

    @Query("SELECT * FROM watch_items WHERE id = :id")
    fun observeById(id: Long): Flow<WatchItem?>

    @Query("SELECT * FROM watch_items WHERE id = :id")
    suspend fun getById(id: Long): WatchItem?

    /**
     * 全库搜索：片名 / 原名 / 备注。
     * 用 instr 做纯子串匹配（中文直接命中，英文忽略大小写，也不会被 % 之类的字符干扰）。
     */
    @Query(
        """
        SELECT * FROM watch_items
        WHERE instr(lower(replace(title, ' ', '')), lower(replace(:query, ' ', ''))) > 0
           OR instr(lower(replace(originalTitle, ' ', '')), lower(replace(:query, ' ', ''))) > 0
           OR instr(lower(replace(comment, ' ', '')), lower(replace(:query, ' ', ''))) > 0
        ORDER BY updatedAt DESC
        """
    )
    fun observeSearch(query: String): Flow<List<WatchItem>>

    /** 在线搜索添加前判重：同名同类型的条目是否已存在 */
    @Query("SELECT COUNT(*) FROM watch_items WHERE title = :title AND type = :type")
    suspend fun countByTitle(title: String, type: WatchType): Int

    /** 小组件：即将上映（限制条数，已看的自动移出） */
    @Query("SELECT * FROM watch_items WHERE status != 'WATCHED' AND releaseDate IS NOT NULL AND releaseDate >= :todayStart ORDER BY releaseDate ASC LIMIT :limit")
    suspend fun getUpcoming(todayStart: Long, limit: Int): List<WatchItem>

    /** 提醒：在 [todayStart, horizon] 窗口内、开启提醒且未提醒过的条目 */
    @Query("SELECT * FROM watch_items WHERE reminderEnabled = 1 AND releaseDate IS NOT NULL AND releaseDate >= :todayStart AND releaseDate <= :horizon AND (notifiedForDate IS NULL OR notifiedForDate != releaseDate)")
    suspend fun getReminderCandidates(todayStart: Long, horizon: Long): List<WatchItem>

    @Query("UPDATE watch_items SET notifiedForDate = :date WHERE id = :id")
    suspend fun markReminded(id: Long, date: Long)

    @Query("UPDATE watch_items SET posterUrl = :url WHERE id = :id")
    suspend fun updatePoster(id: Long, url: String)

    @Query("UPDATE watch_items SET trailerKey = :key, trailerName = :name WHERE id = :id")
    suspend fun updateTrailer(id: Long, key: String, name: String)

    @Query("UPDATE watch_items SET tmdbId = :tmdbId WHERE id = :id")
    suspend fun updateTmdbId(id: Long, tmdbId: Int)

    /** 只更新分类，不改变 updatedAt（避免自动补全分类后看单顺序跳动） */
    @Query("UPDATE watch_items SET category = :category WHERE id = :id")
    suspend fun updateCategory(id: Long, category: WatchCategory)
}
