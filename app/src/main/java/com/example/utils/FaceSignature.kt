package com.example.utils

import android.graphics.PointF
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.hypot
import kotlin.math.sqrt

object FaceSignature {
    const val DIMENSION = 12

    /**
     * Extracts a 12-dimensional, perfectly scale-invariant feature vector
     * representing the unique geometric proportions of a face.
     */
    fun extract(face: Face): FloatArray? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)?.position ?: return null
        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position ?: return null
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position ?: return null
        val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position ?: return null
        val leftCheek = face.getLandmark(FaceLandmark.LEFT_CHEEK)?.position ?: return null
        val rightCheek = face.getLandmark(FaceLandmark.RIGHT_CHEEK)?.position ?: return null

        val pD = dist(leftEye, rightEye) // Pupillary Distance
        if (pD < 1e-4f) return null

        val dNoseLeft = dist(noseBase, leftEye)
        val dNoseRight = dist(noseBase, rightEye)
        val dMouthWidth = dist(mouthLeft, mouthRight)
        
        val mouthCenter = PointF(
            (mouthLeft.x + mouthRight.x) / 2f,
            (mouthLeft.y + mouthRight.y) / 2f
        )
        val dNoseMouth = dist(noseBase, mouthCenter)
        val dMouthBottom = dist(mouthBottom, noseBase)
        val dCheekLeft = dist(leftCheek, noseBase)
        val dCheekRight = dist(rightCheek, noseBase)
        val dCheekDist = dist(leftCheek, rightCheek)
        val dLeftEyeMouth = dist(leftEye, mouthLeft)
        val dRightEyeMouth = dist(rightEye, mouthRight)

        val features = FloatArray(DIMENSION)
        features[0] = dNoseLeft / pD
        features[1] = dNoseRight / pD
        features[2] = dMouthWidth / pD
        features[3] = dNoseMouth / pD
        features[4] = dMouthBottom / pD
        features[5] = dCheekLeft / pD
        features[6] = dCheekRight / pD
        features[7] = dCheekDist / pD
        features[8] = dLeftEyeMouth / pD
        features[9] = dRightEyeMouth / pD
        
        // Lateral symmetries - extremely useful details
        features[10] = if (dNoseRight > 0f) dNoseLeft / dNoseRight else 1.0f
        features[11] = if (dCheekRight > 0f) dCheekLeft / dCheekRight else 1.0f

        return features
    }

    private fun dist(p1: PointF, p2: PointF): Float {
        return hypot(p1.x - p2.x, p1.y - p2.y)
    }

    /**
     * Measures similarity between two biometric signatures.
     * Uses solid statistical weights assigning higher importance to structural
     * bone points (eyes, nose, mouth corners) than cheeks, which vary under smiling.
     */
    fun similarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.size != DIMENSION || v2.size != DIMENSION) return 0f
        
        // Eye-nose-mouth bone coordinates are the most stable features
        val weights = floatArrayOf(
            1.2f, // nose-left distance
            1.2f, // nose-right distance
            1.1f, // mouth-width
            1.0f, // nose-mouth
            1.0f, // mouth-bottom
            0.8f, // cheek-left
            0.8f, // cheek-right
            0.8f, // cheek-distance
            1.1f, // left-eye to mouth
            1.1f, // right-eye to mouth
            0.9f, // nose-symmetry
            0.8f  // cheek-symmetry
        )

        var dot = 0f
        var normA = 0f
        var normB = 0f

        for (i in 0 until DIMENSION) {
            val w = weights[i]
            val wa = v1[i] * w
            val wb = v2[i] * w
            dot += wa * wb
            normA += wa * wa
            normB += wb * wb
        }

        if (normA <= 0f || normB <= 0f) return 0f
        return dot / (sqrt(normA) * sqrt(normB))
    }
}
