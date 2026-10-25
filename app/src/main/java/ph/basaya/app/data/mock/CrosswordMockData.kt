package ph.basaya.app.data.mock

import ph.basaya.app.data.model.CrosswordGameLevel

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