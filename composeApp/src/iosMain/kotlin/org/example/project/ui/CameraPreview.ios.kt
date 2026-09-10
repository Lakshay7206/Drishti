package org.example.project.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import org.example.project.utils.DetectedTarget

@Composable
actual fun CameraPreview(modifier: Modifier, onUpdate: (List<DetectedTarget>, List<String>, String?, String?) -> Unit) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text("Camera not supported on iOS yet")
    }
}
