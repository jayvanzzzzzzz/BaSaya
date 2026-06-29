package com.example.basaya.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.basaya.data.dao.CrosswordLevelDao
import com.example.basaya.data.dao.GameProgressDao
import com.example.basaya.data.dao.LessonDao
import com.example.basaya.data.entity.CrosswordProgress
import com.example.basaya.data.entity.LessonEntity
import com.example.basaya.data.entity.CrosswordLevelEntity

@Database(
    entities = [
        CrosswordProgress::class,
        LessonEntity::class,
        CrosswordLevelEntity::class
    ],
    version = 2
)
abstract class GameDatabase : RoomDatabase() {

    abstract fun gameProgressDao(): GameProgressDao
    abstract fun lessonDao(): LessonDao
    abstract fun crosswordLevelDao(): CrosswordLevelDao

    companion object {
        @Volatile
        private var INSTANCE: GameDatabase? = null

        fun getDatabase(context: Context): GameDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GameDatabase::class.java,
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