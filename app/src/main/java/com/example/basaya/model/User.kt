package com.example.basaya.model

import java.util.Date

data class User(
    val uid: String = "",
    val email: String = "",
    val role: String = "student",
    val firstName: String = "",
    val lastName: String = "",
    val dob: String = "",
    val gender: String = "",
    val username: String = "",
    val middleName: String = "",
    val createdAt: Long = System.currentTimeMillis()
)