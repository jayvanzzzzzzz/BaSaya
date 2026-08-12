package com.example.basaya.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activities")
data class ActivityEntity(
    @PrimaryKey val lessonId: String,
    val title: String,
    val instruction: String,
    val explanation: String,
    val pagesJson: String
)