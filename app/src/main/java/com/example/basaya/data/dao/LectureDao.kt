package com.example.basaya.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.basaya.data.entity.LectureEntity

@Dao
interface LectureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(lecture: LectureEntity)

    @Query("SELECT * FROM lectures WHERE lessonId = :lessonId")
    suspend fun getLecture(lessonId: String): LectureEntity?

    @Query("DELETE FROM lectures WHERE lessonId = :lessonId")
    suspend fun deleteByLessonId(lessonId: String)
}