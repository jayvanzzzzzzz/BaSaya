package com.example.basaya.data.mock

import com.example.basaya.model.CrosswordGameLevel

object CrosswordMockData {
    val gameLevels = listOf(
        CrosswordGameLevel(
            id = 1,
            level = 1,
            words = listOf("LAMESA", "SALA", "LASA")
        ),

        CrosswordGameLevel(
            id = 2,
            level = 2,
            words = listOf("QWERTY", "YTREWQ", "WERTYQ")
        )
    )
}