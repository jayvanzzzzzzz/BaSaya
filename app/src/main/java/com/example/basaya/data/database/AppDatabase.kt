package com.example.basaya.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.basaya.data.dao.ActivityDao
import com.example.basaya.data.dao.CrosswordLevelDao
import com.example.basaya.data.dao.CrosswordProgressDao
import com.example.basaya.data.dao.LectureDao
import com.example.basaya.data.dao.LessonDao
import com.example.basaya.data.dao.QuizDao
import com.example.basaya.data.entity.ActivityEntity
import com.example.basaya.data.entity.CrosswordProgress
import com.example.basaya.data.entity.LectureEntity
import com.example.basaya.data.entity.LessonEntity
import com.example.basaya.data.entity.CrosswordLevelEntity
import com.example.basaya.data.entity.QuizEntity

@Database(
    entities = [
        CrosswordProgress::class,
        LessonEntity::class,
        CrosswordLevelEntity::class,
        LectureEntity::class,
        ActivityEntity::class,
        QuizEntity::class
    ],
    version = 8
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun crosswordProgressDao(): CrosswordProgressDao
    abstract fun lessonDao(): LessonDao
    abstract fun crosswordLevelDao(): CrosswordLevelDao
    abstract fun lectureDao(): LectureDao
    abstract fun activityDao(): ActivityDao
    abstract fun quizDao(): QuizDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "game_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}