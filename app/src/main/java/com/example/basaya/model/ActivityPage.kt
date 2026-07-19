package com.example.basaya.model

data class ActivityPage(
    val words: List<String> = emptyList(),
    val correctIndices: List<Int> = emptyList()
)