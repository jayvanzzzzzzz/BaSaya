package com.example.basaya.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_progress")
data class CrosswordProgress(
    @PrimaryKey
    val id: Int = 1,
    val currentLevel: Int?,
    val completedLevel: Int = 0,
    val foundWords: String = ""
)