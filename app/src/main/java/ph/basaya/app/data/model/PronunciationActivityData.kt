package ph.basaya.app.data.model
data class PronunciationActivityData(
    val id: String = "",
    val title: String = "",
    val instruction: String = "",
    val words: List<String> = emptyList(),
    val audioRefs: List<String> = emptyList(),
    val pages: List<PronunciationReadingPage> = emptyList()
)
data class PronunciationReadingWord(val word: String = "", val audioUrl: String = "")
data class PronunciationReadingPage(val sentence: String = "", val audioUrl: String = "", val words: List<PronunciationReadingWord> = emptyList())
