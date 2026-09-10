package org.example.project.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import org.example.project.utils.DetectedTarget

@Composable
fun HomeScreen() {
    var detectedObjects by remember { mutableStateOf(emptyList<DetectedTarget>()) }
    var spokenMessages by remember { mutableStateOf(emptyList<String>()) }
    var findingTarget by remember { mutableStateOf<String?>(null) }
    var sosStatus by remember { mutableStateOf<String?>(null) }
    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            onUpdate = { allTargets, spoken, target, sos ->
                detectedObjects = allTargets
                findingTarget = target
                sosStatus = sos
                if (spoken.isNotEmpty()) {
                    spokenMessages = spoken
                }
            }
        )
        
        // Finding Mode Overlay
        findingTarget?.let { target ->
            Box(
                modifier = Modifier
                    .padding(top = 64.dp)
                    .background(Color.Blue.copy(alpha = 0.7f))
                    .padding(16.dp)
                    .align(Alignment.TopCenter)
            ) {
                Text(
                    text = "🔍 Looking for: $target",
                    color = Color.White,
                    style = TextStyle(fontSize = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                )
            }
        }

        // SOS Overlay
        sosStatus?.let { status ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (status == "SENDING") Color.Red.copy(alpha = 0.8f) else Color.Green.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (status == "SENDING") "SENDING SOS" else "SENT",
                    color = Color.White,
                    style = TextStyle(fontSize = 40.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold)
                )
            }
        }
        
        // HUD Canvas for drawing ML bounding boxes
        if (detectedObjects.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                
                detectedObjects.forEach { target ->
                    target.boundingBox?.let { bbox ->
                        val left = bbox[0] * canvasWidth
                        val top = bbox[1] * canvasHeight
                        val right = bbox[2] * canvasWidth
                        val bottom = bbox[3] * canvasHeight
                        
                        drawRect(
                            color = Color.Green,
                            topLeft = Offset(left, top),
                            size = Size(right - left, bottom - top),
                            style = Stroke(width = 6f)
                        )
                        
                        drawText(
                            textMeasurer = textMeasurer,
                            text = "${target.label} (${(target.confidence * 100).toInt()}%)",
                            topLeft = Offset(left, top - 60f),
                            style = TextStyle(color = Color.Green, fontSize = 20.sp, background = Color.Black.copy(alpha = 0.5f))
                        )
                    }
                }
            }
        }
        
        if (spokenMessages.isNotEmpty()) {
            LaunchedEffect(spokenMessages) {
                delay(4000)
                spokenMessages = emptyList()
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier.background(Color.Red.copy(alpha = 0.8f)).padding(16.dp)
                ) {
                    items(spokenMessages) { msg ->
                        Text(text = "📢 $msg", color = Color.Black, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
        
        if (detectedObjects.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                LazyColumn(
                    modifier = Modifier.background(Color.White.copy(alpha = 0.8f)).padding(8.dp)
                ) {
                    items(detectedObjects) { obj ->
                        val distStr = obj.distance.toString().take(4)
                        Text(text = "Detected: ${obj.label} - ${obj.position} (${distStr}m)", color = Color.Black, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
