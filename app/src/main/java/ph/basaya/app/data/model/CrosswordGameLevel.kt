package ph.basaya.app.data.model

data class CrosswordGameLevel(
    val id: Int,
    var level: Int,
    val words: List<String>
)