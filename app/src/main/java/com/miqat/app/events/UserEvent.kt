package com.miqat.app.events

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** A birthday or special day added by the user. Stored only on this phone. */
@Entity(tableName = "events")
data class UserEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val title: String,
    val description: String = "",
    val yearly: Boolean = false
)

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY dateEpochDay")
    fun all(): Flow<List<UserEvent>>

    @Insert
    suspend fun insert(e: UserEvent): Long

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun delete(id: Long)
}

// Separate database file from the cache, so saved events are never touched by cache changes.
@Database(entities = [UserEvent::class], version = 1, exportSchema = false)
abstract class EventsDb : RoomDatabase() {
    abstract fun events(): EventDao

    companion object {
        fun build(ctx: Context): EventsDb =
            Room.databaseBuilder(ctx.applicationContext, EventsDb::class.java, "miqat_events.db").build()
    }
}
