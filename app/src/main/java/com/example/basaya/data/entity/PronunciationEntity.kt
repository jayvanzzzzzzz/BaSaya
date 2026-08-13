package com.example.basaya.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pronunciation")
data class PronunciationEntity(
    @PrimaryKey val lessonId: String,
    val title: String,
    val instruction: String,
    val wordsJson: String,
    val audioRefsJson: String
)