package com.example.basaya.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "crossword_levels")
data class CrosswordLevelEntity(
    @PrimaryKey
    val id: String,         // Firestore document ID
    val lessonId: String,   // foreign key to lessons
    val level: Int,
    val words: String,      // comma-separated e.g. "LAMESA,SALA,LASA"
    val order: Int
)