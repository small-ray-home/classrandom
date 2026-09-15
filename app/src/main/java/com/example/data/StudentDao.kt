package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {

    @Query("SELECT * FROM students ORDER BY number ASC, id ASC")
    fun getAllStudents(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE enabled = 1 ORDER BY number ASC, id ASC")
    fun getEnabledStudents(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE enabled = 1 AND drawnInCurrentRound = 0 ORDER BY number ASC, id ASC")
    fun getUndrawnEnabledStudents(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students ORDER BY number ASC, id ASC")
    suspend fun getAllStudentsSnapshot(): List<StudentEntity>

    @Query("SELECT * FROM students WHERE enabled = 1 ORDER BY number ASC, id ASC")
    suspend fun getEnabledStudentsSnapshot(): List<StudentEntity>

    @Query("SELECT * FROM students WHERE enabled = 1 AND drawnInCurrentRound = 0 ORDER BY number ASC, id ASC")
    suspend fun getUndrawnEnabledStudentsSnapshot(): List<StudentEntity>

    @Query("SELECT * FROM students WHERE number = :number LIMIT 1")
    suspend fun getStudentByNumber(number: Int): StudentEntity?

    @Query("SELECT * FROM students WHERE id = :id LIMIT 1")
    suspend fun getStudentById(id: Long): StudentEntity?

    @Query("SELECT COUNT(*) FROM students")
    fun getTotalStudentCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM students WHERE enabled = 1")
    fun getEnabledStudentCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM students WHERE enabled = 1 AND drawnInCurrentRound = 1")
    fun getDrawnStudentCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(student: StudentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(students: List<StudentEntity>): List<Long>

    @Update
    suspend fun update(student: StudentEntity)

    @Query("UPDATE students SET enabled = :enabled WHERE id = :id")
    suspend fun updateEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE students SET drawnInCurrentRound = 1 WHERE id = :id")
    suspend fun markAsDrawn(id: Long)

    @Query("UPDATE students SET drawnInCurrentRound = 0")
    suspend fun resetCurrentRound()

    @Delete
    suspend fun delete(student: StudentEntity)

    @Query("DELETE FROM students WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM students")
    suspend fun deleteAllStudents()
}
