package com.example.ui.composables

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.FaceVerifyViewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun CameraPreview(
    viewModel: FaceVerifyViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val lensFacing = CameraSelector.LENS_FACING_FRONT

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(lensFacing) {
        val cameraProviderProvider = ProcessCameraProvider.getInstance(context)
        cameraProviderProvider.addListener({
            val cameraProvider = cameraProviderProvider.get()
            
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val faceDetector = FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .build()
            )

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                    val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)
                    
                    faceDetector.process(image)
                        .addOnSuccessListener { faces ->
                            if (faces.isNotEmpty()) {
                                val bitmap = try {
                                    imageProxy.toBitmap()
                                } catch (e: Exception) {
                                    null
                                }
                                if (bitmap != null) {
                                    viewModel.onFaceDetected(
                                        faces[0],
                                        bitmap,
                                        imageProxy.width,
                                        imageProxy.height,
                                        rotationDegrees,
                                        previewView.width,
                                        previewView.height,
                                        isFront = true
                                    )
                                } else {
                                    viewModel.onFaceCleared()
                                }
                            } else {
                                viewModel.onFaceCleared()
                            }
                        }
                        .addOnFailureListener {
                            viewModel.onFaceCleared()
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
                    CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                    preview,
                    analysis
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = modifier) {
        // Camera Live Surface
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Native overlay tracking Canvas
        val faceRect = viewModel.activeFaceRect
        val hasFace = viewModel.faceDetectedInFrame && faceRect != null
        
        val animatedColor by animateColorAsState(
            targetValue = when {
                viewModel.isRegistering -> Color(0xFF673AB7) // Biometric Registration Purple
                viewModel.currentMatch != null -> Color(0xFF4CAF50) // Verified Match Green
                hasFace -> Color(0xFFE53935) // Face Tracked (evaluating/unknown) Red
                else -> Color.DarkGray.copy(alpha = 0.5f)
            },
            animationSpec = tween(300),
            label = "bracket_color"
        )

        val activeRect = faceRect ?: android.graphics.RectF(0f, 0f, 0f, 0f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (hasFace) {
                val r = activeRect
                val strokeWidth = 8f
                val cornerLength = (r.width() * 0.15f).coerceAtLeast(35f)

                // Render outer bounded contour line
                drawRoundRect(
                    color = animatedColor.copy(alpha = 0.12f),
                    topLeft = Offset(r.left, r.top),
                    size = androidx.compose.ui.geometry.Size(r.width(), r.height()),
                    cornerRadius = CornerRadius(20f, 20f),
                    style = Stroke(width = 3f)
                )

                // Top-Left bracket
                val path1 = Path().apply {
                    moveTo(r.left - strokeWidth / 2, r.top + cornerLength)
                    lineTo(r.left - strokeWidth / 2, r.top - strokeWidth / 2)
                    lineTo(r.left + cornerLength, r.top - strokeWidth / 2)
                }
                drawPath(
                    path = path1,
                    color = animatedColor,
                    style = Stroke(width = strokeWidth)
                )

                // Top-Right bracket
                val path2 = Path().apply {
                    moveTo(r.right + strokeWidth / 2, r.top + cornerLength)
                    lineTo(r.right + strokeWidth / 2, r.top - strokeWidth / 2)
                    lineTo(r.right - cornerLength, r.top - strokeWidth / 2)
                }
                drawPath(
                    path = path2,
                    color = animatedColor,
                    style = Stroke(width = strokeWidth)
                )

                // Bottom-Left bracket
                val path3 = Path().apply {
                    moveTo(r.left - strokeWidth / 2, r.bottom - cornerLength)
                    lineTo(r.left - strokeWidth / 2, r.bottom + strokeWidth / 2)
                    lineTo(r.left + cornerLength, r.bottom + strokeWidth / 2)
                }
                drawPath(
                    path = path3,
                    color = animatedColor,
                    style = Stroke(width = strokeWidth)
                )

                // Bottom-Right bracket
                val path4 = Path().apply {
                    moveTo(r.right + strokeWidth / 2, r.bottom - cornerLength)
                    lineTo(r.right + strokeWidth / 2, r.bottom + strokeWidth / 2)
                    lineTo(r.right - cornerLength, r.bottom + strokeWidth / 2)
                }
                drawPath(
                    path = path4,
                    color = animatedColor,
                    style = Stroke(width = strokeWidth)
                )
            }
        }
    }
}
