package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DrawHistoryDao {

    @Query("SELECT * FROM draw_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<DrawHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: DrawHistoryEntity): Long

    @Query("DELETE FROM draw_history")
    suspend fun clearAllHistory()
}
