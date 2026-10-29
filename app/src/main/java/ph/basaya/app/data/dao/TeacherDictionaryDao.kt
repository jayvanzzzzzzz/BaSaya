package ph.basaya.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import ph.basaya.app.data.entity.TeacherDictionaryEntity

@Dao
abstract class TeacherDictionaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(entries: List<TeacherDictionaryEntity>)

    @Query("SELECT * FROM teacher_dictionary WHERE `key` = :key LIMIT 1")
    abstract suspend fun getByKey(key: String): TeacherDictionaryEntity?

    @Query("SELECT * FROM teacher_dictionary")
    abstract suspend fun getAll(): List<TeacherDictionaryEntity>

    @Query("DELETE FROM teacher_dictionary")
    abstract suspend fun clear()

    @Transaction
    open suspend fun replaceAll(entries: List<TeacherDictionaryEntity>) {
        clear()
        if (entries.isNotEmpty()) upsertAll(entries)
    }
}
