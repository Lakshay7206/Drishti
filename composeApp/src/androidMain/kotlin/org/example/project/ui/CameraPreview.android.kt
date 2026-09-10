package org.example.project.ui

import android.Manifest
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.provider.ContactsContract
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import com.google.mlkit.common.model.LocalModel
import org.example.project.utils.DecisionEngine
import org.example.project.utils.DetectedTarget
import org.example.project.utils.VoiceCommandManager
import java.util.concurrent.Executors
import java.util.Locale

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
actual fun CameraPreview(modifier: Modifier, onUpdate: (List<DetectedTarget>, List<String>, String?, String?) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermissions by remember { mutableStateOf(false) }
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    val decisionEngine = remember { DecisionEngine() }
    var sosStatus by remember { mutableStateOf<String?>(null) }

    val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_CONTACTS
    )

    var myEmergencyNumber by remember { mutableStateOf("+919991519128") } 

    val contactPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri ->
        uri?.let {
            val cursor = context.contentResolver.query(it, null, null, null, null)
            if (cursor?.moveToFirst() == true) {
                val id = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                val hasPhone = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER))
                
                if (hasPhone == "1") {
                    val phones = context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = " + id,
                        null,
                        null
                    )
                    if (phones?.moveToFirst() == true) {
                        val number = phones.getString(phones.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                        // Clean number (remove spaces etc)
                        myEmergencyNumber = number.replace(Regex("[^0-9+]"), "")
                        Log.d("CONTACT_PICKER", "Selected: $myEmergencyNumber")
                    }
                    phones?.close()
                }
            }
            cursor?.close()
        }
    }

    val sosManager = remember(context) {
        VoiceCommandManager(
            context = context,
            onSosDetected = { 
                sosStatus = "SENDING"
                tts?.speak("sending sos", TextToSpeech.QUEUE_FLUSH, null, null)
            },
            onSosSent = {
                sosStatus = "SENT"
                tts?.speak("sent", TextToSpeech.QUEUE_FLUSH, null, null)
            },
            onFindingModeActivated = {
                tts?.speak("Finding mode activated. What would you like to find?", TextToSpeech.QUEUE_FLUSH, null, null)
            },
            onFindObject = { target ->
                decisionEngine.currentFindingTarget = target
                if (target != null) {
                    tts?.speak("Searching for $target. Please rotate your camera slowly.", TextToSpeech.QUEUE_FLUSH, null, null)
                }
                Log.d("FIND_MODE", "Looking for: $target")
            },
            emergencyNumber = myEmergencyNumber
        )
    }

    DisposableEffect(context) {
        val textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }
        tts = textToSpeech
        
        onDispose {
            textToSpeech.stop()
            textToSpeech.shutdown()
            sosManager.stopListening()
        }
    }

    LaunchedEffect(hasPermissions) {
        if (hasPermissions) {
            sosManager.startListening()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermissions = result.values.all { it }
    }

    LaunchedEffect(Unit) {
        if (requiredPermissions.all { 
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED 
        }) {
            hasPermissions = true
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    if (hasPermissions) {
        val objectDetector = remember { 
            val localModel = LocalModel.Builder()
                .setAssetFilePath("object_detection.tflite")
                .build()
            val customOptions = CustomObjectDetectorOptions.Builder(localModel)
                .setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .setClassificationConfidenceThreshold(0.3f)
                .setMaxPerObjectLabelCount(3)
                .build()
            ObjectDetection.getClient(customOptions)
        }

        val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

        Box(modifier = modifier) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                
                                val rot = imageProxy.imageInfo.rotationDegrees
                                val frameWidth = if (rot == 90 || rot == 270) image.height else image.width
                                val frameHeight = if (rot == 90 || rot == 270) image.width else image.height
                                val imageWidth = frameWidth.toFloat()
                                val imageHeight = frameHeight.toFloat()

                                objectDetector.process(image)
                                    .addOnSuccessListener { detectedObjects ->
                                        val targets = detectedObjects.map { obj ->
                                            val labelObj = obj.labels.firstOrNull()
                                            val text = labelObj?.text ?: "Unknown Object"
                                            val confidence = labelObj?.confidence ?: 0.5f
                                            
                                            val bbox = obj.boundingBox
                                            val area = (bbox.width() * bbox.height()).toDouble()
                                            val dist = if (area > 0) (800.0 / Math.sqrt(area)).toFloat() else 5.0f
                                            
                                            val centerX = bbox.exactCenterX()
                                            val position = when {
                                                centerX < imageWidth / 3 -> "Left"
                                                centerX > 2 * imageWidth / 3 -> "Right"
                                                else -> "Front"
                                            }

                                            val normLeft = bbox.left.toFloat() / imageWidth
                                            val normTop = bbox.top.toFloat() / imageHeight
                                            val normRight = bbox.right.toFloat() / imageWidth
                                            val normBottom = bbox.bottom.toFloat() / imageHeight

                                            DetectedTarget(
                                                label = text,
                                                distance = dist,
                                                position = position,
                                                confidence = confidence,
                                                boundingBox = listOf(normLeft, normTop, normRight, normBottom)
                                            )
                                        }

                                        val spokenMessages = decisionEngine.evaluateTargets(targets)
                                        if (spokenMessages.isNotEmpty()) {
                                            spokenMessages.forEach { msg ->
                                                val shouldFlush = msg.startsWith("$$$")
                                                val finalMsg = if (shouldFlush) msg.substring(3) else msg
                                                val queueMode = if (shouldFlush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                                                tts?.speak(finalMsg, queueMode, null, null)
                                            }
                                        }
                                        onUpdate(targets, spokenMessages, decisionEngine.currentFindingTarget, sosStatus)
                                        // Reset SENT status after a delay
                                        if (sosStatus == "SENT") {
                                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                                sosStatus = null
                                            }, 5000)
                                        }
                                    }
                                    .addOnCompleteListener { 
                                        imageProxy.close() 
                                    }
                            } else {
                                imageProxy.close()
                            }
                        }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner, 
                                CameraSelector.DEFAULT_BACK_CAMERA, 
                                preview, 
                                imageAnalysis
                            )
                        } catch (exc: Exception) {
                            Log.e("CameraPreview", "Use case binding failed", exc)
                        }

                    }, ContextCompat.getMainExecutor(ctx))
                    
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Settings Button Overlay
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                shape = MaterialTheme.shapes.medium,
                color = Color.Black.copy(alpha = 0.5f),
                tonalElevation = 4.dp
            ) {
                IconButton(onClick = { contactPicker.launch(null) }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Set SOS Contact",
                        tint = Color.White
                    )
                }
            }
            
            // Current Contact Display
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                shape = MaterialTheme.shapes.small,
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Text(
                    text = "SOS to: $myEmergencyNumber",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Requesting camera permission...")
        }
    }
}
