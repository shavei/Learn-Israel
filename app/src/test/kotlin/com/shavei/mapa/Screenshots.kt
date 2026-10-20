package com.shavei.mapa

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ActivityScenario
import java.io.File

/**
 * Saves the activity's window to app/build/screenshots. captureToImage() waits for a
 * hardware redraw that Robolectric never delivers, so the view tree is drawn into a
 * software bitmap instead.
 */
object Screenshots {
    fun save(activity: Activity, name: String) {
        val dir = File(System.getProperty("screenshotDir") ?: "build/screenshots").apply { mkdirs() }
        val view = activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    fun <A : Activity> save(scenario: ActivityScenario<A>, name: String) = scenario.onActivity { save(it, name) }
}
