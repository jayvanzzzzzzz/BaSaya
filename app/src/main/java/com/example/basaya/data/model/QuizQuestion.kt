package com.example.basaya.data.model

data class QuizQuestion(
    val question: String = "",
    val choices: List<String> = emptyList(),
    val answer: Int = 0 // index into choices
)