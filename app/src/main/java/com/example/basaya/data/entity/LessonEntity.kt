package com.example.basaya.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lessons")
data class LessonEntity(
    @PrimaryKey
    val id: String,         // Firestore document ID
    val userId: String,     // which user this cached lesson belongs to
    val title: String,
    val description: String,
    val difficulty: String,
    val order: Int,
    val unlocked: Boolean   // whether this user can access the lesson yet
)