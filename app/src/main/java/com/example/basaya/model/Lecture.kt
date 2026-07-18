package com.example.basaya.model

import com.example.basaya.model.LecturePage

data class Lecture (
    val id: String,
    val title: String,
    val pages: List<LecturePage>
)