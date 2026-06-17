package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt

class FaceNetModel(context: Context) {
    private var interpreter: Interpreter? = null
    val isLoaded: Boolean get() = interpreter != null

    companion object {
        const val INPUT_SIZE = 112
        const val EMBEDDING_DIM = 192 // Standard dimension for mobilefacenet embeddings
    }

    private val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)

    init {
        loadModel(context)
    }

    private fun loadModel(context: Context) {
        try {
            val localFile = java.io.File(context.filesDir, "models/mobilefacenet.tflite")
            val modelBuffer = if (localFile.exists()) {
                android.util.Log.i("FaceNetModel", "Loading model from secure local file caching ✓")
                val inputStream = FileInputStream(localFile)
                val fileChannel = inputStream.channel
                fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, localFile.length())
            } else {
                android.util.Log.w("FaceNetModel", "No local model found yet. Dynamic download required.")
                val assetManager = context.assets
                val fileDescriptor = try { assetManager.openFd("mobilefacenet.tflite") } catch (e: Exception) { null }
                if (fileDescriptor != null) {
                    val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
                    val fileChannel = inputStream.channel
                    fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
                } else {
                    null
                }
            }

            if (modelBuffer != null) {
                val options = Interpreter.Options().apply {
                    numThreads = 4
                    useXNNPACK = true
                }
                interpreter = Interpreter(modelBuffer, options)
                android.util.Log.i("FaceNetModel", "Successfully loaded MobileFaceNet TFLite model ✓")
            }
        } catch (e: Exception) {
            android.util.Log.e("FaceNetModel", "Failed to load TFLite model: ${e.message}", e)
        }
    }

    /**
     * Call to hot-reload the internal TFLite Interpreter once the model has finished downloading.
     */
    fun reload(context: Context): Boolean {
        close()
        loadModel(context)
        return isLoaded
    }

    /**
     * Preprocesses a structured 112x112 bitmap, runs inference and returns
     * its L2-normalized 192D embedding profile.
     */
    fun getEmbedding(bitmap: Bitmap): FloatArray? {
        val interp = interpreter ?: return null
        return try {
            val resized = if (bitmap.width == INPUT_SIZE && bitmap.height == INPUT_SIZE) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
            }

            // Allocate a direct byte buffer for TFLite (1 * 112 * 112 * 3 Channels * 4 Bytes per Float)
            val inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            // Quick bulk readout of image pixel colors
            resized.getPixels(intValues, 0, resized.width, 0, 0, resized.width, resized.height)
            inputBuffer.rewind()
            
            for (pixelValue in intValues) {
                // Normalize standard 0-255 RGB channels to [-1, 1] range matching MobileFaceNet requirements
                val r = ((pixelValue shr 16 and 0xFF) / 127.5f) - 1.0f
                val g = ((pixelValue shr 8 and 0xFF) / 127.5f) - 1.0f
                val b = ((pixelValue and 0xFF) / 127.5f) - 1.0f
                
                inputBuffer.putFloat(r)
                inputBuffer.putFloat(g)
                inputBuffer.putFloat(b)
            }

            val outputArray = Array(1) { FloatArray(EMBEDDING_DIM) }
            interp.run(inputBuffer, outputArray)
            
            // Perform mandatory L2 Normalization to ensure distance matches cosine space correctly
            l2Normalize(outputArray[0])
        } catch (e: Exception) {
            android.util.Log.e("FaceNetModel", "Inference error: ${e.message}", e)
            null
        }
    }

    private fun l2Normalize(v: FloatArray): FloatArray {
        var sumSquares = 0f
        for (value in v) {
            sumSquares += value * value
        }
        val norm = sqrt(sumSquares.toDouble()).toFloat()
        if (norm < 1e-10f) return v
        return FloatArray(v.size) { v[it] / norm }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
