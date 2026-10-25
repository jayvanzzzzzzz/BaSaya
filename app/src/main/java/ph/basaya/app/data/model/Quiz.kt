package ph.basaya.app.data.model

data class Quiz(
    val id: String = "",
    val title: String = "",
    val questions: List<QuizQuestion> = emptyList()
)