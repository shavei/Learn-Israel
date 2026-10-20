package com.shavei.mapa

import android.app.Application
import androidx.room.Room
import com.shavei.mapa.content.Content
import com.shavei.mapa.content.ContentParser
import com.shavei.mapa.data.MIGRATION_1_2
import com.shavei.mapa.data.MIGRATION_2_3
import com.shavei.mapa.data.MapaDatabase
import com.shavei.mapa.data.ProgressRepository
import com.shavei.mapa.data.SettingsStore
import com.shavei.mapa.learning.fsrs.FsrsScheduler

/** Manual dependency container. Small enough for Phase 0 that Hilt is not needed yet. */
class MapaApplication : Application() {

    val content: Content by lazy {
        fun read(name: String) = assets.open(name).bufferedReader().use { it.readText() }
        ContentParser.parse(read("map.json"), read("places.json"))
    }

    val scheduler: FsrsScheduler by lazy { FsrsScheduler() }

    val database: MapaDatabase by lazy {
        Room.databaseBuilder(this, MapaDatabase::class.java, "mapa.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }

    val settings: SettingsStore by lazy { SettingsStore(this) }

    val progress: ProgressRepository by lazy {
        ProgressRepository(database.progressDao(), BuildConfigInfo.versionName(this), scheduler)
    }
}

private object BuildConfigInfo {
    fun versionName(app: Application): String =
        app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "dev"
}
