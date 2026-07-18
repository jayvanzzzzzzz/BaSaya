package com.example.basaya.model

data class LecturePage(
    val pageNumber: Int,
    val sentences: List<String>,
    val audioResNames: List<String> = emptyList() // e.g. "lesson1_page1_sentence1" -> res/raw file
)