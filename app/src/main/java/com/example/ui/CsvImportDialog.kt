package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.csv.ColumnMapping
import com.example.csv.DuplicateStrategy

@Composable
fun CsvImportDialog(
    state: CsvImportState,
    onMappingChange: (ColumnMapping) -> Unit,
    onStrategyChange: (DuplicateStrategy) -> Unit,
    onNextStep: (Int) -> Unit,
    onConfirmImport: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!state.isOpen || state.headerAnalysis == null) return

    val headers = state.headerAnalysis.headers

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.UploadFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (state.step == 1) "CSV 欄位對應" else "資料預覽與確認",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.78f)
            ) {
                if (state.isImporting) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("正在匯入學生資料...", style = MaterialTheme.typography.bodyLarge)
                    }
                } else if (state.step == 1) {
                    // STEP 1: Column Mapping
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "已讀取 CSV 首列標題。請確認各資料項目對應的 CSV 欄位：",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Seat Number Mapping
                        ColumnMappingSelector(
                            label = "座號 (必填數字)",
                            headers = headers,
                            selectedIndex = state.mapping.numberColIndex,
                            onSelect = { index ->
                                onMappingChange(state.mapping.copy(numberColIndex = index))
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Chinese Name Mapping
                        ColumnMappingSelector(
                            label = "中文姓名 (必填)",
                            headers = headers,
                            selectedIndex = state.mapping.nameColIndex,
                            onSelect = { index ->
                                onMappingChange(state.mapping.copy(nameColIndex = index))
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // English Name Mapping
                        ColumnMappingSelector(
                            label = "英文姓名 (選填)",
                            headers = headers,
                            selectedIndex = state.mapping.englishNameColIndex,
                            allowUnused = true,
                            onSelect = { index ->
                                onMappingChange(state.mapping.copy(englishNameColIndex = index))
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "💡 系統已自動偵測推薦對應欄位，您也可以點擊下拉選單手動調整。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    // STEP 2: Preview & Validation
                    val validation = state.validationResult
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        item {
                            // Validation summary badges
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                "有效資料",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                "${validation?.validStudents?.size ?: 0} 筆",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if ((validation?.errors?.size ?: 0) > 0) {
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Error,
                                            contentDescription = null,
                                            tint = if ((validation?.errors?.size ?: 0) > 0) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                "異常資料",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                "${validation?.errors?.size ?: 0} 筆",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if ((validation?.errors?.size ?: 0) > 0) {
                                                    MaterialTheme.colorScheme.error
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Duplicate Strategy Selector
                            Text(
                                text = "遇到重複座號時：",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            DuplicateStrategy.values().forEach { strategy ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onStrategyChange(strategy) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = state.duplicateStrategy == strategy,
                                        onClick = { onStrategyChange(strategy) }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = strategy.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = strategy.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Errors Section if any
                        if (validation != null && validation.errors.isNotEmpty()) {
                            item {
                                Text(
                                    text = "異常列清單 (匯入時將自動略過此類錯誤列)：",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            items(validation.errors) { err ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = "第 ${err.rowNumber} 列：${err.reason}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        if (err.rawContent.isNotBlank()) {
                                            Text(
                                                text = "原文：${err.rawContent}",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                            item {
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }

                        // Valid Preview Table
                        item {
                            Text(
                                text = "有效資料預覽 (共 ${validation?.validStudents?.size ?: 0} 筆)：",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (validation != null && validation.validStudents.isNotEmpty()) {
                            val previewItems = validation.validStudents.take(20)
                            items(previewItems) { item ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "#${item.number}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.width(44.dp)
                                        )
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (item.englishName.isNotBlank()) {
                                            Text(
                                                text = item.englishName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            if (validation.validStudents.size > 20) {
                                item {
                                    Text(
                                        text = "... 以及其餘 ${validation.validStudents.size - 20} 筆資料",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            }
                        } else {
                            item {
                                Text(
                                    text = "無有效資料",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (state.step == 1) {
                Button(
                    onClick = { onNextStep(2) },
                    enabled = state.mapping.numberColIndex >= 0 && state.mapping.nameColIndex >= 0
                ) {
                    Text("下一步：預覽資料")
                }
            } else {
                Button(
                    onClick = onConfirmImport,
                    enabled = !state.isImporting && (state.validationResult?.validStudents?.isNotEmpty() == true)
                ) {
                    Text("確認匯入 (${state.validationResult?.validStudents?.size ?: 0} 筆)")
                }
            }
        },
        dismissButton = {
            if (state.step == 2) {
                OutlinedButton(onClick = { onNextStep(1) }, enabled = !state.isImporting) {
                    Text("返回修改欄位")
                }
            } else {
                TextButton(onClick = onDismiss, enabled = !state.isImporting) {
                    Text("取消")
                }
            }
        }
    )
}

@Composable
private fun ColumnMappingSelector(
    label: String,
    headers: List<String>,
    selectedIndex: Int,
    allowUnused: Boolean = false,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    RoundedCornerShape(12.dp)
                )
                .background(MaterialTheme.colorScheme.surface)
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayText = when {
                    selectedIndex in headers.indices -> "第 ${selectedIndex + 1} 欄：${headers[selectedIndex]}"
                    selectedIndex == -1 -> if (allowUnused) "（不使用此欄位）" else "請選擇對應欄位..."
                    else -> "請選擇對應欄位..."
                }

                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selectedIndex >= 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                if (allowUnused) {
                    DropdownMenuItem(
                        text = { Text("不使用此欄位") },
                        onClick = {
                            onSelect(-1)
                            expanded = false
                        }
                    )
                }

                headers.forEachIndexed { index, headerName ->
                    DropdownMenuItem(
                        text = { Text("第 ${index + 1} 欄：$headerName") },
                        onClick = {
                            onSelect(index)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
