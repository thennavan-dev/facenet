package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "face_templates")
data class FaceTemplate(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val pose: String, // "Front", "Right", "Left", "Up", "Down"
    val featuresCsv: String, // Comma-separated floats representing the signature
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getFeaturesArray(): FloatArray {
        if (featuresCsv.isEmpty()) return FloatArray(0)
        return try {
            featuresCsv.split(",").map { it.toFloat() }.toFloatArray()
        } catch (e: Exception) {
            FloatArray(0)
        }
    }

    companion object {
        fun fromArray(name: String, pose: String, array: FloatArray): FaceTemplate {
            val csv = array.joinToString(",") { it.toString() }
            return FaceTemplate(name = name, pose = pose, featuresCsv = csv)
        }
    }
}
