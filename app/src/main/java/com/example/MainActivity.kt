package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.StudentEntity
import com.example.ui.AddEditStudentDialog
import com.example.ui.CsvImportDialog
import com.example.ui.DashboardScreen
import com.example.ui.DrawResultDialog
import com.example.ui.HistoryScreen
import com.example.ui.MainViewModel
import com.example.ui.SettingsScreen
import com.example.ui.StudentListScreen
import com.example.ui.theme.MyApplicationTheme

enum class AppTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD("控制台", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    STUDENTS("學生名單", Icons.Filled.Group, Icons.Outlined.Group),
    HISTORY("抽籤紀錄", Icons.Filled.History, Icons.Outlined.History),
    SETTINGS("設定", Icons.Filled.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val overlayPermissionState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainAppContent(
                    viewModel = viewModel,
                    hasOverlayPermission = overlayPermissionState.value,
                    onRefreshPermission = { checkOverlayPermission() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkOverlayPermission()
    }

    private fun checkOverlayPermission() {
        overlayPermissionState.value = Settings.canDrawOverlays(this)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    viewModel: MainViewModel,
    hasOverlayPermission: Boolean,
    onRefreshPermission: () -> Unit
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(AppTab.DASHBOARD) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Observers
    val allStudents by viewModel.allStudents.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val enabledCount by viewModel.enabledCount.collectAsState()
    val drawnCount by viewModel.drawnCount.collectAsState()
    val drawHistory by viewModel.drawHistory.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val scheduleStatus by viewModel.scheduleStatus.collectAsState()
    val inAppDrawResult by viewModel.inAppDrawResult.collectAsState()
    val isRolling by viewModel.isRollingAnimation.collectAsState()
    val animatedStudent by viewModel.animatedStudent.collectAsState()
    val csvImportState by viewModel.csvImportState.collectAsState()

    // Dialog & Lock States
    var isSettingsUnlocked by remember { mutableStateOf(false) }
    var showSettingsPasswordDialog by remember { mutableStateOf(false) }
    var showBreakPasswordDialog by remember { mutableStateOf(false) }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var studentToEdit by remember { mutableStateOf<StudentEntity?>(null) }

    fun requestGoToSettings() {
        if (isSettingsUnlocked) {
            currentTab = AppTab.SETTINGS
        } else {
            showSettingsPasswordDialog = true
        }
    }

    // Toast / Snackbar Listeners
    LaunchedEffect(Unit) {
        viewModel.messageEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Permission Launcher for POST_NOTIFICATIONS (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // CSV File Pickers
    val csvFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onCsvFileSelected(uri)
        }
    }

    val csvExportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportStudentsToCsv(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentTab) {
                            AppTab.DASHBOARD -> "懸浮抽籤"
                            AppTab.STUDENTS -> "學生名單管理"
                            AppTab.HISTORY -> "歷史抽籤紀錄"
                            AppTab.SETTINGS -> "系統與參數設定"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors()
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.values().forEach { tab ->
                    val selected = currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (tab == AppTab.SETTINGS) {
                                requestGoToSettings()
                            } else {
                                currentTab = tab
                            }
                        },
                        icon = {
                            Icon(
                                if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title
                            )
                        },
                        label = { Text(tab.title) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                AppTab.DASHBOARD -> {
                    DashboardScreen(
                        hasOverlayPermission = hasOverlayPermission,
                        settings = appSettings,
                        scheduleStatus = scheduleStatus,
                        totalCount = totalCount,
                        enabledCount = enabledCount,
                        drawnCount = drawnCount,
                        onToggleFloatingService = { enable ->
                            viewModel.toggleFloatingService(enable)
                        },
                        onTestDraw = {
                            viewModel.requestDrawAction(
                                onRequirePassword = {
                                    showBreakPasswordDialog = true
                                }
                            )
                        },
                        onResetRound = {
                            viewModel.resetCurrentRound()
                        },
                        onNavigateToStudents = { currentTab = AppTab.STUDENTS },
                        onNavigateToHistory = { currentTab = AppTab.HISTORY },
                        onNavigateToSettings = { requestGoToSettings() }
                    )
                }

                AppTab.STUDENTS -> {
                    StudentListScreen(
                        students = allStudents,
                        onAddStudentClick = {
                            studentToEdit = null
                            showAddEditDialog = true
                        },
                        onEditStudentClick = { student ->
                            studentToEdit = student
                            showAddEditDialog = true
                        },
                        onToggleEnabled = { student ->
                            viewModel.toggleStudentEnabled(student)
                        },
                        onDeleteStudent = { student ->
                            viewModel.deleteStudent(student)
                        },
                        onDeleteAllStudents = {
                            viewModel.deleteAllStudents()
                        },
                        onResetRound = {
                            viewModel.resetCurrentRound()
                        },
                        onImportCsvClick = {
                            csvFilePicker.launch(
                                arrayOf(
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "text/plain",
                                    "application/csv",
                                    "*/*"
                                )
                            )
                        },
                        onExportCsvClick = {
                            csvExportPicker.launch("學生名單.csv")
                        }
                    )
                }

                AppTab.HISTORY -> {
                    HistoryScreen(
                        historyList = drawHistory,
                        onClearHistory = {
                            viewModel.clearHistory()
                        }
                    )
                }

                AppTab.SETTINGS -> {
                    SettingsScreen(
                        hasOverlayPermission = hasOverlayPermission,
                        settings = appSettings,
                        onUpdateNonRepeating = { viewModel.updateNonRepeating(it) },
                        onUpdateAnimationEnabled = { viewModel.updateAnimationEnabled(it) },
                        onUpdateResultDuration = { viewModel.updateResultDuration(it) },
                        onUpdateShowEnglishName = { viewModel.updateShowEnglishName(it) },
                        onUpdateButtonSize = { viewModel.updateButtonSize(it) },
                        onUpdateButtonOpacity = { viewModel.updateButtonOpacity(it) },
                        onUpdateRememberPosition = { viewModel.updateRememberPosition(it) },
                        onResetButtonPosition = { viewModel.resetButtonPosition() },
                        onUpdateBootAutoStart = { viewModel.updateBootAutoStart(it) },
                        onUpdateBreakDvdEnabled = { viewModel.updateBreakDvdEnabled(it) }
                    )
                }
            }
        }
    }

    // Add / Edit Student Dialog
    if (showAddEditDialog) {
        AddEditStudentDialog(
            studentToEdit = studentToEdit,
            onDismiss = {
                showAddEditDialog = false
                studentToEdit = null
            },
            onSave = { id, number, name, englishName ->
                viewModel.saveStudent(id, number, name, englishName)
                showAddEditDialog = false
                studentToEdit = null
            }
        )
    }

    // CSV Import Dialog
    if (csvImportState.isOpen) {
        CsvImportDialog(
            state = csvImportState,
            onMappingChange = { mapping ->
                viewModel.updateCsvMapping(mapping)
            },
            onStrategyChange = { strategy ->
                viewModel.setCsvDuplicateStrategy(strategy)
            },
            onNextStep = { step ->
                viewModel.setCsvImportStep(step)
            },
            onConfirmImport = {
                viewModel.executeCsvImport()
            },
            onDismiss = {
                viewModel.closeCsvImport()
            }
        )
    }

    // Settings Password Dialog
    if (showSettingsPasswordDialog) {
        SettingsPasswordDialog(
            onDismiss = {
                showSettingsPasswordDialog = false
            },
            onSuccess = {
                isSettingsUnlocked = true
                showSettingsPasswordDialog = false
                currentTab = AppTab.SETTINGS
            },
            onVerify = { input ->
                viewModel.verifyPassword(input)
            }
        )
    }

    // Break Draw Password Dialog
    if (showBreakPasswordDialog) {
        BreakDrawPasswordDialog(
            onDismiss = {
                showBreakPasswordDialog = false
            },
            onSuccess = {
                showBreakPasswordDialog = false
                viewModel.triggerDrawAction()
            },
            onVerify = { input ->
                viewModel.verifyPassword(input)
            }
        )
    }

    // In-App Test Draw Dialog
    DrawResultDialog(
        result = inAppDrawResult,
        animatedStudent = animatedStudent,
        isRolling = isRolling,
        settings = appSettings,
        onDismiss = { viewModel.dismissInAppDraw() },
        onResetRound = {
            viewModel.resetCurrentRound()
            viewModel.triggerDrawAction()
        },
        onGoToSettings = {
            viewModel.dismissInAppDraw()
            currentTab = AppTab.STUDENTS
        }
    )
}

@Composable
fun SettingsPasswordDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    onVerify: (String) -> Boolean
) {
    var password by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔒", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("輸入設定密碼", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    text = "進入系統與參數設定需輸入密碼：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        isError = false
                    },
                    label = { Text("密碼") },
                    isError = isError,
                    supportingText = if (isError) {
                        { Text("密碼錯誤，請重新輸入密碼", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (onVerify(password)) {
                        onSuccess()
                    } else {
                        isError = true
                    }
                }
            ) {
                Text("解鎖進入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun BreakDrawPasswordDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    onVerify: (String) -> Boolean
) {
    var password by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⏸️", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("下課鎖定抽籤", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    text = "目前為下課休息時間，抽籤已被鎖定。\n請輸入密碼才能抽籤：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        isError = false
                    },
                    label = { Text("解鎖密碼") },
                    isError = isError,
                    supportingText = if (isError) {
                        { Text("密碼錯誤，請重新輸入", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (onVerify(password)) {
                        onSuccess()
                    } else {
                        isError = true
                    }
                }
            ) {
                Text("解鎖並抽籤")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
