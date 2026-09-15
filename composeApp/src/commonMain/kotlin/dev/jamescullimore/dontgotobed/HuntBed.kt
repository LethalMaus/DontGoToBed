package dev.jamescullimore.dontgotobed

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.random.Random

data class HuntBed(val id: Long, val latitude: Int, val longitude: Int, val row: Int, val health: Int = 42) {
    fun contains(map: TileMap, lat: Int, lon: Int, y: Int): Boolean =
        map.wrap(lat - latitude) < 3 && map.wrap(lon - longitude, map.depth) < 3 && y in row..row + 1

    fun onTop(map: TileMap, lat: Float, lon: Float, bottom: Float, axis: WorldAxis): Boolean {
        if (abs(bottom - (row + 2)) > 0.12f) return false
        fun overlaps(start: Float, width: Float, anchor: Int, span: Int): Boolean {
            val offset = ((start - anchor) % span + span) % span
            return offset < 3f || offset + width > span
        }
        return overlaps(lat, if (axis == WorldAxis.Latitude) 2f else 1f, latitude, map.width) &&
            overlaps(lon, if (axis == WorldAxis.Longitude) 2f else 1f, longitude, map.depth)
    }
}

/** Owns only the six horizontal slabs making up this bed, never neighbouring terrain. */
class HuntBedVoxels(private val map: TileMap) {
    private var installed: HuntBed? = null
    private var ids = emptyList<Int>()
    fun set(bed: HuntBed?) {
        if (bed == installed) return
        ids.forEach(map::removeBlock)
        ids = emptyList()
        installed = bed
        if (bed == null) return
        val axis = map.activeAxis
        val slice = map.activeSlice
        val created = mutableListOf<Int>()
        for (depth in 0..2) {
            map.setActiveSlice(WorldAxis.Latitude, bed.latitude, bed.longitude + depth)
            for (layer in 0..1) {
                val id = map.placeBlock(bed.latitude, bed.row + layer, 3, 1,
                    if (layer == 0) BlockMaterial.Wood.color else Color.White,
                    health = bed.health, material = BlockMaterial.Wood, shape = BlockShape.Platform, managed = true)
                if (id >= 0) created += id
            }
        }
        map.setActiveSlice(axis, slice, slice)
        ids = created
    }
}

object HuntBedPlacement {
    fun find(map: TileMap, forbidden: List<WorldLocation>, random: Random = Random.Default): HuntBed? {
        // Bound each attempt so crowded large maps cannot stall the round clock.
        val start = random.nextInt(map.width * map.depth)
        for (offset in 0 until minOf(1024, map.width * map.depth)) {
            val index = (start + offset) % (map.width * map.depth)
            val lat = index % map.width
            val lon = index / map.width
            if (forbidden.any { map.wrap(it.latitude - lat + 2) < 7 && map.wrap(it.longitude - lon + 2, map.depth) < 7 }) continue
            var floor = -1
            for (y in map.height - 4 downTo 0) if (map.getVoxel(lat, lon, y)) { floor = y; break }
            if (floor < 0) continue
            val row = floor + 1
            var clear = true
            for (x in 0..2) for (z in 0..2) {
                if (!map.getVoxel(lat + x, lon + z, row - 1)) clear = false
                for (y in row..row + 2) if (map.getVoxel(lat + x, lon + z, y)) clear = false
            }
            if (clear) return HuntBed(nanoTime(), lat, lon, row)
        }
        return null
    }
}

data class BedLoot(val id: Long, val type: ItemType, val count: Int, val latitude: Int, val longitude: Int, val row: Int)
