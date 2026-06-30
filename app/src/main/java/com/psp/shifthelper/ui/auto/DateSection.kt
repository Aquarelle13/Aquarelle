package com.psp.shifthelper.ui.auto

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psp.shifthelper.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun DateSection(
    selectedDate: String,
    onDateSelected: (String) -> Unit,
    recognizedDates: List<String> = emptyList(),
    storedDates: List<String> = emptyList()
) {
    // 과거 7일부터 미래 4일까지의 날짜 생성
    val dates = remember {
        (-7..4).map {
            LocalDate.now().plusDays(it.toLong()).format(DateTimeFormatter.ofPattern("MM.dd"))
        }
    }

    // 날짜 스크롤 선택
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        dates.forEach { date ->
            val isSelected = selectedDate == date
            val isRecognized = recognizedDates.contains(date)
            val isStored = storedDates.contains(date)
            
            OutlinedButton(
                onClick = { onDateSelected(date) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = when {
                        isSelected -> AccentBlue.copy(alpha = 0.15f)
                        isRecognized -> StatusOk.copy(alpha = 0.1f)
                        isStored -> AccentBlue.copy(alpha = 0.05f)
                        else -> Surface
                    }
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.5.dp,
                    color = when {
                        isSelected -> AccentBlue
                        isRecognized -> StatusOk
                        isStored -> AccentBlue.copy(alpha = 0.5f)
                        else -> Border
                    }
                ),
                contentPadding = PaddingValues(
                    horizontal = 12.dp,
                    vertical = 8.dp
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = date,
                        color = if (isSelected) AccentBlue else if (isRecognized) StatusOk else if (isStored) Foreground else MutedForeground,
                        fontWeight = if (isSelected || isRecognized || isStored) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                    if (isRecognized) {
                        Text(
                            text = "인식됨",
                            fontSize = 8.sp,
                            color = StatusOk
                        )
                    } else if (isStored) {
                        Text(
                            text = "저장됨",
                            fontSize = 8.sp,
                            color = AccentBlue
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SectionLabel(number: String, title: String) {
    Text(
        text = "$number · $title",
        fontSize = 11.sp,
        color = MutedForeground,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold
    )
}
