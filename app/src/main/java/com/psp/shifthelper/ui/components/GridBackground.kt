package com.psp.shifthelper.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.psp.shifthelper.ui.theme.GridLine

@Composable
fun GridBackground(
    modifier: Modifier = Modifier,
    cols: Int = 14,
    rows: Int = 25,
    gridSize: Float = 60f,
    lineColor: Color = GridLine
) {
    // 캔버스 크기를 명확히 지정하여 확대 시에도 잘리지 않도록 함
    Canvas(modifier = modifier.size((cols * gridSize).dp, (rows * gridSize).dp)) {
        val width = size.width
        val height = size.height
        val gSize = gridSize * density

        // 가로선
        for (i in 0..rows) {
            val y = i * gSize
            drawLine(
                color = lineColor.copy(alpha = 0.5f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.5f // 선 두께 강화
            )
        }

        // 세로선
        for (i in 0..cols) {
            val x = i * gSize
            drawLine(
                color = lineColor.copy(alpha = 0.5f),
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1.5f // 선 두께 강화
            )
        }
    }
}
