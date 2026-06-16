package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.model.FaceTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface FaceTemplateDao {
    @Query("SELECT * FROM face_templates ORDER BY timestamp DESC")
    fun getAllTemplates(): Flow<List<FaceTemplate>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: FaceTemplate)

    @Query("DELETE FROM face_templates WHERE id = :id")
    suspend fun deleteTemplateById(id: Int)

    @Query("DELETE FROM face_templates WHERE name = :name")
    suspend fun deleteByName(name: String)

    @Query("SELECT DISTINCT name FROM face_templates")
    fun getDistinctNamesFlow(): Flow<List<String>>

    @Query("SELECT * FROM face_templates WHERE name = :name")
    suspend fun getTemplatesForName(name: String): List<FaceTemplate>
}
