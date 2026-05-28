package com.example.basaya.model

data class Lesson(

    val id: String,
    val title: String,
    val description: String,
    val difficulty: String,
    val imageRes: Int,

    val lecture: Lecture,
    val activity: Activity,
    val quiz: Quiz,
    val game: Game
)