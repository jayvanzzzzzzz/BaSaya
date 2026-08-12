package com.example.basaya.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lessons")
data class LessonEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val title: String,
    val description: String,
    val difficulty: String,
    val order: Int,
    val unlocked: Boolean,
    val lectureFinished: Boolean = false,
    val gameFinished: Boolean = false,
    val activityFinished: Boolean = false,
    val quizFinished: Boolean = false,
    val isDownloaded: Boolean
)