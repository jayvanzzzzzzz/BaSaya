package com.example.basaya.model

import com.google.firebase.Timestamp

data class AssignedLesson(
    val lessonId: String = "",
    val assignedAt: Timestamp? = null,
    val unlocked: Boolean = false
)