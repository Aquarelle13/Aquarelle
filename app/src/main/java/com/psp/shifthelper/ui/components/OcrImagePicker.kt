package com.psp.shifthelper.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.psp.shifthelper.domain.OcrResult
import com.psp.shifthelper.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun OcrImagePicker(
    title: String,
    imageUri: Uri?,
    result: OcrResult,
    onPicked: (String) -> Unit,
    onCancel: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val density = LocalDensity.current

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Foreground)
            IconButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = null, tint = Foreground) }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            val constraints = this
            val screenWidthPx = with(density) { constraints.maxWidth.toPx() }
            val screenHeightPx = with(density) { constraints.maxHeight.toPx() }
            
            val imageAspect = result.imageWidth.toFloat() / result.imageHeight.toFloat()
            val screenAspect = screenWidthPx / screenHeightPx
            
            val (drawW, drawH) = if (imageAspect > screenAspect) {
                screenWidthPx to (screenWidthPx / imageAspect)
            } else {
                (screenHeightPx * imageAspect) to screenHeightPx
            }
            
            val startX = (screenWidthPx - drawW) / 2
            val startY = (screenHeightPx - drawH) / 2
            val scaleFactor = drawW / result.imageWidth.toFloat()

            Box(modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 8f)
                        offset = (offset + pan) * zoom
                    }
                }
            ) {
                Box(modifier = Modifier.fillMaxSize().graphicsLayer(
                    scaleX = scale, 
                    scaleY = scale, 
                    translationX = offset.x, 
                    translationY = offset.y,
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                )) {
                    AsyncImage(model = imageUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    
                    result.allDetectedLines.forEach { line ->
                        val box = line.boundingBox
                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        (startX + box.left * scaleFactor).roundToInt(),
                                        (startY + box.top * scaleFactor).roundToInt()
                                    )
                                }
                                .size(
                                    width = (box.width() * scaleFactor / density.density).dp,
                                    height = (box.height() * scaleFactor / density.density).dp
                                )
                                .border(1.dp, StatusOk.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                                .background(StatusOk.copy(alpha = 0.05f))
                                .pointerInput(line.text) {
                                    detectTapGestures { onPicked(line.text) }
                                }
                        )
                    }
                }
            }
        }
    }
}
