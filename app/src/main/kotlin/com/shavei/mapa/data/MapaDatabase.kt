package com.shavei.mapa.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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
    /**
     * The question type: LOCATE, BUILD (Map Builder), TRACE (Trace It), NAME_CHOICE,
     * NAME_TYPED, RELATE, BLITZ and ROAD_TRIP (free play, Pin It at a reduced weight), or
     * PLACEMENT and MAP_TEST for the two tests, which are not practice.
     */
    val mode: String,
    val sessionId: String,
    val timestamp: Long,
    val rating: Int,
    val errorMeters: Double?,
    /** Where the player tapped; for Map Builder, where the shape's center was dropped. */
    val tapX: Double?,
    val tapY: Double?,
    /** The chosen place id, the typed text, or the path drawn in Trace It ("x,y;x,y", meters). */
    val answer: String?,
    val responseMs: Long,
    val appVersion: String,
    // The fields below arrived in version 3; answers logged before then have them null.
    /** The card's FSRS state before this answer, null for a new card. */
    val stateBefore: Int? = null,
    /** When the card was due before this answer: earlier than [timestamp] means a due review. */
    val dueBefore: Long? = null,
    /** Days since the card's last review, null for a new card. */
    val elapsedDays: Double? = null,
    /** The scheduler's recall probability at the time of the answer, null for a new card. */
    val predictedRecall: Double? = null,
    @ColumnInfo(defaultValue = "0") val hint: Boolean = false,
    /** For a missed tap: the known place it was nearest to. */
    val tappedNear: String? = null,
)

/** One finished pre/post map test (docs/08-measurement.md); its answers are in reviews. */
@Entity(tableName = "map_tests")
data class MapTestEntity(
    @PrimaryKey val sessionId: String,
    /** PRE or POST. */
    val kind: String,
    val finishedAt: Long,
    val found: Int,
    val total: Int,
    val medianErrorMeters: Double,
)

/** An achievement and when it was first earned; once earned, it is kept. */
@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val id: String,
    val unlockedAt: Long,
)

@Dao
interface ProgressDao {
    @Query("SELECT * FROM cards")
    suspend fun cards(): List<CardEntity>

    @Upsert
    suspend fun upsert(card: CardEntity)

    @Insert
    suspend fun insert(review: ReviewEntity)

    /**
     * Places introduced at or after [since]: first practiced then, outside the placement
     * test. A place the placement test found was never introduced; one it missed is
     * introduced when it is first taught.
     */
    @Query(
        """SELECT COUNT(*) FROM (SELECT placeId FROM reviews GROUP BY placeId
            HAVING MIN(CASE WHEN mode NOT IN ('PLACEMENT', 'MAP_TEST') THEN timestamp END) >= :since
            AND MAX(mode = 'PLACEMENT' AND rating >= 3) = 0)""",
    )
    suspend fun countIntroducedSince(since: Long): Int

    /** When every practice answer was given (the placement and map tests are not practice). */
    @Query("SELECT timestamp FROM reviews WHERE mode NOT IN ('PLACEMENT', 'MAP_TEST') ORDER BY timestamp")
    suspend fun practiceTimes(): List<Long>

    @Query("SELECT MAX(timestamp) FROM reviews WHERE mode NOT IN ('PLACEMENT', 'MAP_TEST')")
    suspend fun lastPracticeTime(): Long?

    /** Any answer with feedback, the placement test included: after one, a map test is no longer a pre-test. */
    @Query("SELECT EXISTS(SELECT 1 FROM reviews WHERE mode != 'MAP_TEST')")
    suspend fun hasAnsweredWithFeedback(): Boolean

    @Query("SELECT * FROM reviews ORDER BY timestamp")
    suspend fun reviews(): List<ReviewEntity>

    @Insert
    suspend fun insert(test: MapTestEntity)

    @Query("SELECT * FROM map_tests ORDER BY finishedAt")
    suspend fun mapTests(): List<MapTestEntity>

    /** Distance errors of Pin It answers, for the history of average error. */
    @Query("SELECT placeId, timestamp, errorMeters FROM reviews WHERE mode = 'LOCATE' AND errorMeters IS NOT NULL ORDER BY timestamp")
    suspend fun locateErrors(): List<LocateError>

    /** Missed taps by the place asked and the place the tap was nearest to. */
    @Query("SELECT placeId AS asked, tappedNear AS tapped, COUNT(*) AS count FROM reviews WHERE tappedNear IS NOT NULL GROUP BY placeId, tappedNear")
    suspend fun confusions(): List<ConfusionRow>

    @Query("SELECT * FROM achievements ORDER BY unlockedAt")
    suspend fun achievements(): List<AchievementEntity>

    /** Keeps the first unlock time of an achievement already stored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(achievement: AchievementEntity)
}

data class LocateError(val placeId: String, val timestamp: Long, val errorMeters: Double)

data class ConfusionRow(val asked: String, val tapped: String, val count: Int)

@Database(
    entities = [CardEntity::class, ReviewEntity::class, MapTestEntity::class, AchievementEntity::class],
    version = 4,
    exportSchema = false,
)
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

/** Phase 1d: what the learning stats need on every answer, and the map test results. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `reviews` ADD COLUMN `stateBefore` INTEGER")
        db.execSQL("ALTER TABLE `reviews` ADD COLUMN `dueBefore` INTEGER")
        db.execSQL("ALTER TABLE `reviews` ADD COLUMN `elapsedDays` REAL")
        db.execSQL("ALTER TABLE `reviews` ADD COLUMN `predictedRecall` REAL")
        db.execSQL("ALTER TABLE `reviews` ADD COLUMN `hint` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `reviews` ADD COLUMN `tappedNear` TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `map_tests` (`sessionId` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                "`finishedAt` INTEGER NOT NULL, `found` INTEGER NOT NULL, `total` INTEGER NOT NULL, " +
                "`medianErrorMeters` REAL NOT NULL, PRIMARY KEY(`sessionId`))",
        )
    }
}

/** Phase 2d: achievements, kept once earned. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `achievements` (`id` TEXT NOT NULL, `unlockedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}
