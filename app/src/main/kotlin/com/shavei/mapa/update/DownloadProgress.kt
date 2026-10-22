package com.shavei.mapa.update

import kotlin.math.ceil

/**
 * How far the update's download is: [done] of [size] bytes ([size] 0 when the server did
 * not say), [elapsedMs] since it started. The time left is guessed from the average speed
 * so far, which is steadier than the speed of the last moment.
 */
data class DownloadProgress(val done: Long, val size: Long, val elapsedMs: Long) {
    /** 0..100, null while the size is unknown. */
    val percent: Int? get() = if (size > 0) (done * 100 / size).toInt().coerceIn(0, 100) else null

    /** 0..1, null while the size is unknown. */
    val fraction: Float? get() = if (size > 0) (done.toFloat() / size).coerceIn(0f, 1f) else null

    /**
     * Seconds left, rounded up: under a minute to 5 seconds, then to whole minutes, so the
     * number does not flicker. Null while there is too little to go by.
     */
    val secondsLeft: Long?
        get() {
            if (size <= 0 || done <= 0 || elapsedMs < MIN_ELAPSED_MS) return null
            val left = (size - done).coerceAtLeast(0) * elapsedMs / done / 1000.0
            return when {
                left < 60 -> (ceil(left / 5) * 5).toLong().coerceAtLeast(5)
                else -> (ceil(left / 60) * 60).toLong()
            }
        }

    private companion object {
        /** The first moment's speed says little: a guess waits this long. */
        const val MIN_ELAPSED_MS = 1500L
    }
}
