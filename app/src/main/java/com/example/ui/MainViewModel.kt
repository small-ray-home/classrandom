package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.csv.ColumnMapping
import com.example.csv.CsvExporter
import com.example.csv.CsvHeaderAnalysis
import com.example.csv.CsvImporter
import com.example.csv.CsvParser
import com.example.csv.CsvValidationResult
import com.example.csv.DuplicateStrategy
import com.example.data.AppSettings
import com.example.data.ClassScheduleManager
import com.example.data.DrawHistoryEntity
import com.example.data.DrawResult
import com.example.data.ScheduleStatus
import com.example.data.StudentEntity
import com.example.data.StudentRepository
import com.example.service.FloatingDrawService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CsvImportState(
    val isOpen: Boolean = false,
    val step: Int = 1, // 1: Mapping, 2: Preview & Validation, 3: Importing / Complete
    val headerAnalysis: CsvHeaderAnalysis? = null,
    val mapping: ColumnMapping = ColumnMapping(),
    val validationResult: CsvValidationResult? = null,
    val duplicateStrategy: DuplicateStrategy = DuplicateStrategy.OVERWRITE,
    val isImporting: Boolean = false,
    val importedCount: Int = 0,
    val errorMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StudentRepository.getInstance(application)

    val allStudents: StateFlow<List<StudentEntity>> = repository.allStudents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCount: StateFlow<Int> = repository.totalCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val enabledCount: StateFlow<Int> = repository.enabledCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val drawnCount: StateFlow<Int> = repository.drawnCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val drawHistory: StateFlow<List<DrawHistoryEntity>> = repository.drawHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appSettings: StateFlow<AppSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    // In-App Test Draw State
    private val _inAppDrawResult = MutableStateFlow<DrawResult?>(null)
    val inAppDrawResult: StateFlow<DrawResult?> = _inAppDrawResult.asStateFlow()

    private val _isRollingAnimation = MutableStateFlow(false)
    val isRollingAnimation: StateFlow<Boolean> = _isRollingAnimation.asStateFlow()

    private val _animatedStudent = MutableStateFlow<StudentEntity?>(null)
    val animatedStudent: StateFlow<StudentEntity?> = _animatedStudent.asStateFlow()

    // Schedule & Class/Break Status
    private val _scheduleStatus = MutableStateFlow<ScheduleStatus>(ClassScheduleManager.getCurrentStatus())
    val scheduleStatus: StateFlow<ScheduleStatus> = _scheduleStatus.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                _scheduleStatus.value = ClassScheduleManager.getCurrentStatus()
                val didReset = ClassScheduleManager.checkAndAutoReset(getApplication(), repository)
                if (didReset) {
                    _messageEvents.emit("上課時間已到，已自動重設本輪抽籤名單")
                }
                delay(3000)
            }
        }
    }

    // CSV Import State
    private val _csvImportState = MutableStateFlow(CsvImportState())
    val csvImportState: StateFlow<CsvImportState> = _csvImportState.asStateFlow()

    // Event Messages
    private val _messageEvents = MutableSharedFlow<String>()
    val messageEvents: SharedFlow<String> = _messageEvents.asSharedFlow()

    private var inAppAnimationJob: Job? = null

    fun isBreakTime(): Boolean {
        return ClassScheduleManager.isBreakTime()
    }

    fun verifyPassword(password: String): Boolean {
        return ClassScheduleManager.verifyPassword(password)
    }

    fun requestDrawAction(onRequirePassword: () -> Unit) {
        if (isBreakTime()) {
            onRequirePassword()
        } else {
            triggerDrawAction()
        }
    }

    fun submitBreakPasswordAndDraw(password: String): Boolean {
        if (verifyPassword(password)) {
            triggerDrawAction()
            return true
        }
        return false
    }

    fun checkOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(getApplication())
    }

    fun toggleFloatingService(enable: Boolean) {
        val context = getApplication<Application>()
        if (enable) {
            if (!checkOverlayPermission()) {
                viewModelScope.launch {
                    _messageEvents.emit("尚未取得懸浮視窗權限，請先授權")
                }
                return
            }
            FloatingDrawService.start(context)
        } else {
            FloatingDrawService.stop(context)
        }
    }

    fun triggerDrawAction() {
        viewModelScope.launch {
            inAppAnimationJob?.cancel()
            val result = withContext(Dispatchers.IO) {
                repository.executeDraw()
            }
            val settings = repository.settingsManager.getSettings()

            if (result is DrawResult.Success && settings.animationEnabled && result.candidates.isNotEmpty()) {
                _inAppDrawResult.value = result
                _isRollingAnimation.value = true

                inAppAnimationJob = launch {
                    val pool = result.candidates
                    for (step in 0 until 12) {
                        _animatedStudent.value = pool.random()
                        val delayMs = 60L + (step * step * 1.5f).toLong()
                        delay(delayMs)
                    }
                    _animatedStudent.value = result.student
                    _isRollingAnimation.value = false
                }
            } else {
                _inAppDrawResult.value = result
                _isRollingAnimation.value = false
                if (result is DrawResult.Success) {
                    _animatedStudent.value = result.student
                }
            }
        }
    }

    fun dismissInAppDraw() {
        inAppAnimationJob?.cancel()
        _inAppDrawResult.value = null
        _isRollingAnimation.value = false
        _animatedStudent.value = null
    }

    fun toggleStudentEnabled(student: StudentEntity) {
        viewModelScope.launch {
            repository.setStudentEnabled(student.id, !student.enabled)
        }
    }

    fun saveStudent(id: Long, number: Int, name: String, englishName: String) {
        viewModelScope.launch {
            if (id == 0L) {
                repository.insertStudent(
                    StudentEntity(
                        number = number,
                        name = name.trim(),
                        englishName = englishName.trim(),
                        enabled = true,
                        drawnInCurrentRound = false
                    )
                )
                _messageEvents.emit("已新增學生：$name ($number 號)")
            } else {
                repository.updateStudent(
                    StudentEntity(
                        id = id,
                        number = number,
                        name = name.trim(),
                        englishName = englishName.trim(),
                        enabled = true,
                        drawnInCurrentRound = false
                    )
                )
                _messageEvents.emit("已更新學生：$name ($number 號)")
            }
        }
    }

    fun deleteStudent(student: StudentEntity) {
        viewModelScope.launch {
            repository.deleteStudent(student)
            _messageEvents.emit("已刪除學生：${student.name}")
        }
    }

    fun deleteAllStudents() {
        viewModelScope.launch {
            repository.deleteAllStudents()
            _messageEvents.emit("已清空所有學生資料")
        }
    }

    fun resetCurrentRound() {
        viewModelScope.launch {
            repository.resetCurrentRound()
            _messageEvents.emit("本輪抽籤狀態已重置")
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _messageEvents.emit("已清空抽籤紀錄")
        }
    }

    // CSV Operations
    fun onCsvFileSelected(uri: Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _messageEvents.emit("無法開啟選擇的檔案")
                    return@launch
                }

                val parsedRows = withContext(Dispatchers.IO) {
                    inputStream.use { CsvParser.readCsv(it) }
                }

                if (parsedRows.isEmpty()) {
                    _messageEvents.emit("CSV 檔案為空或無有效內容")
                    return@launch
                }

                val analysis = CsvImporter.analyzeCsv(parsedRows)
                if (analysis == null) {
                    _messageEvents.emit("無法解析 CSV 標題列")
                    return@launch
                }

                // Initial validation using suggested mapping
                val initialValidation = CsvImporter.validateRows(
                    analysis.rawRows,
                    analysis.suggestedMapping
                )

                _csvImportState.value = CsvImportState(
                    isOpen = true,
                    step = 1,
                    headerAnalysis = analysis,
                    mapping = analysis.suggestedMapping,
                    validationResult = initialValidation,
                    duplicateStrategy = DuplicateStrategy.OVERWRITE
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _messageEvents.emit("讀取 CSV 失敗: ${e.localizedMessage ?: "格式錯誤"}")
            }
        }
    }

    fun updateCsvMapping(mapping: ColumnMapping) {
        val currentState = _csvImportState.value
        val analysis = currentState.headerAnalysis ?: return
        val validation = CsvImporter.validateRows(analysis.rawRows, mapping)
        _csvImportState.value = currentState.copy(
            mapping = mapping,
            validationResult = validation
        )
    }

    fun setCsvDuplicateStrategy(strategy: DuplicateStrategy) {
        _csvImportState.value = _csvImportState.value.copy(duplicateStrategy = strategy)
    }

    fun setCsvImportStep(step: Int) {
        _csvImportState.value = _csvImportState.value.copy(step = step)
    }

    fun closeCsvImport() {
        _csvImportState.value = CsvImportState()
    }

    fun executeCsvImport() {
        val currentState = _csvImportState.value
        val validStudents = currentState.validationResult?.validStudents ?: return
        if (validStudents.isEmpty()) {
            viewModelScope.launch {
                _messageEvents.emit("沒有有效資料可供匯入")
            }
            return
        }

        viewModelScope.launch {
            _csvImportState.value = currentState.copy(isImporting = true)
            val importedCount = withContext(Dispatchers.IO) {
                CsvImporter.executeImport(
                    repository,
                    validStudents,
                    currentState.duplicateStrategy
                )
            }
            _csvImportState.value = CsvImportState()
            _messageEvents.emit("成功匯入 $importedCount 筆學生資料")
        }
    }

    fun exportStudentsToCsv(uri: Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val students = withContext(Dispatchers.IO) {
                    repository.allStudents
                }
                val studentList = allStudents.value
                if (studentList.isEmpty()) {
                    _messageEvents.emit("目前沒有學生資料可匯出")
                    return@launch
                }

                val outputStream = context.contentResolver.openOutputStream(uri)
                if (outputStream == null) {
                    _messageEvents.emit("無法寫入目標位置")
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    outputStream.use { stream ->
                        CsvExporter.exportToCsv(studentList, stream)
                    }
                }
                _messageEvents.emit("成功匯出 ${studentList.size} 筆學生資料")
            } catch (e: Exception) {
                e.printStackTrace()
                _messageEvents.emit("匯出失敗: ${e.localizedMessage ?: "未知錯誤"}")
            }
        }
    }

    // Setting modifiers
    fun updateNonRepeating(enabled: Boolean) = viewModelScope.launch {
        repository.settingsManager.setNonRepeating(enabled)
    }

    fun updateAnimationEnabled(enabled: Boolean) = viewModelScope.launch {
        repository.settingsManager.setAnimationEnabled(enabled)
    }

    fun updateResultDuration(seconds: Int) = viewModelScope.launch {
        repository.settingsManager.setResultDurationSeconds(seconds)
    }

    fun updateShowEnglishName(show: Boolean) = viewModelScope.launch {
        repository.settingsManager.setShowEnglishName(show)
    }

    fun updateButtonSize(sizeDp: Int) = viewModelScope.launch {
        repository.settingsManager.setButtonSizeDp(sizeDp)
    }

    fun updateButtonOpacity(opacity: Float) = viewModelScope.launch {
        repository.settingsManager.setButtonOpacity(opacity)
    }

    fun updateRememberPosition(remember: Boolean) = viewModelScope.launch {
        repository.settingsManager.setRememberPosition(remember)
    }

    fun resetButtonPosition() = viewModelScope.launch {
        repository.settingsManager.savePosition(0.85f, 0.5f)
        _messageEvents.emit("已重設懸浮按鈕位置至預設右側中央")
    }

    fun updateBootAutoStart(autoStart: Boolean) = viewModelScope.launch {
        repository.settingsManager.setBootAutoStart(autoStart)
    }

    fun updateBreakDvdEnabled(enabled: Boolean) = viewModelScope.launch {
        repository.settingsManager.setBreakDvdEnabled(enabled)
    }
}
