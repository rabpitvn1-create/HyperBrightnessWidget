package com.hyperbrightness.widget

object BrightnessLevels {
    val RAW_LEVELS: IntArray = intArrayOf(11, 17, 26, 38, 49)

    const val MIN_RAW: Int = 11
    const val MAX_RAW: Int = 55

    fun clamp(raw: Int): Int = raw.coerceIn(MIN_RAW, MAX_RAW)

    fun nearest(raw: Int): Int = RAW_LEVELS.minBy { kotlin.math.abs(it - raw) }

    fun nextAfter(currentRaw: Int): Int {
        val current = nearest(currentRaw)
        return RAW_LEVELS.firstOrNull { it > current } ?: RAW_LEVELS.first()
    }
}
