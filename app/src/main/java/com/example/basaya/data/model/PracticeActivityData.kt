package com.example.basaya.data.model

data class PracticeActivityData(
    val id: String = "",
    val title: String = "",
    val instruction: String = "",
    val explanation: String = "",
    val pages: List<ActivityPage> = emptyList()
)