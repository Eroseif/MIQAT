package com.miqat.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/** Generic JSON cache: mosque searches and timetables, so the app works offline. */
@Entity(tableName = "cache")
data class CacheEntry(
    @PrimaryKey val cacheKey: String,
    val json: String,
    val fetchedAt: Long
)

@Dao
interface CacheDao {
    @Query("SELECT * FROM cache WHERE cacheKey = :key")
    suspend fun get(key: String): CacheEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: CacheEntry)
}

@Database(entities = [CacheEntry::class], version = 1, exportSchema = false)
abstract class MiqatDb : RoomDatabase() {
    abstract fun cache(): CacheDao

    companion object {
        fun build(ctx: Context): MiqatDb =
            Room.databaseBuilder(ctx.applicationContext, MiqatDb::class.java, "miqat.db").build()
    }
}
