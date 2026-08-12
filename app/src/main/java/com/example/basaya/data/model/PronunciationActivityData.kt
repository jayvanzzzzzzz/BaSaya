package com.example.basaya.data.model
data class PronunciationActivityData(
    val id: String = "",
    val title: String = "",
    val instruction: String = "",
    val words: List<String> = emptyList(),
    val audioRefs: List<String> = emptyList()
)