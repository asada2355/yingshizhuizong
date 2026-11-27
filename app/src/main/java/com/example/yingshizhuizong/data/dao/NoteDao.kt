package com.example.yingshizhuizong.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.yingshizhuizong.data.entity.Note
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)

    @Query("SELECT * FROM notes WHERE watchItemId = :watchItemId ORDER BY createdAt DESC")
    fun observeForItem(watchItemId: Long): Flow<List<Note>>

    @Query("SELECT COUNT(*) FROM notes WHERE watchItemId = :watchItemId")
    fun observeCount(watchItemId: Long): Flow<Int>
}
