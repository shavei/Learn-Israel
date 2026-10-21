package com.shavei.mapa

import android.app.Application
import androidx.room.Room
import com.shavei.mapa.content.Content
import com.shavei.mapa.content.ContentParser
import com.shavei.mapa.data.MIGRATION_1_2
import com.shavei.mapa.data.MIGRATION_2_3
import com.shavei.mapa.data.MIGRATION_3_4
import com.shavei.mapa.data.MIGRATION_4_5
import com.shavei.mapa.data.MIGRATION_5_6
import com.shavei.mapa.data.MapaDatabase
import com.shavei.mapa.data.ProgressRepository
import com.shavei.mapa.data.SettingsStore
import com.shavei.mapa.learning.fsrs.FsrsScheduler
import com.shavei.mapa.reminder.Reminders
import com.shavei.mapa.ui.map.MapTerrain

/** Manual dependency container. Small enough for Phase 0 that Hilt is not needed yet. */
class MapaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Alarms are lost when the app is force-stopped (some phones do that to save
        // battery), so the reminder is set again whenever the app starts.
        Reminders.apply(this, settings.current)
    }

    val content: Content by lazy {
        fun read(name: String) = assets.open(name).bufferedReader().use { it.readText() }
        ContentParser.parse(read("map.json"), read("places.json"))
    }

    val scheduler: FsrsScheduler by lazy { FsrsScheduler() }

    val database: MapaDatabase by lazy {
        Room.databaseBuilder(this, MapaDatabase::class.java, "mapa.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()
    }

    val settings: SettingsStore by lazy { SettingsStore(this) }

    /** The relief map style's terrain, built once and shared by every map. */
    val terrain: MapTerrain by lazy { MapTerrain(assets, content.map) }

    val progress: ProgressRepository by lazy {
        ProgressRepository(database.progressDao(), BuildConfigInfo.versionName(this), scheduler)
    }
}

private object BuildConfigInfo {
    fun versionName(app: Application): String =
        app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "dev"
}
