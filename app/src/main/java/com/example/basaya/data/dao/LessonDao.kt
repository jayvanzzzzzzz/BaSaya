package com.example.basaya.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.basaya.data.entity.LessonEntity

@Dao
interface LessonDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(lessons: List<LessonEntity>)

    @Query("SELECT * FROM lessons ORDER BY `order` ASC")
    suspend fun getAllLessons(): List<LessonEntity>

    @Query("SELECT * FROM lessons WHERE userId = :userId ORDER BY `order` ASC")
    suspend fun getLessonsForUser(userId: String): List<LessonEntity>

    @Query("DELETE FROM lessons WHERE userId = :userId")
    suspend fun deleteLessonsForUser(userId: String)
}