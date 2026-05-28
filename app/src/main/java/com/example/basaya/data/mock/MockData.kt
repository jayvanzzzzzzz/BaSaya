package com.example.basaya.data.mock

import com.example.basaya.R
import com.example.basaya.model.Lesson
import com.example.basaya.model.Quiz
import com.example.basaya.model.Activity
import com.example.basaya.model.Game
import com.example.basaya.model.Lecture
import com.example.basaya.model.Question

object MockData {

    val lesson = listOf(

        Lesson(

            id = "1",
            title = "Mga Kulay",
            description = "Learn Bisaya colors",
            difficulty = "Beginner",
            imageRes = R.drawable.sample_lesson,

            lecture = Lecture(
                content = "Pula means red",
                vocabulary = listOf(
                    "Pula",
                    "Asul",
                    "Dalag"
                )
            ),

            activity = Activity(
                type = "Matching",
                instruction = "Match the colors"
            ),

            quiz = Quiz(

                questions = listOf(

                    Question(
                        question = "What is pula?",
                        choices = listOf(
                            "Red",
                            "Blue",
                            "Green",
                            "Yellow"
                        ),
                        answer = "Red"
                    )
                )
            ),

            game = Game(
                type = "Word Hunt",
                title = "Find the Colors"
            )
        ),
        Lesson(

            id = "1",
            title = "Mga Kulay",
            description = "Learn Bisaya colors",
            difficulty = "Beginner",
            imageRes = R.drawable.sample_lesson,

            lecture = Lecture(
                content = "Pula means red",
                vocabulary = listOf(
                    "Pula",
                    "Asul",
                    "Dalag"
                )
            ),

            activity = Activity(
                type = "Matching",
                instruction = "Match the colors"
            ),

            quiz = Quiz(

                questions = listOf(

                    Question(
                        question = "What is pula?",
                        choices = listOf(
                            "Red",
                            "Blue",
                            "Green",
                            "Yellow"
                        ),
                        answer = "Red"
                    )
                )
            ),

            game = Game(
                type = "Word Hunt",
                title = "Find the Colors"
            )
        ),
        Lesson(

            id = "1",
            title = "Mga Kulay",
            description = "Learn Bisaya colors",
            difficulty = "Beginner",
            imageRes = R.drawable.sample_lesson,

            lecture = Lecture(
                content = "Pula means red",
                vocabulary = listOf(
                    "Pula",
                    "Asul",
                    "Dalag"
                )
            ),

            activity = Activity(
                type = "Matching",
                instruction = "Match the colors"
            ),

            quiz = Quiz(

                questions = listOf(

                    Question(
                        question = "What is pula?",
                        choices = listOf(
                            "Red",
                            "Blue",
                            "Green",
                            "Yellow"
                        ),
                        answer = "Red"
                    )
                )
            ),

            game = Game(
                type = "Word Hunt",
                title = "Find the Colors"
            )
        )
    )
}