package com.example.basaya.data.model

data class Quiz(
    val id: String = "",
    val title: String = "",
    val questions: List<QuizQuestion> = emptyList()
)