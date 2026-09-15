package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.AppSettings
import com.example.data.DrawResult
import com.example.data.StudentEntity

@Composable
fun DrawResultDialog(
    result: DrawResult?,
    animatedStudent: StudentEntity?,
    isRolling: Boolean,
    settings: AppSettings,
    onDismiss: () -> Unit,
    onResetRound: () -> Unit,
    onGoToSettings: () -> Unit
) {
    if (result == null) return

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xF00F172A), // Deep Slate 94%
                            Color(0xF51E1B4B)  // Deep Indigo 96%
                        )
                    )
                )
                .border(
                    1.5.dp,
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF818CF8).copy(alpha = 0.8f),
                            Color(0xFF38BDF8).copy(alpha = 0.4f)
                        )
                    ),
                    RoundedCornerShape(28.dp)
                )
                .shadow(24.dp, RoundedCornerShape(28.dp))
                .clickable { onDismiss() }
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            when (result) {
                is DrawResult.Success -> {
                    val displayStudent = if (isRolling) animatedStudent ?: result.student else result.student

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text("🎲", fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "抽籤結果",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE2E8F0)
                            )

                            if (settings.nonRepeating) {
                                Spacer(modifier = Modifier.width(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF312E81).copy(alpha = 0.7f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        Color(0xFF818CF8).copy(alpha = 0.5f)
                                    )
                                ) {
                                    Text(
                                        text = "本輪 ${result.drawnCount}/${result.totalEnabledCount}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFA5B4FC),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Seat Number Circle
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E1B4B).copy(alpha = 0.9f))
                                .border(2.dp, Color(0xFF818CF8), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = displayStudent.number.toString(),
                                style = MaterialTheme.typography.displayMedium.copy(fontSize = 38.sp),
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Chinese Name
                        Text(
                            text = displayStudent.name,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF8FAFC),
                            textAlign = TextAlign.Center
                        )

                        // English Name (if enabled and not empty)
                        if (settings.showEnglishName && displayStudent.englishName.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = displayStudent.englishName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF94A3B8),
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "（點擊任意處關閉）",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                is DrawResult.RoundCompleted -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🎉", fontSize = 42.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "本輪已全部抽完",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF8FAFC)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${result.totalEnabledCount} / ${result.totalEnabledCount}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFA5B4FC)
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = onResetRound,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4F46E5)
                            ),
                            modifier = Modifier.fillMaxWidth(0.7f)
                        ) {
                            Text("開始下一輪", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                DrawResult.NoStudents -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("👥", fontSize = 40.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "目前沒有可抽籤的學生",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF8FAFC)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "請先新增學生名單或匯入 CSV 檔案",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = onGoToSettings,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4F46E5)
                            )
                        ) {
                            Text("前往學生名單")
                        }
                    }
                }

                DrawResult.NoEnabledStudents -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("⚠️", fontSize = 40.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "目前沒有啟用中的學生",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF8FAFC)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "所有學生皆處於停用狀態，請前往名單啟用學生",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = onGoToSettings,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4F46E5)
                            )
                        ) {
                            Text("前往學生名單")
                        }
                    }
                }
            }
        }
    }
}
