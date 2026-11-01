package com.miqat.app.zakat

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
import kotlinx.coroutines.flow.Flow

/** The user's wealth entries (kept as typed text) and hawl start date. Stored only on this phone. */
@Entity(tableName = "zakat_profile")
data class ZakatProfile(
    @PrimaryKey val id: Int = 1,
    val cash: String = "",
    val bank: String = "",
    val home: String = "",
    val goldGrams: String = "",
    val silverGrams: String = "",
    val inventory: String = "",
    val receivable: String = "",
    val other: String = "",
    val debts: String = "",
    val jewelleryGrams: String = "",
    /** "" = not chosen yet, "ADORN" = personal adornment, "INVEST" = savings / for sale or hire. */
    val jewelleryUse: String = "",
    val hawlStartEpochDay: Long? = null,
    val reminder: Boolean = false
)

@Entity(tableName = "zakat_history")
data class ZakatHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val savedAt: Long,
    val currency: String,
    val goldPerGram: String,
    val nisabGrams: String,
    val nisab: String,
    val totalAssets: String,
    val deductions: String,
    val net: String,
    val nisabReached: Boolean,
    val rate: String,
    val zakat: String
)

@Dao
interface ZakatDao {
    @Query("SELECT * FROM zakat_profile WHERE id = 1")
    suspend fun profile(): ZakatProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(p: ZakatProfile)

    @Query("SELECT * FROM zakat_history ORDER BY savedAt DESC")
    fun history(): Flow<List<ZakatHistory>>

    @Insert
    suspend fun addHistory(h: ZakatHistory): Long

    @Query("DELETE FROM zakat_history WHERE id = :id")
    suspend fun deleteHistory(id: Long)
}

// Its own database file, so price caching never touches the user's financial entries.
@Database(entities = [ZakatProfile::class, ZakatHistory::class], version = 1, exportSchema = false)
abstract class ZakatDb : RoomDatabase() {
    abstract fun dao(): ZakatDao

    companion object {
        fun build(ctx: Context): ZakatDb =
            Room.databaseBuilder(ctx.applicationContext, ZakatDb::class.java, "miqat_zakat.db").build()
    }
}
