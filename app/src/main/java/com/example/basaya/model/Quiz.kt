package com.example.basaya.model

data class Quiz(
    val id: String = "",
    val title: String = "",
    val questions: List<QuizQuestion> = emptyList()
)