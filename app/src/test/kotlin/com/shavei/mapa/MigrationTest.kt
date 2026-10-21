package com.shavei.mapa

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shavei.mapa.data.MIGRATION_1_2
import com.shavei.mapa.data.MIGRATION_2_3
import com.shavei.mapa.data.MIGRATION_3_4
import com.shavei.mapa.data.MIGRATION_4_5
import com.shavei.mapa.data.MapaDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Phase 0 players keep their progress: the v1 database opens as v5 with LOCATE cards. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    @Test
    fun phase0ProgressBecomesLocateSkill() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.getDatabasePath("migration-test.db").apply { parentFile?.mkdirs(); delete() }

        // Exactly the schema Room created for version 1.
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `cards` (`placeId` TEXT NOT NULL, `state` INTEGER NOT NULL, `step` INTEGER, " +
                    "`stability` REAL, `difficulty` REAL, `due` INTEGER NOT NULL, `lastReview` INTEGER, PRIMARY KEY(`placeId`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `reviews` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `placeId` TEXT NOT NULL, " +
                    "`sessionId` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `rating` INTEGER NOT NULL, `errorMeters` REAL NOT NULL, " +
                    "`tapX` REAL NOT NULL, `tapY` REAL NOT NULL, `responseMs` INTEGER NOT NULL, `appVersion` TEXT NOT NULL)",
            )
            db.execSQL("INSERT INTO cards VALUES ('city.haifa', 2, NULL, 12.5, 4.0, 1000, 500)")
            db.execSQL("INSERT INTO reviews VALUES (1, 'city.haifa', 's1', 500, 3, 1200.0, 1.0, 2.0, 3000, '0.1.0')")
            db.version = 1
        }

        val room = Room.databaseBuilder(context, MapaDatabase::class.java, "migration-test.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).allowMainThreadQueries().build()
        val cards = room.progressDao().cards()
        assertEquals(1, cards.size)
        with(cards.single()) {
            assertEquals("city.haifa", placeId)
            assertEquals("LOCATE", facet)
            assertEquals(12.5, stability!!, 0.0)
            assertEquals(0, struggles)
        }
        assertEquals(1, room.progressDao().countIntroducedSince(0))
        // Version 3 adds the learning stats fields, empty for answers logged before.
        with(room.progressDao().reviews().single()) {
            assertEquals("LOCATE", mode)
            assertNull(stateBefore)
            assertNull(predictedRecall)
            assertFalse(hint)
        }
        assertTrue(room.progressDao().mapTests().isEmpty())
        // Version 4 adds the achievements, none earned yet.
        assertTrue(room.progressDao().achievements().isEmpty())
        // Version 5 adds the blitz rounds, none played yet.
        assertTrue(room.progressDao().blitzRounds().isEmpty())
        room.close()
    }
}
