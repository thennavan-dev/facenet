package com.example.utils

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import com.example.utils.FaceNetModel.Companion.INPUT_SIZE
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.atan2
import kotlin.math.hypot

object FaceAligner {

    // Standard eye locations in a normalized 112x112 output frame
    private val REF_LEFT_EYE = PointF(38.2946f, 51.6963f)
    private val REF_RIGHT_EYE = PointF(73.5318f, 51.5014f)

    /**
     * Aligns and crops the face from the source bitmap based on MlKit landmarks.
     * Horizontal levels the eyes, applies uniform scale and center offsets.
     */
    fun align(src: Bitmap, face: Face, rotation: Int, isFront: Boolean): Bitmap? {
        val leftEyeLm = face.getLandmark(FaceLandmark.LEFT_EYE) ?: return null
        val rightEyeLm = face.getLandmark(FaceLandmark.RIGHT_EYE) ?: return null

        // MlKit coordinates are based on sensor scale. Map standard points rotated upright
        var leftPt = rotateLandmark(leftEyeLm.position, src.width, src.height, rotation)
        var rightPt = rotateLandmark(rightEyeLm.position, src.width, src.height, rotation)

        if (isFront) {
            leftPt = PointF(src.width - leftPt.x, leftPt.y)
            rightPt = PointF(src.width - rightPt.x, rightPt.y)
        }

        val matrix = buildSimilarityTransform(leftPt, rightPt)

        return try {
            val transformed = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
            Bitmap.createScaledBitmap(transformed, INPUT_SIZE, INPUT_SIZE, true)
        } catch (e: Exception) {
            android.util.Log.e("FaceAligner", "Failed to construct aligned bitmap: ${e.message}")
            null
        }
    }

    private fun rotateLandmark(p: PointF, width: Int, height: Int, rotation: Int): PointF {
        return when (rotation) {
            90 -> PointF(p.y, width - p.x)
            180 -> PointF(width - p.x, height - p.y)
            270 -> PointF(height - p.y, p.x)
            else -> PointF(p.x, p.y)
        }
    }

    private fun buildSimilarityTransform(srcLeft: PointF, srcRight: PointF): Matrix {
        val dx = srcRight.x - srcLeft.x
        val dy = srcRight.y - srcLeft.y
        val srcDist = hypot(dx, dy).coerceAtLeast(1e-4f)

        val rdx = REF_RIGHT_EYE.x - REF_LEFT_EYE.x
        val rdy = REF_RIGHT_EYE.y - REF_LEFT_EYE.y
        val refDist = hypot(rdx, rdy)

        val scale = refDist / srcDist
        val angle = Math.toDegrees(
            atan2(dy.toDouble(), dx.toDouble()) - atan2(rdy.toDouble(), rdx.toDouble())
        ).toFloat()

        val matrix = Matrix()
        matrix.postScale(scale, scale, srcLeft.x, srcLeft.y)
        matrix.postRotate(-angle, srcLeft.x, srcLeft.y)

        val pts = floatArrayOf(srcLeft.x, srcLeft.y)
        matrix.mapPoints(pts)
        matrix.postTranslate(REF_LEFT_EYE.x - pts[0], REF_LEFT_EYE.y - pts[1])
        return matrix
    }
}
