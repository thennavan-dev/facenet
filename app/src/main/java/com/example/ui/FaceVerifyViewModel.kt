package com.example.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.FaceRepository
import com.example.data.MatchResult
import com.example.model.FaceTemplate
import com.example.utils.FacePose
import com.example.utils.FaceAligner
import com.example.utils.FaceNetModel
import com.example.utils.ModelDownloader
import com.example.utils.PoseDetector
import com.google.mlkit.vision.face.Face
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FaceVerifyViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = FaceRepository(database.faceTemplateDao())
    private val faceNetModel = FaceNetModel(application)

    var isModelReady by mutableStateOf(false)
    val modelDownloadProgress = ModelDownloader.downloadProgress
    val modelDownloadError = ModelDownloader.errorMessage

    init {
        isModelReady = ModelDownloader.isModelAvailable(application)
        if (!isModelReady) {
            triggerModelDownload(application)
        }
    }

    fun triggerModelDownload(context: android.content.Context) {
        viewModelScope.launch {
            statusMessage = "Downloading biometric neural network..."
            ModelDownloader.startDownload(context) {
                isModelReady = faceNetModel.reload(context)
                if (isModelReady) {
                    statusMessage = "Biometric network ready. Looking for face..."
                } else {
                    statusMessage = "⚠️ Loaded network corrupted. Please restart app."
                }
            }
        }
    }

    val allTemplates: StateFlow<List<FaceTemplate>> = repository.allTemplates
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val distinctNames: StateFlow<List<String>> = repository.distinctNames
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Camera and Tracking states
    var isScanning by mutableStateOf(true)
    var currentMatch by mutableStateOf<MatchResult?>(null)
    var activeFaceRect by mutableStateOf<android.graphics.RectF?>(null)
    var faceDetectedInFrame by mutableStateOf(false)
    
    // UI details
    var activeYaw by mutableStateOf(0f)
    var activePitch by mutableStateOf(0f)

    // Registration States
    var isRegistering by mutableStateOf(false)
    var registerName by mutableStateOf("")
    var currentRegPose by mutableStateOf<FacePose?>(null)
    val capturedPoses = mutableMapOf<FacePose, FloatArray>()
    private val currentPoseSamples = mutableListOf<FloatArray>()
    var poseSampleProgress by mutableStateOf(0) // 0 to 3 count

    // Status / User instructions
    var statusMessage by mutableStateOf("Position your face to begin")

    fun startRegistration(name: String) {
        registerName = name
        isRegistering = true
        isScanning = false
        capturedPoses.clear()
        currentPoseSamples.clear()
        poseSampleProgress = 0
        currentRegPose = FacePose.FRONT
        statusMessage = "Step 1 of 5: Look straight at the camera"
    }

    fun cancelRegistration() {
        isRegistering = false
        registerName = ""
        currentRegPose = null
        capturedPoses.clear()
        currentPoseSamples.clear()
        poseSampleProgress = 0
        isScanning = true
        statusMessage = "Tracking active"
    }

    fun deleteFace(name: String) {
        viewModelScope.launch {
            repository.deleteByName(name)
            if (currentMatch?.name == name) {
                currentMatch = null
            }
        }
    }

    fun deleteTemplateById(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    /**
     * Called on each CameraX frame from the Analyzer thread.
     */
    fun onFaceDetected(
        face: Face,
        frameBitmap: android.graphics.Bitmap,
        imgWidth: Int,
        imgHeight: Int,
        rotation: Int,
        previewW: Int,
        previewH: Int,
        isFront: Boolean
    ) {
        if (!isModelReady) {
            statusMessage = "⏳ Downloading biometric model library..."
            return
        }

        // Map original boundary box to match screen resolution coordinates
        val mapped = mapToPreview(
            face.boundingBox, imgWidth, imgHeight, rotation, previewW, previewH, isFront
        )
        
        activeFaceRect = mapped
        faceDetectedInFrame = true
        activeYaw = face.headEulerAngleY
        activePitch = face.headEulerAngleX

        // Eye Blink Gate - rejects blurry closed eye captures as noise
        val leftOpen = face.leftEyeOpenProbability ?: 1.0f
        val rightOpen = face.rightEyeOpenProbability ?: 1.0f
        if (leftOpen < 0.25f || rightOpen < 0.25f) {
            statusMessage = "⚠️ Keep your eyes open"
            return
        }

        // Feature Size Gate
        if (face.boundingBox.width() < 120 || face.boundingBox.height() < 120) {
            statusMessage = "⚠️ Please move closer"
            return
        }

        val alignedBitmap = FaceAligner.align(frameBitmap, face, rotation, isFront)
        if (alignedBitmap == null) {
            statusMessage = "⚠️ Ensure eyes are visible for alignment"
            return
        }
        val signature = faceNetModel.getEmbedding(alignedBitmap)
        if (signature == null) {
            statusMessage = "⚠️ Error calculating face signature"
            return
        }

        if (isScanning) {
            // Scanning Mode
            viewModelScope.launch {
                val match = repository.findBestMatch(signature)
                currentMatch = match
                statusMessage = if (match != null) {
                    "✅ Verified: ${match.name} [Confidence: ${(match.similarity * 100).toInt()}% • Pose: ${match.pose}]"
                } else {
                    "❌ Unknown face"
                }
            }
        } else if (isRegistering && currentRegPose != null) {
            // Biometric Registration Flow (Multi-Pose stepping)
            val detectedPose = PoseDetector.detectPose(face)
            if (detectedPose == currentRegPose) {
                statusMessage = "Capturing ${detectedPose.label}... Don't move."
                currentPoseSamples.add(signature)
                poseSampleProgress = currentPoseSamples.size

                if (currentPoseSamples.size >= 3) {
                    // Outliers filtering and signal averaging
                    val averaged = averageSignatures(currentPoseSamples)
                    if (averaged != null) {
                        capturedPoses[currentRegPose!!] = averaged
                        currentPoseSamples.clear()
                        poseSampleProgress = 0
                        advanceRegistrationStep()
                    } else {
                        currentPoseSamples.clear()
                        poseSampleProgress = 0
                        statusMessage = "Capture instable. Keep head steady."
                    }
                }
            } else {
                statusMessage = "⚠️ Move head to match instruction below"
            }
        }
    }

    fun onFaceCleared() {
        activeFaceRect = null
        faceDetectedInFrame = false
        currentPoseSamples.clear()
        poseSampleProgress = 0
        if (isScanning) {
            currentMatch = null
            statusMessage = "Looking for a face..."
        }
    }

    private fun advanceRegistrationStep() {
        val current = currentRegPose ?: return
        val poses = FacePose.values()
        val nextIndex = current.ordinal + 1

        if (nextIndex < poses.size) {
            currentRegPose = poses[nextIndex]
            statusMessage = "Tilt to next angle: ${poses[nextIndex].label}"
        } else {
            // Completed all 5 angles! Save everything to Room
            viewModelScope.launch {
                capturedPoses.forEach { (pose, sig) ->
                    repository.insert(FaceTemplate.fromArray(registerName.trim(), pose.label, sig))
                }
                isRegistering = false
                registerName = ""
                currentRegPose = null
                capturedPoses.clear()
                isScanning = true
                statusMessage = "✅ Registration completed! Active Scanning enabled."
            }
        }
    }

    private fun averageSignatures(samples: List<FloatArray>): FloatArray? {
        if (samples.isEmpty()) return null
        if (samples.size == 1) return samples[0]

        val size = FaceNetModel.EMBEDDING_DIM
        val keep = mutableListOf<FloatArray>()

        // Look for consistent samples (drop out-of-bounds micro flashes)
        for (i in samples.indices) {
            var sumSimilarity = 0f
            var count = 0
            for (j in samples.indices) {
                if (i == j) continue
                var score = 0f
                for (d in 0 until size) {
                    score += samples[i][d] * samples[j][d]
                }
                sumSimilarity += score
                count++
            }
            val avgSim = if (count == 0) 1f else sumSimilarity / count
            if (avgSim >= 0.82f) { // high consistency requirement
                keep.add(samples[i])
            }
        }

        val pool = if (keep.isEmpty()) samples else keep
        val mean = FloatArray(size)
        for (v in pool) {
            for (d in 0 until size) {
                mean[d] += v[d]
            }
        }
        for (d in 0 until size) {
            mean[d] /= pool.size
        }
        
        // Re-normalize the averaged vector so it lies perfectly on the unit sphere
        var sumSquares = 0f
        for (d in 0 until size) {
            sumSquares += mean[d] * mean[d]
        }
        val norm = kotlin.math.sqrt(sumSquares.toDouble()).toFloat()
        if (norm > 1e-10f) {
            for (d in 0 until size) {
                mean[d] /= norm
            }
        }
        return mean
    }

    override fun onCleared() {
        super.onCleared()
        faceNetModel.close()
    }

    private fun mapToPreview(
        box: android.graphics.Rect,
        imgW: Int,
        imgH: Int,
        rotation: Int,
        previewW: Int,
        previewH: Int,
        isFront: Boolean
    ): android.graphics.RectF {
        val (srcW, srcH) = if (rotation == 90 || rotation == 270) {
            imgH.toFloat() to imgW.toFloat()
        } else {
            imgW.toFloat() to imgH.toFloat()
        }
        val sx = previewW / srcW
        val sy = previewH / srcH
        var l = box.left * sx
        val t = box.top * sy
        var r = box.right * sx
        val b = box.bottom * sy
        if (isFront) {
            val tmp = l
            l = previewW - r
            r = previewW - tmp
        }
        return android.graphics.RectF(l, t, r, b)
    }
}
