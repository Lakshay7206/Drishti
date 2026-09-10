package org.example.project.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Bundle
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale

class VoiceCommandManager(
    private val context: Context,
    private val onSosDetected: () -> Unit,
    private val onSosSent: () -> Unit,
    private val onFindingModeActivated: () -> Unit,
    private val onFindObject: (String?) -> Unit,
    private val emergencyNumber: String = "+919991519128" 
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    fun startListening() {
        mainHandler.post {
            if (isListening) return@post
            
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d("VOICE_CMD", ">>> Listening Active - Speak Now!")
                        isListening = true
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        isListening = false
                    }
                    override fun onError(error: Int) {
                        Log.e("VOICE_CMD", "Error: $error")
                        isListening = false
                        // Error 7 is timeout, error 9 is no match. Restart after delay.
                        mainHandler.postDelayed({ startListening() }, 500)
                    }
                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        processMatches(matches)
                        startListening()
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        processMatches(matches)
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            try {
                speechRecognizer?.startListening(recognizerIntent)
            } catch (e: Exception) {
                Log.e("VOICE_CMD", "Failed to start", e)
                isListening = false
            }
        }
    }

    private fun processMatches(matches: List<String>?) {
        matches?.forEach { text ->
            val lowerText = text.lowercase()
            if (lowerText.contains("sos")) {
                Log.d("VOICE_CMD", "SOS Detected!")
                onSosDetected()
                triggerEmergencyAction()
            } else if (lowerText.contains("finding mode")) {
                Log.d("VOICE_CMD", "Finding Mode Activated!")
                onFindingModeActivated()
            } else if (lowerText.contains("find") || lowerText.contains("where is") || lowerText.contains("search for")) {
                // regex to find the object name after "find" or "where is" or "search for"
                val regex = Regex("(find|where is|search for) (a|an|the|my)? ?(\\w+)")
                val match = regex.find(lowerText)
                val target = match?.groupValues?.get(3)
                if (target != null) {
                    Log.d("VOICE_CMD", "Finding target: $target")
                    onFindObject(target)
                }
            } else if (lowerText.contains("stop finding") || lowerText.contains("cancel search") || lowerText.contains("exit finding mode")) {
                onFindObject(null)
            }
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun triggerEmergencyAction() {
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    val lat = location?.latitude ?: 0.0
                    val lng = location?.longitude ?: 0.0
                    val mapsUrl = "https://www.google.com/maps?q=$lat,$lng"
                    
                    val message = "\ud83d\udea8 EMERGENCY SOS! I need help. My current location is: $mapsUrl"
                    
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://api.whatsapp.com/send?phone=$emergencyNumber&text=${Uri.encode(message)}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    onSosSent()
                }
        } catch (e: SecurityException) {
            Log.e("VOICE_CMD", "Location permission missing", e)
        }
    }
}
