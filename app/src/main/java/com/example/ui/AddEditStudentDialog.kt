package com.example.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.StudentEntity

@Composable
fun AddEditStudentDialog(
    studentToEdit: StudentEntity?,
    onDismiss: () -> Unit,
    onSave: (id: Long, number: Int, name: String, englishName: String) -> Unit
) {
    var numberText by remember(studentToEdit) {
        mutableStateOf(studentToEdit?.number?.toString() ?: "")
    }
    var nameText by remember(studentToEdit) {
        mutableStateOf(studentToEdit?.name ?: "")
    }
    var englishNameText by remember(studentToEdit) {
        mutableStateOf(studentToEdit?.englishName ?: "")
    }

    var numberError by remember { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = if (studentToEdit == null) "新增學生" else "編輯學生",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Number Field
                OutlinedTextField(
                    value = numberText,
                    onValueChange = {
                        numberText = it
                        if (it.isNotBlank()) numberError = null
                    },
                    label = { Text("座號 (數字)") },
                    placeholder = { Text("例如：17") },
                    leadingIcon = {
                        Icon(Icons.Default.Badge, contentDescription = "座號")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = numberError != null,
                    supportingText = {
                        numberError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Chinese Name Field
                OutlinedTextField(
                    value = nameText,
                    onValueChange = {
                        nameText = it
                        if (it.isNotBlank()) nameError = null
                    },
                    label = { Text("姓名 (必填)") },
                    placeholder = { Text("例如：王小明") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = "姓名")
                    },
                    isError = nameError != null,
                    supportingText = {
                        nameError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // English Name Field (Optional)
                OutlinedTextField(
                    value = englishNameText,
                    onValueChange = { englishNameText = it },
                    label = { Text("英文姓名 (選填)") },
                    placeholder = { Text("例如：WANG MING") },
                    leadingIcon = {
                        Icon(Icons.Default.Language, contentDescription = "英文姓名")
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val num = numberText.trim().toIntOrNull()
                    val name = nameText.trim()

                    var hasError = false
                    if (num == null || num <= 0) {
                        numberError = "請輸入有效的正整數座號"
                        hasError = true
                    }
                    if (name.isEmpty()) {
                        nameError = "姓名不能為空"
                        hasError = true
                    }

                    if (!hasError && num != null) {
                        onSave(
                            studentToEdit?.id ?: 0L,
                            num,
                            name,
                            englishNameText.trim()
                        )
                        onDismiss()
                    }
                }
            ) {
                Text("儲存", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
