package org.example.project.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

import org.example.project.utils.DetectedTarget

@Composable
expect fun CameraPreview(
    modifier: Modifier = Modifier, 
    onUpdate: (List<DetectedTarget>, List<String>, String?, String?) -> Unit = { _, _, _, _ -> }
)
