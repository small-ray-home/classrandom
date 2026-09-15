package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "draw_history")
data class DrawHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val number: Int,
    val name: String,
    val englishName: String = ""
)
