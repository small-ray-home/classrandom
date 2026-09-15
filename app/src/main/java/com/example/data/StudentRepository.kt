package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.security.SecureRandom

sealed interface DrawResult {
    data class Success(
        val student: StudentEntity,
        val drawnCount: Int,
        val totalEnabledCount: Int,
        val candidates: List<StudentEntity>
    ) : DrawResult

    data class RoundCompleted(
        val totalEnabledCount: Int
    ) : DrawResult

    object NoStudents : DrawResult
    object NoEnabledStudents : DrawResult
}

class StudentRepository(
    private val studentDao: StudentDao,
    private val historyDao: DrawHistoryDao,
    val settingsManager: AppSettingsManager
) {
    private val random = SecureRandom()

    val allStudents: Flow<List<StudentEntity>> = studentDao.getAllStudents()
    val totalCount: Flow<Int> = studentDao.getTotalStudentCount()
    val enabledCount: Flow<Int> = studentDao.getEnabledStudentCount()
    val drawnCount: Flow<Int> = studentDao.getDrawnStudentCount()
    val drawHistory: Flow<List<DrawHistoryEntity>> = historyDao.getAllHistory()
    val settingsFlow: Flow<AppSettings> = settingsManager.settingsFlow

    suspend fun insertStudent(student: StudentEntity): Long = studentDao.insert(student)

    suspend fun insertStudents(students: List<StudentEntity>): List<Long> = studentDao.insertAll(students)

    suspend fun updateStudent(student: StudentEntity) = studentDao.update(student)

    suspend fun setStudentEnabled(id: Long, enabled: Boolean) = studentDao.updateEnabled(id, enabled)

    suspend fun deleteStudent(student: StudentEntity) = studentDao.delete(student)

    suspend fun deleteStudentById(id: Long) = studentDao.deleteById(id)

    suspend fun deleteAllStudents() = studentDao.deleteAllStudents()

    suspend fun resetCurrentRound() = studentDao.resetCurrentRound()

    suspend fun clearHistory() = historyDao.clearAllHistory()

    suspend fun getStudentByNumber(number: Int): StudentEntity? = studentDao.getStudentByNumber(number)

    suspend fun executeDraw(): DrawResult {
        val all = studentDao.getAllStudentsSnapshot()
        if (all.isEmpty()) {
            return DrawResult.NoStudents
        }

        val enabled = studentDao.getEnabledStudentsSnapshot()
        if (enabled.isEmpty()) {
            return DrawResult.NoEnabledStudents
        }

        val settings = settingsManager.getSettings()

        if (settings.nonRepeating) {
            val undrawn = studentDao.getUndrawnEnabledStudentsSnapshot()
            if (undrawn.isEmpty()) {
                return DrawResult.RoundCompleted(totalEnabledCount = enabled.size)
            }

            val randomIndex = random.nextInt(undrawn.size)
            val selected = undrawn[randomIndex]

            // Mark as drawn in DB
            studentDao.markAsDrawn(selected.id)

            // Calculate progress (how many are drawn now)
            val currentDrawnCount = enabled.size - undrawn.size + 1

            // Save history
            historyDao.insert(
                DrawHistoryEntity(
                    number = selected.number,
                    name = selected.name,
                    englishName = selected.englishName
                )
            )

            return DrawResult.Success(
                student = selected,
                drawnCount = currentDrawnCount,
                totalEnabledCount = enabled.size,
                candidates = enabled
            )
        } else {
            val randomIndex = random.nextInt(enabled.size)
            val selected = enabled[randomIndex]

            // Save history
            historyDao.insert(
                DrawHistoryEntity(
                    number = selected.number,
                    name = selected.name,
                    englishName = selected.englishName
                )
            )

            return DrawResult.Success(
                student = selected,
                drawnCount = 1,
                totalEnabledCount = enabled.size,
                candidates = enabled
            )
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: StudentRepository? = null

        fun getInstance(context: Context): StudentRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getInstance(context)
                val settings = AppSettingsManager(context)
                val instance = StudentRepository(db.studentDao(), db.drawHistoryDao(), settings)
                INSTANCE = instance
                instance
            }
        }
    }
}
