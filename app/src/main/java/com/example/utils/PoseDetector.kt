package com.example.utils

import com.google.mlkit.vision.face.Face

enum class FacePose(val label: String, val instruction: String) {
    FRONT("Front", "Look straight into the camera"),
    RIGHT("Right", "Turn your head slightly to the right"),
    LEFT("Left", "Turn your head slightly to the left"),
    UP("Up", "Tilt your chin slightly upwards"),
    DOWN("Down", "Tilt your chin slightly downwards")
}

object PoseDetector {
    fun detectPose(face: Face): FacePose? {
        val yaw = face.headEulerAngleY
        val pitch = face.headEulerAngleX

        return when {
            yaw < -15f && yaw >= -45f && pitch in -12f..12f -> FacePose.RIGHT
            yaw > 15f && yaw <= 45f && pitch in -12f..12f -> FacePose.LEFT
            pitch > 10f && pitch <= 35f && yaw in -12f..12f -> FacePose.UP
            pitch < -10f && pitch >= -35f && yaw in -12f..12f -> FacePose.DOWN
            yaw in -8f..8f && pitch in -8f..8f -> FacePose.FRONT
            else -> null
        }
    }
}
