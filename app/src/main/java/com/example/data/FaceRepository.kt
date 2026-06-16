package com.example.data

import com.example.model.FaceTemplate
import com.example.utils.FaceSignature
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class FaceRepository(private val faceTemplateDao: FaceTemplateDao) {

    val allTemplates: Flow<List<FaceTemplate>> = faceTemplateDao.getAllTemplates()
    val distinctNames: Flow<List<String>> = faceTemplateDao.getDistinctNamesFlow()

    suspend fun insert(template: FaceTemplate) {
        faceTemplateDao.insertTemplate(template)
    }

    suspend fun deleteById(id: Int) {
        faceTemplateDao.deleteTemplateById(id)
    }

    suspend fun deleteByName(name: String) {
        faceTemplateDao.deleteByName(name)
    }

    suspend fun getTemplatesForName(name: String): List<FaceTemplate> {
        return faceTemplateDao.getTemplatesForName(name)
    }

    /**
     * Finds the best match for the query features from all stored face templates.
     * Compares using scale-invariant weighted cosine similarity.
     */
    suspend fun findBestMatch(queryFeatures: FloatArray, threshold: Float = 0.925f): MatchResult? {
        val templates = allTemplates.firstOrNull() ?: return null
        if (templates.isEmpty()) return null

        var bestScore = -1f
        var bestMatch: FaceTemplate? = null

        for (template in templates) {
            val storedFeatures = template.getFeaturesArray()
            if (storedFeatures.size != FaceSignature.DIMENSION) continue
            
            val score = FaceSignature.similarity(queryFeatures, storedFeatures)
            if (score > bestScore) {
                bestScore = score
                bestMatch = template
            }
        }

        return if (bestScore >= threshold && bestMatch != null) {
            MatchResult(
                name = bestMatch.name,
                similarity = bestScore,
                pose = bestMatch.pose,
                id = bestMatch.id
            )
        } else {
            null
        }
    }
}

data class MatchResult(
    val name: String,
    val similarity: Float,
    val pose: String,
    val id: Int
)
