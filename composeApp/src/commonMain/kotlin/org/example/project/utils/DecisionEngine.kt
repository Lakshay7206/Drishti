package org.example.project.utils

import kotlin.math.abs

data class DetectedTarget(
    val label: String,
    val distance: Float,
    val position: String, // "Left", "Front", "Right"
    val confidence: Float,
    val boundingBox: List<Float>? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class RiskLevel {
    HIGH, MEDIUM, LOW
}

class DecisionEngine {
    
    // Store history of announced targets to prevent spam
    private val lastSpokenMap = mutableMapOf<String, Long>()
    private val trackingHistory = mutableMapOf<String, DetectedTarget>()
    
    var currentFindingTarget: String? = null

    /**
     * Main Brain: Processes detections and decides what (if anything) to speak.
     */
    fun evaluateTargets(targets: List<DetectedTarget>): List<String> {
        val currentTime = System.currentTimeMillis()

        // 1. Filter candidates based on LOS, Distance, and Risk
        val candidates = targets.filter { target ->
            if (target.confidence < 0.5f) return@filter false
            
            // Override: If we are looking for this specific thing, always consider it
            val isFindingTarget = currentFindingTarget?.let { 
                target.label.lowercase().contains(it.lowercase()) 
            } ?: false
            
            if (isFindingTarget) return@filter true

            // Apply Production Logic Filters
            shouldSpeak(target)
        }

        // 2. Prioritize (Distance first, then Risk)
        val sorted = candidates.sortedWith(compareBy(
            { it.distance },
            { getRiskLevel(it.label) == RiskLevel.HIGH } // High risk has priority
        ))

        // 3. Selection (Winner Takes All)
        val top = sorted.firstOrNull() ?: return emptyList()

        // 4. Cooldown / Stability check
        if (!canSpeak(top.label, currentTime)) return emptyList()

        // 5. Generate Human-Friendly Speech
        val message = generateSpeech(top, targets)
        
        // Critical override: If extremely close, use a flush-marker for UI layer
        val isCritical = top.distance < 1.0f || getRiskLevel(top.label) == RiskLevel.HIGH
        return if (isCritical) listOf("$$$message") else listOf(message)
    }

    private fun shouldSpeak(target: DetectedTarget): Boolean {
        // Filter 1: Line of Sight (Center corridor)
        val bbox = target.boundingBox
        val centerX = if (bbox != null) (bbox[0] + bbox[2]) / 2f else 0.5f
        val isInLineOfSight = centerX in 0.3f..0.7f // 40% center region as requested

        if (!isInLineOfSight) return false

        // Filter 2: Relevant Distance
        if (target.distance > 2.5f) return false

        // Filter 3: Risk Level
        val risk = getRiskLevel(target.label)
        return when (risk) {
            RiskLevel.HIGH -> true
            RiskLevel.MEDIUM -> target.distance < 1.5f // Only speak medium risk if fairly close
            RiskLevel.LOW -> false
        }
    }

    private fun getRiskLevel(label: String): RiskLevel {
        val lower = label.lowercase()
        return when {
            // High Risk: Collision or critical hazards
            listOf("person", "car", "vehicle", "bike", "truck", "bus", "stairs", "pole", "wall", "dog", "hole", "elevator")
                .any { lower.contains(it) } -> RiskLevel.HIGH
            
            // Medium Risk: Fixed furniture or stationary obstacles
            listOf("chair", "table", "desk", "dustbin", "trash", "bench", "couch", "bed", "furniture")
                .any { lower.contains(it) } -> RiskLevel.MEDIUM
            
            // Low Risk: Small items or environment details
            else -> RiskLevel.LOW
        }
    }

    private fun generateSpeech(target: DetectedTarget, allTargets: List<DetectedTarget>): String {
        // If it's a finding target, give directional guidance and spatial reference
        currentFindingTarget?.let { finding ->
            if (target.label.lowercase().contains(finding.lowercase())) {
                val direction = when (target.position) {
                    "Left" -> "slightly left"
                    "Right" -> "slightly right"
                    else -> "directly ahead"
                }
                
                val reference = findReferenceObject(target, allTargets)
                val referenceMsg = reference?.let { " on the ${it.label}" } ?: ""
                
                return "${target.label} found$referenceMsg, $direction. It is ${target.distance.format(1)} meters away."
            }
        }

        // Standard Navigation Cues
        val label = target.label.lowercase()
        return when {
            label.contains("person") -> "Person ahead"
            label.contains("car") || label.contains("vehicle") -> "Vehicle ahead"
            label.contains("stairs") -> "Stairs ahead, be careful"
            label.contains("pole") || label.contains("wall") -> "Obstacle ahead"
            else -> "Obstacle ahead, ${target.distance.format(1)} meters"
        }
    }

    private fun canSpeak(label: String, currentTime: Long): Boolean {
        val last = lastSpokenMap[label] ?: 0
        if (currentTime - last > 3500) { // 3.5 sec cooldown
            lastSpokenMap[label] = currentTime
            return true
        }
        return false
    }

    private fun Float.format(digits: Int): String = String.format("%.${digits}f", this)

    // Keep for potential reference mode (e.g. "on the table") in finding mode
    private fun findReferenceObject(target: DetectedTarget, allTargets: List<DetectedTarget>): DetectedTarget? {
        val targetBox = target.boundingBox ?: return null
        val candidates = listOf("table", "desk", "chair", "bed", "couch", "shelf", "dining table")
        
        return allTargets.filter { candidates.contains(it.label.lowercase()) }
            .firstOrNull { ref ->
                val refBox = ref.boundingBox ?: return@firstOrNull false
                val horizontalOverlap = targetBox[0] < refBox[2] && targetBox[2] > refBox[0]
                val isOnTop = targetBox[3] >= refBox[1] && targetBox[3] <= refBox[1] + 0.2f
                val isInside = targetBox[1] > refBox[1] && targetBox[3] < refBox[3]
                horizontalOverlap && (isOnTop || isInside)
            }
    }
}
