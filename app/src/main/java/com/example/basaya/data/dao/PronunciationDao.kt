package com.example.basaya.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.basaya.data.entity.PronunciationEntity

@Dao
interface PronunciationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PronunciationEntity)

    @Query("SELECT * FROM pronunciation WHERE lessonId = :lessonId LIMIT 1")
    suspend fun getActivity(lessonId: String): PronunciationEntity?

    @Query("DELETE FROM pronunciation WHERE lessonId = :lessonId")
    suspend fun deleteByLessonId(lessonId: String)
}