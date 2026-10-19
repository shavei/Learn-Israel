package com.shavei.mapa.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

/** FSRS memory state of one place (Phase 0 tracks only the LOCATE skill). */
@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val placeId: String,
    val state: Int,
    val step: Int?,
    val stability: Double?,
    val difficulty: Double?,
    val due: Long,
    val lastReview: Long?,
)

/** One answered question, kept for stats, parameter fitting and research. */
@Entity(tableName = "reviews")
data class ReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeId: String,
    val sessionId: String,
    val timestamp: Long,
    val rating: Int,
    val errorMeters: Double,
    val tapX: Double,
    val tapY: Double,
    val responseMs: Long,
    val appVersion: String,
)

@Dao
interface ProgressDao {
    @Query("SELECT * FROM cards")
    suspend fun cards(): List<CardEntity>

    @Upsert
    suspend fun upsert(card: CardEntity)

    @Insert
    suspend fun insert(review: ReviewEntity)

    /** Places whose very first review happened at or after [since]. */
    @Query(
        """SELECT COUNT(*) FROM (SELECT placeId FROM reviews GROUP BY placeId HAVING MIN(timestamp) >= :since)""",
    )
    suspend fun countIntroducedSince(since: Long): Int
}

@Database(entities = [CardEntity::class, ReviewEntity::class], version = 1, exportSchema = false)
abstract class MapaDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
}
