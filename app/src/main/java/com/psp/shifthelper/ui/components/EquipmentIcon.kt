package com.psp.shifthelper.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psp.shifthelper.ui.theme.*

@Composable
fun EquipmentIcon(
    code: String,
    name: String? = null,
    running: Boolean,
    modifier: Modifier = Modifier,
    worker: String? = null
) {
    // 가동 중일 때 테두리 점멸 효과
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val backgroundColor = if (running)
        StatusOk.copy(alpha = 0.2f)
    else
        Surface.copy(alpha = 0.9f)

    val borderColor = if (running) 
        StatusOk 
    else 
        Border

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp), // 이미지의 둥근 모서리 반영
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 장비 코드 (중앙 굵은 텍스트)
                Text(
                    text = code,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = if (running) Foreground else MutedForeground,
                    textAlign = TextAlign.Center,
                    lineHeight = 11.sp,
                    maxLines = 1
                )

                // 장비 이름 (코드가 짧을 때 보조 정보)
                if (name != null && name != code) {
                    Text(
                        text = name,
                        fontSize = 7.sp,
                        color = MutedForeground,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }

                // 작업자 (하단 작은 텍스트)
                if (worker != null && running) {
                    Text(
                        text = worker,
                        fontSize = 9.sp,
                        color = AccentBlue,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
