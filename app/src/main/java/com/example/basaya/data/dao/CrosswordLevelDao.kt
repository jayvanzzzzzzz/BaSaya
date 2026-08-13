package com.example.basaya.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.basaya.data.entity.CrosswordLevelEntity

@Dao
interface CrosswordLevelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(levels: List<CrosswordLevelEntity>)

    @Query("SELECT * FROM crossword_levels WHERE lessonId = :lessonId ORDER BY `order` ASC")
    suspend fun getLevelsForLesson(lessonId: String): List<CrosswordLevelEntity>

    @Query("DELETE FROM crossword_levels WHERE lessonId = :lessonId")
    suspend fun deleteByLessonId(lessonId: String)
}