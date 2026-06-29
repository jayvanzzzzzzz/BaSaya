package com.example.basaya.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.basaya.data.entity.CrosswordProgress

@Dao
interface GameProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: CrosswordProgress)

    @Query("SELECT * FROM game_progress WHERE id = 1")
    suspend fun getProgress(): CrosswordProgress?
}