package com.psp.shifthelper.ui.manage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psp.shifthelper.ui.theme.*

data class PolicyUi(
    val title: String,
    val description: String,
    val enabled: Boolean
)

@Composable
fun PolicySection() {
    val policies = remember {
        mutableStateListOf(
            PolicyUi("연속 배정 제한", "직전 3일 동일 장비/동일 조원 배정 금지", true),
            PolicyUi("별도 관리 장비", "특수 장비 연속 제한 ON", true),
            PolicyUi("야간 연속 3일 제한", "야간 3일 연속이면 제외", false),
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionLabel("05", "FATIGUE POLICY")

            policies.forEachIndexed { index, policy ->
                PolicyRow(
                    policy = policy,
                    onToggle = {
                        policies[index] = policy.copy(enabled = !policy.enabled)
                    }
                )
                if (index < policies.lastIndex) {
                    HorizontalDivider(
                        color = Border.copy(alpha = 0.5f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

@Composable
fun PolicyRow(
    policy: PolicyUi,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = policy.title,
                fontSize = 13.sp,
                color = Foreground,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = policy.description,
                fontSize = 11.sp,
                color = MutedForeground
            )
        }
        Switch(
            checked = policy.enabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Foreground,
                checkedTrackColor = StatusOk,
                uncheckedThumbColor = MutedForeground,
                uncheckedTrackColor = Border
            )
        )
    }
}