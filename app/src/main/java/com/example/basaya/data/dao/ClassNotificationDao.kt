package com.example.basaya.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.basaya.data.entity.ClassNotificationEntity

@Dao
interface ClassNotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ClassNotificationEntity>)

    @Query("SELECT * FROM class_notifications WHERE classId = :classId ORDER BY visibleAt DESC")
    suspend fun getForClass(classId: String): List<ClassNotificationEntity>
}
