package com.shavei.mapa

import android.app.Application
import androidx.room.Room
import com.shavei.mapa.content.Content
import com.shavei.mapa.content.ContentParser
import com.shavei.mapa.data.MapaDatabase
import com.shavei.mapa.data.ProgressRepository
import com.shavei.mapa.learning.fsrs.FsrsScheduler

/** Manual dependency container. Small enough for Phase 0 that Hilt is not needed yet. */
class MapaApplication : Application() {

    val content: Content by lazy {
        fun read(name: String) = assets.open(name).bufferedReader().use { it.readText() }
        ContentParser.parse(read("map.json"), read("places.json"))
    }

    val scheduler: FsrsScheduler by lazy { FsrsScheduler() }

    val progress: ProgressRepository by lazy {
        val db = Room.databaseBuilder(this, MapaDatabase::class.java, "mapa.db").build()
        ProgressRepository(db.progressDao(), BuildConfigInfo.versionName(this))
    }
}

private object BuildConfigInfo {
    fun versionName(app: Application): String =
        app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "dev"
}
