package com.example.basaya.data.model

data class Lecture (
    val id: String,
    val title: String,
    val pages: List<LecturePage>
)