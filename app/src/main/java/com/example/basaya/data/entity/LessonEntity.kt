package com.example.basaya.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lessons")
data class LessonEntity(
    @PrimaryKey
    val id: String,         // Firestore document ID
    val title: String,
    val description: String,
    val difficulty: String,
    val order: Int
)
