package com.psp.shifthelper.ui.history

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
fun HistoryScreen(
    viewModel: HistoryViewModel = viewModel()
) {
    val historyItems by viewModel.historyItems.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(16.dp)
    ) {
        Text(
            text = "ASSIGNMENT HISTORY",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Foreground
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (historyItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "저장된 배정 이력이 없습니다.",
                    color = MutedForeground,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(historyItems) { item ->
                    HistoryCard(
                        item = item,
                        onDelete = { viewModel.deleteHistoryByGroup(item.date, item.shift, item.team) }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryCard(
    item: HistoryItem,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = item.date,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Foreground
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = item.shift,
                            fontSize = 13.sp,
                            color = AccentBlue,
                            fontWeight = FontWeight.Bold
                        )
                        if (item.team != null) {
                            Text(
                                text = "${item.team}조",
                                fontSize = 13.sp,
                                color = StatusOk,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "삭제",
                            tint = StatusError.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MutedForeground
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = Border, thickness = 0.5.dp)
                    
                    item.assignments.forEach { detail ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = detail.workerName,
                                fontSize = 14.sp,
                                color = Foreground,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = detail.equipmentCode,
                                fontSize = 14.sp,
                                color = MutedForeground,
                                modifier = Modifier.weight(1.5f)
                            )
                            val statusColor = if (detail.status == "Ok") StatusOk else StatusError
                            Text(
                                text = detail.status,
                                fontSize = 12.sp,
                                color = statusColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
