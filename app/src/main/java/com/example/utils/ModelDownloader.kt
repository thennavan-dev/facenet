package com.example.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object ModelDownloader {

    private val _downloadProgress = MutableStateFlow<Int?>(null) // null = idle/done, 0-100 = active downloading progress
    val downloadProgress: StateFlow<Int?> = _downloadProgress

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun isModelAvailable(context: Context): Boolean {
        val file = File(context.filesDir, "models/mobilefacenet.tflite")
        return file.exists() && file.length() > 1000000
    }

    suspend fun startDownload(context: Context, onComplete: () -> Unit) = withContext(Dispatchers.IO) {
        val targetFile = File(context.filesDir, "models/mobilefacenet.tflite")
        if (targetFile.exists() && targetFile.length() > 1000000) {
            _downloadProgress.value = 100
            withContext(Dispatchers.Main) {
                onComplete()
            }
            return@withContext
        }

        try {
            _errorMessage.value = null
            _downloadProgress.value = 0
            targetFile.parentFile?.mkdirs()

            // Dual multi-CDN raw endpoints representing perfect fallback redundancy!
            val urls = listOf(
                "https://raw.githubusercontent.com/shubham0204/Face_Recognition_with_Tensorflow_Lite/master/app/src/main/assets/mobilefacenet.tflite",
                "https://raw.githubusercontent.com/goyalankit/Face-Recognition-Android/master/app/src/main/assets/mobilefacenet.tflite",
                "https://gitee.com/shubham0204/Face-Recognition-with-TensorFlow-Lite/raw/master/app/src/main/assets/mobilefacenet.tflite"
            )

            var success = false
            var lastError = "Could not download Face model"

            for (urlString in urls) {
                if (success) break
                try {
                    android.util.Log.i("ModelDownloader", "Attempting download from: $urlString")
                    val url = URL(urlString)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    connection.instanceFollowRedirects = true
                    connection.connect()

                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        connection.disconnect()
                        continue
                    }

                    val fileLength = connection.contentLength
                    BufferedInputStream(connection.inputStream).use { input ->
                        FileOutputStream(targetFile).use { output ->
                            val data = ByteArray(8192)
                            var total: Long = 0
                            var count: Int
                            while (input.read(data).also { count = it } != -1) {
                                total += count.toLong()
                                if (fileLength > 0) {
                                    val percent = ((total * 100) / fileLength).toInt()
                                    _downloadProgress.value = percent
                                }
                                output.write(data, 0, count)
                            }
                        }
                    }
                    connection.disconnect()

                    if (targetFile.exists() && targetFile.length() > 1000000) {
                        success = true
                        android.util.Log.i("ModelDownloader", "Successfully downloaded biometric model from: $urlString")
                    }
                } catch (e: Exception) {
                    lastError = e.message ?: "Connection error"
                    android.util.Log.e("ModelDownloader", "Download failed for $urlString: ${e.message}")
                }
            }

            if (success) {
                _downloadProgress.value = 100
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            } else {
                targetFile.delete()
                _downloadProgress.value = null
                _errorMessage.value = "Failed to download face model: $lastError. Please find an internet connection."
            }
        } catch (e: Exception) {
            targetFile.delete()
            _downloadProgress.value = null
            _errorMessage.value = e.message ?: "Fatal exception occurred."
        }
    }
}
