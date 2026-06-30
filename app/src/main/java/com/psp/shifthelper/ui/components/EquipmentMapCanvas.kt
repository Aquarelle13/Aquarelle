package com.psp.shifthelper.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.psp.shifthelper.data.model.Equipment
import com.psp.shifthelper.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun EquipmentMapCanvas(
    equipments: List<Equipment>,
    modifier: Modifier = Modifier,
    isEditMode: Boolean = false,
    assignments: Map<Long, String> = emptyMap(), // equipmentId -> workerName
    selectedEquipmentId: Long? = null,
    onEquipmentClick: (Equipment) -> Unit = {},
    onPositionChanged: (Long, Float, Float, Float, Float) -> Unit = { _, _, _, _, _ -> }
) {
    var scale by remember { mutableStateOf(0.45f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .clipToBounds()
            .then(
                if (!isEditMode) {
                    Modifier.pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val oldScale = scale
                            val newScale = (scale * zoom).coerceIn(0.2f, 3f)
                            scale = newScale
                            offset = centroid - (centroid - offset) * (newScale / oldScale) + pan
                        }
                    }
                } else Modifier
            )
            .background(if (isEditMode) Color.Black.copy(alpha = 0.1f) else Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                )
        ) {
            equipments.forEach { equipment ->
                val workerName = assignments[equipment.id]
                val isSelected = selectedEquipmentId == equipment.id

                DraggableItem(
                    equipment = equipment,
                    isEditMode = isEditMode,
                    isSelected = isSelected,
                    workerName = workerName,
                    scale = scale,
                    onPositionChanged = onPositionChanged,
                    onClick = { onEquipmentClick(equipment) }
                )
            }
        }
    }
}

@Composable
private fun DraggableItem(
    equipment: Equipment,
    isEditMode: Boolean,
    isSelected: Boolean,
    workerName: String?,
    scale: Float,
    onPositionChanged: (Long, Float, Float, Float, Float) -> Unit,
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    var currentX by remember(equipment.id) { mutableStateOf(equipment.posX) }
    var currentY by remember(equipment.id) { mutableStateOf(equipment.posY) }
    var currentW by remember(equipment.id) { mutableStateOf(equipment.width) }
    var currentH by remember(equipment.id) { mutableStateOf(equipment.height) }

    Box(
        modifier = Modifier
            .offset { IntOffset(currentX.dp.roundToPx(), currentY.dp.roundToPx()) }
            .size(currentW.dp, currentH.dp)
            .then(
                if (isEditMode) {
                    Modifier.pointerInput(equipment.id) {
                        detectDragGestures(
                            onDragEnd = {
                                val snappedX = (currentX / 10f).roundToInt() * 10f
                                val snappedY = (currentY / 10f).roundToInt() * 10f
                                currentX = snappedX
                                currentY = snappedY
                                onPositionChanged(equipment.id, snappedX, snappedY, currentW, currentH)
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            currentX += dragAmount.x / scale / density.density
                            currentY += dragAmount.y / scale / density.density
                        }
                    }
                } else Modifier.clickable { onClick() }
            )
            .alpha(if (workerName != null && !isEditMode) 0.6f else 1.0f) // 배정 완료 시 투명도
            .border(
                width = if (isEditMode || isSelected) 2.dp else 0.dp,
                color = if (isSelected) AccentBlue else if (isEditMode) MutedForeground else Color.Transparent,
                shape = RoundedCornerShape(25.dp)
            )
    ) {
        EquipmentIcon(
            code = equipment.code,
            name = equipment.name,
            running = equipment.isRunning,
            worker = workerName,
            modifier = Modifier.fillMaxSize()
        )
    }
}
