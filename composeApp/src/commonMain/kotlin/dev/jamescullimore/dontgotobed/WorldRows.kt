package dev.jamescullimore.dontgotobed

import kotlin.math.floor

/** The same discrete row rule is used for visibility, combat, and collisions. */
object WorldRows {
    fun index(coordinatePx: Float, unitPx: Float, rowCount: Int): Int {
        val cell = floor(coordinatePx / unitPx).toInt()
        return ((cell % rowCount) + rowCount) % rowCount
    }

    fun same(a: Float, b: Float, unitPx: Float, rowCount: Int): Boolean =
        index(a, unitPx, rowCount) == index(b, unitPx, rowCount)
    fun previewAlpha(other: Float, player: Float, unitPx: Float, rowCount: Int, previewRows: Int): Float {
        val distance = kotlin.math.abs(index(other, unitPx, rowCount) - index(player, unitPx, rowCount))
        val wrappedDistance = minOf(distance, rowCount - distance)
        return when {
            wrappedDistance == 0 -> 1f
            wrappedDistance > previewRows.coerceIn(0, 2) -> 0f
            wrappedDistance == 1 -> 0.18f
            else -> 0.08f
        }
    }
}
