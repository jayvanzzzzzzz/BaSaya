package com.example.basaya.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.basaya.data.entity.QuizEntity

@Dao
interface QuizDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(quiz: QuizEntity)

    @Query("SELECT * FROM quizzes WHERE lessonId = :lessonId")
    suspend fun getQuiz(lessonId: String): QuizEntity?
}