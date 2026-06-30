package com.psp.shifthelper.ui.manage

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
import com.psp.shifthelper.ui.theme.*

@Composable
fun GroupSection(
    viewModel: ManageViewModel = viewModel()
) {
    val selectedGroup by viewModel.selectedGroup.collectAsState()
    val workers by viewModel.workers.collectAsState()
    val groups = listOf("A", "B", "C", "D")
    
    val memberCount = workers.count { it.group == selectedGroup }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionLabel("01", "GROUP")

            // 조 선택 버튼
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groups.forEach { group ->
                    val isSelected = selectedGroup == group
                    OutlinedButton(
                        onClick = { viewModel.setSelectedGroup(group) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected)
                                AccentBlue.copy(alpha = 0.15f)
                            else
                                Surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.5.dp,
                            color = if (isSelected) AccentBlue else Border
                        ),
                        contentPadding = PaddingValues(
                            horizontal = 20.dp,
                            vertical = 8.dp
                        )
                    ) {
                        Text(
                            text = "${group}조",
                            color = if (isSelected) AccentBlue else MutedForeground,
                            fontWeight = if (isSelected)
                                FontWeight.Bold
                            else
                                FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // 선택된 조 정보
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${selectedGroup}조 · ${memberCount}명",
                    fontSize = 13.sp,
                    color = Foreground,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
