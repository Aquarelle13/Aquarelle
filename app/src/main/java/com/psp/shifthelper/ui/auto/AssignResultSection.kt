package com.psp.shifthelper.ui.auto

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.psp.shifthelper.domain.AssignOutput
import com.psp.shifthelper.ui.theme.*

@Composable
fun AssignResultSection(
    date: String,
    shift: String,
    group: String,
    onManualEdit: () -> Unit,
    viewModel: AutoAssignViewModel = viewModel()
) {
    val assignState by viewModel.assignState.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionLabel("03", "배정 결과 · $group 조 $shift ($date)")

            when (val state = assignState) {
                is AssignUiState.Idle -> {
                    Text(
                        text = "배정 대기 중...",
                        fontSize = 13.sp,
                        color = MutedForeground
                    )
                }
                is AssignUiState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = AccentBlue
                        )
                        Text(
                            text = "배정 계산 중...",
                            fontSize = 13.sp,
                            color = MutedForeground
                        )
                    }
                }
                is AssignUiState.Success -> {
                    AssignSuccessContent(
                        output = state.output,
                        onConfirm = { viewModel.confirmAssignments(state.output.assignments) },
                        onManualEdit = onManualEdit
                    )
                }
                is AssignUiState.Error -> {
                    Text(
                        text = "오류: ${state.message}",
                        fontSize = 12.sp,
                        color = StatusError
                    )
                }
            }
        }
    }
}

@Composable
fun AssignSuccessContent(
    output: AssignOutput,
    onConfirm: () -> Unit,
    onManualEdit: () -> Unit
) {
    // ... 경고 메시지 ... (내용 유지)
    if (output.warnings.isNotEmpty()) {
        output.warnings.forEach { warning ->
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = StatusWarn.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "⚠ $warning",
                    modifier = Modifier.padding(8.dp),
                    fontSize = 11.sp,
                    color = StatusWarn
                )
            }
        }
    }

    HorizontalDivider(color = Border, thickness = 0.5.dp)

    // 배정 목록
    output.results.forEach { result ->
        AssignmentRow(result = result)
        HorizontalDivider(
            color = Border.copy(alpha = 0.5f),
            thickness = 0.5.dp
        )
    }

    // 하단 버튼
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onManualEdit,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border)
        ) {
            Text(
                text = "수동 수정",
                color = MutedForeground,
                fontSize = 12.sp
            )
        }
        Button(
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentBlue
            )
        ) {
            Text(
                text = "배정 확정",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AssignmentRow(result: com.psp.shifthelper.domain.AssignResult) {
    val assignment = result.assignment
    val statusColor = when (assignment.status) {
        "Ok" -> StatusOk
        "Warn" -> StatusWarn
        else -> StatusError
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = result.workerName,
            modifier = Modifier.weight(2f),
            fontSize = 13.sp,
            color = Foreground,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = result.equipmentCode,
            modifier = Modifier.weight(3f),
            fontSize = 12.sp,
            color = MutedForeground
        )
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = statusColor.copy(alpha = 0.15f)
        ) {
            Text(
                text = assignment.status,
                modifier = Modifier.padding(
                    horizontal = 8.dp,
                    vertical = 3.dp
                ),
                fontSize = 11.sp,
                color = statusColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}