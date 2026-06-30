package com.psp.shifthelper.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.psp.shifthelper.data.model.Equipment
import com.psp.shifthelper.ui.components.GridBackground
import com.psp.shifthelper.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

enum class EditInteractionMode { POSITION, SIZE }

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateToOcr: () -> Unit = {}
) {
    val equipments by viewModel.equipments.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()
    val gridCols by viewModel.gridCols.collectAsState()
    val gridRows by viewModel.gridRows.collectAsState()
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))

    var scale by remember { mutableStateOf(0.45f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    // 편집 상태 관리
    var selectedEquipment by remember { mutableStateOf<Equipment?>(null) }
    var interactionMode by remember { mutableStateOf(EditInteractionMode.POSITION) }

    // 밀어내기 로직 처리
    val gridSize = 60f
    fun handleUpdateWithNudge(updated: Equipment) {
        val currentList = equipments.toMutableList()
        val index = currentList.indexOfFirst { it.id == updated.id }
        if (index == -1) return
        
        currentList[index] = updated
        
        // 충돌 체크 및 밀어내기
        equipments.filter { it.id != updated.id }.forEach { other ->
            val otherSnappedX = (other.posX / gridSize).roundToInt() * gridSize
            val otherSnappedY = (other.posY / gridSize).roundToInt() * gridSize
            val updatedSnappedX = (updated.posX / gridSize).roundToInt() * gridSize
            val updatedSnappedY = (updated.posY / gridSize).roundToInt() * gridSize
            
            val isOverlapping = updatedSnappedX < otherSnappedX + other.width &&
                                updatedSnappedX + updated.width > otherSnappedX &&
                                updatedSnappedY < otherSnappedY + other.height &&
                                updatedSnappedY + updated.height > otherSnappedY
            
            if (isOverlapping) {
                viewModel.updateEquipmentPosition(other.id, other.posX + gridSize, other.posY)
            }
        }
        selectedEquipment = updated
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 상단 헤더
            HeaderSection(
                isEditMode = isEditMode,
                today = today,
                gridCols = gridCols,
                gridRows = gridRows,
                selectedEquipment = selectedEquipment,
                scale = scale,
                onZoomChange = { scale = it.coerceIn(0.1f, 3f) },
                onToggleEditMode = { 
                    viewModel.toggleEditMode()
                    selectedEquipment = null 
                },
                onMapSizeUpdate = { c, r -> viewModel.setMapSize(c, r) },
                onAddEquipment = {
                    viewModel.upsertEquipment(Equipment(code = "NEW", name = "신규 장비", isRunning = false, posX = 0f, posY = 0f, width = 120f, height = 60f))
                },
                onSaveAll = { viewModel.saveAllChanges() },
                onSaveSelected = { 
                    selectedEquipment?.let { viewModel.upsertEquipment(it) }
                },
                onCancelEdit = { selectedEquipment = null },
                onExitEdit = {
                    viewModel.toggleEditMode()
                    selectedEquipment = null
                }
            )

            // 장비 배치도 영역
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .clipToBounds()
                    .then(
                        if (!isEditMode) {
                            Modifier.pointerInput(Unit) {
                                detectTransformGestures { centroid, pan, zoom, _ ->
                                    val oldScale = scale
                                    val newScale = (scale * zoom).coerceIn(0.1f, 3f)
                                    scale = newScale
                                    offset = centroid - (centroid - offset) * (newScale / oldScale) + pan
                                }
                            }
                        } else Modifier
                    )
            ) {
                // 확대/축소 캔버스
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
                    val corridorCol = gridCols / 2

                    GridBackground(cols = gridCols, rows = gridRows, gridSize = gridSize)

                    // 중앙 통로 (전체 맵 높이 가로지름)
                    Box(
                        modifier = Modifier
                            .height((gridRows * gridSize).dp)
                            .width(gridSize.dp)
                            .offset(x = (gridSize * corridorCol).dp)
                            .background(Color(0xFFFEF9C3).copy(alpha = 0.5f))
                    ) {
                        Text(
                            "CORRIDOR",
                            modifier = Modifier.align(Alignment.Center).graphicsLayer(rotationZ = -90f),
                            fontSize = 12.sp,
                            color = Color(0xFFCA8A04).copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }

                    // 장비들
                    equipments.forEach { equipment ->
                        val displayEquipment = if (isEditMode && selectedEquipment?.id == equipment.id) {
                            selectedEquipment!!
                        } else {
                            equipment
                        }

                        DraggableEquipment(
                            equipment = displayEquipment,
                            isEditMode = isEditMode,
                            interactionMode = interactionMode,
                            scale = scale,
                            gridCols = gridCols,
                            gridRows = gridRows,
                            corridorCol = corridorCol,
                            isSelected = selectedEquipment?.id == equipment.id,
                            onSelect = { selectedEquipment = it },
                            onDelete = { 
                                viewModel.deleteEquipment(it)
                                if (selectedEquipment?.id == it.id) selectedEquipment = null
                            },
                            onPositionChangedInEdit = { updated ->
                                handleUpdateWithNudge(updated)
                            }
                        )
                    }
                }
                
                if (selectedEquipment != null && !isEditMode) {
                    InfoPanel(equipment = selectedEquipment!!, onClose = { selectedEquipment = null })
                }
            }
        }

        // 하단 도크 (편집 전용)
        if (isEditMode) {
            Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) {
                Surface(
                    modifier = Modifier.height(64.dp).padding(horizontal = 8.dp).shadow(24.dp, RoundedCornerShape(32.dp)),
                    shape = RoundedCornerShape(32.dp),
                    color = Surface.copy(alpha = 0.95f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        DockModeItem(
                            icon = Icons.Default.OpenWith,
                            label = "위치 변경",
                            isSelected = interactionMode == EditInteractionMode.POSITION,
                            onClick = { interactionMode = EditInteractionMode.POSITION }
                        )
                        VerticalDivider(modifier = Modifier.height(24.dp), thickness = 1.dp, color = Border)
                        DockModeItem(
                            icon = Icons.Default.AspectRatio,
                            label = "크기 변경",
                            isSelected = interactionMode == EditInteractionMode.SIZE,
                            onClick = { interactionMode = EditInteractionMode.SIZE }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderSection(
    isEditMode: Boolean,
    today: String,
    gridCols: Int,
    gridRows: Int,
    selectedEquipment: Equipment?,
    scale: Float,
    onZoomChange: (Float) -> Unit,
    onToggleEditMode: () -> Unit,
    onMapSizeUpdate: (Int, Int) -> Unit,
    onAddEquipment: () -> Unit,
    onSaveAll: () -> Unit,
    onSaveSelected: () -> Unit,
    onCancelEdit: () -> Unit,
    onExitEdit: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isEditMode) "WORKSPACE / MAP EDITOR" else "OPERATIONS / DASHBOARD",
                fontSize = 10.sp,
                color = if (isEditMode) AccentBlue else MutedForeground,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold
            )
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 줌 컨트롤 버튼
                IconButton(onClick = { onZoomChange(scale - 0.05f) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Remove, null, tint = MutedForeground)
                }
                Text("${(scale * 100).roundToInt()}%", fontSize = 12.sp, color = MutedForeground, fontWeight = FontWeight.Bold)
                IconButton(onClick = { onZoomChange(scale + 0.05f) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, null, tint = MutedForeground)
                }
                Spacer(Modifier.width(8.dp))
                // 모드 전환 버튼
                Surface(
                    modifier = Modifier.height(32.dp).width(90.dp).clip(RoundedCornerShape(16.dp)),
                    color = SurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight().background(if (!isEditMode) AccentBlue else Color.Transparent).clickable { if (isEditMode) onToggleEditMode() },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Visibility, null, modifier = Modifier.size(14.dp), tint = if (!isEditMode) Color.White else MutedForeground) }
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight().background(if (isEditMode) AccentBlue else Color.Transparent).clickable { if (!isEditMode) onToggleEditMode() },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp), tint = if (isEditMode) Color.White else MutedForeground) }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (isEditMode && selectedEquipment != null) {
            Column {
                Text(text = "현재 선택된 장비: ${selectedEquipment.code}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = AccentBlue)
                Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onSaveSelected, colors = ButtonDefaults.buttonColors(containerColor = StatusOk), shape = RoundedCornerShape(6.dp), modifier = Modifier.height(32.dp)) {
                        Text("이 장비 변경사항 저장", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onCancelEdit, shape = RoundedCornerShape(6.dp), modifier = Modifier.height(32.dp)) {
                        Text("취소", fontSize = 11.sp, color = MutedForeground)
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(text = today, fontSize = 22.sp, fontWeight = FontWeight.Black, color = Foreground)
                    if (isEditMode) {
                        Row(modifier = Modifier.padding(top = 4.dp)) {
                            MapSizeButton(label = "Cols", value = gridCols) { onMapSizeUpdate(it.coerceAtLeast(5), gridRows) }
                            Spacer(Modifier.width(12.dp))
                            MapSizeButton(label = "Rows", value = gridRows) { onMapSizeUpdate(gridCols, it.coerceAtLeast(5)) }
                        }
                    }
                }
                
                if (isEditMode) {
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onAddEquipment, modifier = Modifier.background(SurfaceElevated, CircleShape).size(36.dp)) {
                                Icon(Icons.Default.Add, null, tint = AccentBlue)
                            }
                            Spacer(Modifier.width(12.dp))
                            Button(onClick = onSaveAll, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue), shape = RoundedCornerShape(18.dp), modifier = Modifier.height(36.dp)) {
                                Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("전체 저장", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        TextButton(onClick = onExitEdit, modifier = Modifier.height(32.dp)) {
                            Text("편집 모드 나가기", fontSize = 11.sp, color = StatusError, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoPanel(equipment: Equipment, onClose: () -> Unit) {
    Surface(
        modifier = Modifier.padding(bottom = 24.dp, start = 24.dp, end = 24.dp).fillMaxWidth().shadow(12.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp), color = Surface, border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).background(if (equipment.isRunning) StatusOk.copy(alpha = 0.2f) else Border, CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (equipment.isRunning) Icons.Default.PlayArrow else Icons.Default.Stop, null, tint = if (equipment.isRunning) StatusOk else MutedForeground)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(equipment.code, fontWeight = FontWeight.Bold, color = Foreground)
                Text(if (equipment.isRunning) "작업 진행 중" else "현재 비가동", fontSize = 12.sp, color = MutedForeground)
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, null, tint = MutedForeground) }
        }
    }
}

@Composable
fun DockModeItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (isSelected) AccentBlue.copy(alpha = 0.1f) else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (isSelected) AccentBlue else MutedForeground, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, color = if (isSelected) AccentBlue else MutedForeground, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MapSizeButton(label: String, value: Int, onUpdate: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 10.sp, color = MutedForeground, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        Row(modifier = Modifier.background(SurfaceElevated, RoundedCornerShape(4.dp)).border(1.dp, Border, RoundedCornerShape(4.dp)), verticalAlignment = Alignment.CenterVertically) {
            Text("-", modifier = Modifier.clickable { onUpdate(value - 1) }.padding(horizontal = 8.dp), color = Foreground)
            Text(value.toString(), fontSize = 12.sp, color = Foreground, fontWeight = FontWeight.Bold)
            Text("+", modifier = Modifier.clickable { onUpdate(value + 1) }.padding(horizontal = 8.dp), color = Foreground)
        }
    }
}

@Composable
fun DraggableEquipment(
    equipment: Equipment,
    isEditMode: Boolean,
    interactionMode: EditInteractionMode,
    scale: Float,
    gridCols: Int,
    gridRows: Int,
    corridorCol: Int,
    isSelected: Boolean,
    onSelect: (Equipment) -> Unit,
    onDelete: (Equipment) -> Unit = {},
    onPositionChangedInEdit: (Equipment) -> Unit
) {
    val density = LocalDensity.current
    val gridSize = 60f

    val snappedX = (equipment.posX / gridSize).roundToInt() * gridSize
    val snappedY = (equipment.posY / gridSize).roundToInt() * gridSize
    val snappedW = (equipment.width / gridSize).roundToInt() * gridSize
    val snappedH = (equipment.height / gridSize).roundToInt() * gridSize

    Box(
        modifier = Modifier
            .offset { IntOffset(
                (if (isEditMode) snappedX else equipment.posX).dp.roundToPx(), 
                (if (isEditMode) snappedY else equipment.posY).dp.roundToPx()
            ) }
            .size(
                (if (isEditMode) snappedW else equipment.width).dp, 
                (if (isEditMode) snappedH else equipment.height).dp
            )
            .then(
                if (isEditMode) {
                    Modifier
                        .pointerInput(equipment.id, interactionMode) {
                            val touchSlop = 8.dp.toPx()
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                var dragTriggered = false
                                var currentPos = down.position
                                
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    
                                    if (!dragTriggered) {
                                        val totalDistance = (change.position - down.position).getDistance()
                                        if (totalDistance > touchSlop) {
                                            dragTriggered = true
                                            onSelect(equipment)
                                            change.consume()
                                        }
                                    } else {
                                        val dragAmount = change.position - currentPos
                                        change.consume() // 부모 스크롤 차단
                                        
                                        val deltaX = dragAmount.x / scale / density.density
                                        val deltaY = dragAmount.y / scale / density.density
                                        
                                        if (interactionMode == EditInteractionMode.POSITION) {
                                            onPositionChangedInEdit(equipment.copy(posX = equipment.posX + deltaX, posY = equipment.posY + deltaY))
                                        } else {
                                            onPositionChangedInEdit(equipment.copy(width = (equipment.width + deltaX).coerceAtLeast(gridSize), height = (equipment.height + deltaY).coerceAtLeast(gridSize)))
                                        }
                                    }
                                    currentPos = change.position
                                }
                                
                                // 드래그 종료 시 스냅 로직 (드래그가 발생했을 때만 실행)
                                if (dragTriggered) {
                                    var finalX = snappedX
                                    var finalY = snappedY
                                    if (interactionMode == EditInteractionMode.POSITION) {
                                        val sCol = (finalX / gridSize).roundToInt()
                                        val eCol = ((finalX + snappedW) / gridSize).roundToInt() - 1
                                        if (sCol <= corridorCol && eCol >= corridorCol) {
                                            finalX = if (equipment.posX < corridorCol * gridSize) (corridorCol - (snappedW / gridSize).toInt()) * gridSize else (corridorCol + 1) * gridSize
                                        }
                                        finalX = finalX.coerceIn(0f, (gridCols * gridSize - snappedW))
                                        finalY = finalY.coerceIn(0f, (gridRows * gridSize - snappedH))
                                        onPositionChangedInEdit(equipment.copy(posX = finalX, posY = finalY))
                                    } else {
                                        onPositionChangedInEdit(equipment.copy(width = snappedW.toFloat(), height = snappedH.toFloat()))
                                    }
                                } else {
                                    // 탭으로 간주하여 선택 처리
                                    onSelect(equipment)
                                }
                            }
                        }
                } else Modifier.clickable { onSelect(equipment) }
            )
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(2.dp).border(
                width = if (isSelected) 2.dp else if (isEditMode) 1.dp else 0.dp,
                color = if (isSelected) StatusOk else if (isEditMode) Border else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            ).background(Surface, RoundedCornerShape(8.dp))
        ) {
            com.psp.shifthelper.ui.components.EquipmentIcon(
                code = equipment.code,
                name = equipment.name,
                running = equipment.isRunning,
                modifier = Modifier.fillMaxSize(),
                worker = if (equipment.isRunning) "작업중" else null
            )
        }
        
        if (isEditMode && isSelected) {
            IconButton(
                onClick = { onDelete(equipment) },
                modifier = Modifier.size(24.dp).align(Alignment.TopStart).offset(x = (-6).dp, y = (-6).dp).shadow(4.dp, CircleShape).background(StatusError, CircleShape)
            ) { Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
            
            // 모드 표시 아이콘 및 리사이즈 핸들 영역 (터치 타겟 24dp)
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.BottomEnd)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown().consume() // 핸들 터치 시 부모 전파 즉시 차단
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (interactionMode == EditInteractionMode.POSITION) Icons.Default.OpenWith else Icons.Default.AspectRatio,
                    null,
                    tint = AccentBlue.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
