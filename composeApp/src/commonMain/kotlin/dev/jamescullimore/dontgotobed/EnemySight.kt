package dev.jamescullimore.dontgotobed

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign

/** Voxel ray cast through latitude, longitude and height, taking the shortest wrapped route. */
internal object EnemySight {
    fun visible(map: TileMap, unit: Float, x: Float, other: Float, bottom: Float,
                playerX: Float, playerOther: Float, playerBottom: Float, range: Float): Boolean {
        val alongSpan = if (map.activeAxis == WorldAxis.Latitude) map.width else map.depth
        val otherSpan = if (map.activeAxis == WorldAxis.Latitude) map.depth else map.width
        fun delta(from: Float, to: Float, span: Int): Float =
            ((to - from) % span + span * 1.5f) % span - span * 0.5f
        val sx = x / unit + 1f
        val sz = other / unit + 0.5f
        val sy = bottom / unit + 2.5f
        val dx = delta(sx, playerX / unit + 1f, alongSpan)
        val dz = delta(sz, playerOther / unit + 0.5f, otherSpan)
        val dy = (playerBottom - bottom) / unit
        if (dx * dx + dz * dz + dy * dy > range * range) return false
        var cx = floor(sx).toInt()
        var cz = floor(sz).toInt()
        var cy = floor(sy).toInt()
        fun first(start: Float, cell: Int, direction: Float): Float = when {
            direction > 0 -> (cell + 1 - start) / direction
            direction < 0 -> (start - cell) / -direction
            else -> Float.POSITIVE_INFINITY
        }
        var tx = first(sx, cx, dx)
        var tz = first(sz, cz, dz)
        var ty = first(sy, cy, dy)
        while (true) {
            val lat = if (map.activeAxis == WorldAxis.Latitude) cx else cz
            val lon = if (map.activeAxis == WorldAxis.Latitude) cz else cx
            if (map.getVoxel(lat, lon, cy)) return false
            val next = minOf(tx, tz, ty)
            if (next >= 1f) return true
            if (tx <= next) { cx += sign(dx).toInt(); tx += 1f / abs(dx) }
            if (tz <= next) { cz += sign(dz).toInt(); tz += 1f / abs(dz) }
            if (ty <= next) { cy += sign(dy).toInt(); ty += 1f / abs(dy) }
        }
    }
}

/** Sight is refreshed at most ten times per second per enemy, or immediately after terrain/axis changes. */
internal class EnemySightTracker {
    private var nextCheck = 0L
    private var axis: WorldAxis? = null
    private var revision = -1
    private var visible = false
    fun canSee(now: Long, map: TileMap, unit: Float, x: Float, other: Float, bottom: Float,
               playerX: Float, playerOther: Float, playerBottom: Float, range: Float): Boolean {
        if (now >= nextCheck || axis != map.activeAxis || revision != map.projectileRevision) {
            visible = EnemySight.visible(map, unit, x, other, bottom, playerX, playerOther, playerBottom, range)
            nextCheck = now + 100L
            axis = map.activeAxis
            revision = map.projectileRevision
        }
        return visible
    }
}
