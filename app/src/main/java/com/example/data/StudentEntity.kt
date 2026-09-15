package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "students",
    indices = [Index(value = ["number"], unique = false)]
)
data class StudentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val number: Int,
    val name: String,
    val englishName: String = "",
    val enabled: Boolean = true,
    val drawnInCurrentRound: Boolean = false
)
