package dev.jamescullimore.dontgotobed

import kotlin.math.abs
import kotlin.math.floor

internal object NightPursuit {
    private fun shortestDx(from: Float, to: Float, span: Float): Float =
        ((to - from) % span + span * 1.5f) % span - span * 0.5f

    private fun columns(left: Float, unit: Float, width: Float) =
        floor(left / unit).toInt()..floor((left + width - 0.001f) / unit).toInt()
    // Two-cell-wide characters enter with four clear cells between their bodies.
    const val ENTRY_DISTANCE_TILES = 6f

    /** Join the player's row at a safe horizontal offset, then chase along that row. */
    fun advance(x: Float, other: Float, bottom: Float, map: TileMap,
                             unit: Float, dt: Float, playerWorldXPx: Float, playerOtherAxisPx: Float): Triple<Float, Float, Float> {
        val span = map.width * unit
        val rawDx = shortestDx(x, playerWorldXPx, span)
        val sameRow = WorldRows.same(other, playerOtherAxisPx, unit, map.width)
        val entryDistance = ENTRY_DISTANCE_TILES * unit
        val tooCloseToEnter = abs(rawDx) < entryDistance - unit * 0.01f
        val dx = when {
            sameRow -> kotlin.math.sign(rawDx) * (abs(rawDx) - unit * 2).coerceAtLeast(0f)
            tooCloseToEnter -> (if (rawDx >= 0f) -1f else 1f) * (entryDistance - abs(rawDx))
            else -> 0f
        }
        val dz = if (!sameRow && !tooCloseToEnter) shortestDx(other, playerOtherAxisPx, span) else 0f
        val distance = kotlin.math.sqrt(dx * dx + dz * dz)
        if (distance <= 0.01f) return Triple(x, other, bottom)
        val step = minOf(distance, unit * 3 * dt)
        val nx = ((x + dx / distance * step) % span + span) % span
        val nz = ((other + dz / distance * step) % span + span) % span
        var floor = 0f
        for (c in columns(nx, unit, 2 * unit)) {
            for (o in columns(nz, unit, unit)) {
                val lat = if (map.activeAxis == WorldAxis.Latitude) c else o
                val lon = if (map.activeAxis == WorldAxis.Latitude) o else c
                for (r in map.height - 1 downTo 0) if (map.getVoxel(lat, lon, r)) {
                    floor = maxOf(floor, (r + 1) * unit)
                    break
                }
            }
        }
        // Climb exposed faces visibly, instead of moving through buildings.
        return if (floor > bottom + unit * 0.1f) Triple(x, other, minOf(floor, bottom + unit * 8 * dt))
            else Triple(nx, nz, maxOf(bottom, floor))
    }

}
