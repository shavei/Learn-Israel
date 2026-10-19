package com.shavei.mapa.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** FSRS memory state of one skill (facet) on one place. */
@Entity(tableName = "cards", primaryKeys = ["placeId", "facet"])
data class CardEntity(
    val placeId: String,
    val facet: String,
    val state: Int,
    val step: Int?,
    val stability: Double?,
    val difficulty: Double?,
    val due: Long,
    val lastReview: Long?,
    val struggles: Int,
)

/** One answered question, kept for stats, parameter fitting and research. */
@Entity(tableName = "reviews")
data class ReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeId: String,
    val facet: String,
    /** The question type: LOCATE, NAME_CHOICE, NAME_TYPED or RELATE. */
    val mode: String,
    val sessionId: String,
    val timestamp: Long,
    val rating: Int,
    val errorMeters: Double?,
    val tapX: Double?,
    val tapY: Double?,
    /** The chosen place id or the typed text. */
    val answer: String?,
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

@Database(entities = [CardEntity::class, ReviewEntity::class], version = 2, exportSchema = false)
abstract class MapaDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
}

/** Phase 0 tracked one skill per place; everything it stored becomes the LOCATE skill. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `cards_new` (`placeId` TEXT NOT NULL, `facet` TEXT NOT NULL, `state` INTEGER NOT NULL, " +
                "`step` INTEGER, `stability` REAL, `difficulty` REAL, `due` INTEGER NOT NULL, `lastReview` INTEGER, " +
                "`struggles` INTEGER NOT NULL, PRIMARY KEY(`placeId`, `facet`))",
        )
        db.execSQL(
            "INSERT INTO `cards_new` SELECT placeId, 'LOCATE', state, step, stability, difficulty, due, lastReview, 0 FROM `cards`",
        )
        db.execSQL("DROP TABLE `cards`")
        db.execSQL("ALTER TABLE `cards_new` RENAME TO `cards`")

        db.execSQL(
            "CREATE TABLE `reviews_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `placeId` TEXT NOT NULL, " +
                "`facet` TEXT NOT NULL, `mode` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, " +
                "`rating` INTEGER NOT NULL, `errorMeters` REAL, `tapX` REAL, `tapY` REAL, `answer` TEXT, " +
                "`responseMs` INTEGER NOT NULL, `appVersion` TEXT NOT NULL)",
        )
        db.execSQL(
            "INSERT INTO `reviews_new` SELECT id, placeId, 'LOCATE', 'LOCATE', sessionId, timestamp, rating, " +
                "errorMeters, tapX, tapY, NULL, responseMs, appVersion FROM `reviews`",
        )
        db.execSQL("DROP TABLE `reviews`")
        db.execSQL("ALTER TABLE `reviews_new` RENAME TO `reviews`")
    }
}
