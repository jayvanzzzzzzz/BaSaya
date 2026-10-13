package com.example.basaya.data.model

data class QuizQuestion(
    val question: String = "",
    val choices: List<String> = emptyList(),
    val answer: Int = 0, // index into choices (older lessons: the only correct answer)
    // Set in basaya-web when a question has several correct choices. Nullable because quizzes
    // cached in Room before these fields existed deserialize them as null.
    val answers: List<Int>? = null,
    val multiple: Boolean? = null
) {
    /** All correct choice indices: `answers` when present, otherwise the single `answer`. */
    val correctAnswers: Set<Int>
        get() = answers?.takeIf { it.isNotEmpty() }?.toSet() ?: setOf(answer)

    /** Two or more correct answers: the student must tap all of them. */
    val isMultiple: Boolean get() = correctAnswers.size > 1
}
