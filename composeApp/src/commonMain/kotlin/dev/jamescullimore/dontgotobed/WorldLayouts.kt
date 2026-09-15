package dev.jamescullimore.dontgotobed

import kotlin.random.Random

/** Stable coordinates; changing the camera never changes this object's location. */
data class WorldLocation(val latitude: Int, val longitude: Int, val row: Int) {
    fun along(axis: WorldAxis) = if (axis == WorldAxis.Latitude) latitude else longitude
    fun across(axis: WorldAxis) = if (axis == WorldAxis.Latitude) longitude else latitude
    fun intersects(axis: WorldAxis, slice: Int, size: Int, footprint: Int = 1): Boolean =
        (0 until footprint).any { (across(axis) + it) % size == slice }

    /** Swept top collision in cell units: works even if a fall crosses several cells in one frame. */
    fun landingTop(previousBottom: Float, nextBottom: Float, columns: IntRange,
                   axis: WorldAxis, slice: Int, size: Int): Float? {
        if (!intersects(axis, slice, size, 2)) return null
        val top = row + 3f
        if (previousBottom < top - 0.001f || nextBottom > top || nextBottom > previousBottom) return null
        val overlap = columns.any {
            val col = ((it % size) + size) % size
            col == along(axis) || col == (along(axis) + 1) % size
        }
        return if (overlap) top else null
    }
}

object WorldLayouts {
    fun name(size: Int) = when (size) {
        192 -> "Meadow Village"
        384 -> "Woodland Bridges"
        else -> "Stonekeep Gardens"
    }

    /** Authored districts, repeated on a fixed grid with broad, clear paths between them. */
    fun populate(map: TileMap) {
        map.seedGroundBand()
        fun volume(x: Int, z: Int, y: Int, w: Int, d: Int, h: Int, material: BlockMaterial) {
            for (slice in z until z + d) {
                map.setActiveSlice(WorldAxis.Latitude, x, slice)
                check(map.placeBlock(x, y, w, h, material.color, material.durability, material) >= 0)
            }
        }
        fun house(x: Int, z: Int) {
            // Two open doorways, a four-cell-high room and a walkable roof.
            volume(x, z, 3, 1, 9, 4, BlockMaterial.Wood)
            volume(x + 8, z, 3, 1, 9, 4, BlockMaterial.Wood)
            for (dz in listOf(0, 8)) {
                volume(x + 1, z + dz, 3, 2, 1, 4, BlockMaterial.Wood)
                volume(x + 6, z + dz, 3, 2, 1, 4, BlockMaterial.Wood)
            }
            volume(x, z, 7, 9, 9, 1, BlockMaterial.Wood)
        }
        fun bridge(x: Int, z: Int) {
            for (step in 0..3) {
                volume(x + step, z, 3, 1, 5, step + 1, BlockMaterial.Stone)
                volume(x + 13 - step, z, 3, 1, 5, step + 1, BlockMaterial.Stone)
            }
            volume(x + 4, z, 6, 6, 5, 1, BlockMaterial.Wood)
        }
        fun garden(x: Int, z: Int) {
            for (dx in 0..8 step 4) for (dz in 0..8 step 4) {
                volume(x + dx, z + dz, 3, 2, 2, 1, BlockMaterial.Grass)
            }
            volume(x + 4, z + 4, 4, 2, 2, 2, BlockMaterial.Wood)
            volume(x + 3, z + 3, 6, 4, 4, 1, BlockMaterial.Grass)
        }
        fun terraces(x: Int, z: Int) {
            for (step in 0..3) {
                volume(x + step, z + step, 3 + step, 10 - step * 2, 10 - step * 2, 1, BlockMaterial.Stone)
            }
        }
        fun tree(x: Int, z: Int) {
            volume(x + 1, z + 1, 3, 1, 1, 4, BlockMaterial.Wood)
            volume(x, z, 7, 3, 3, 2, BlockMaterial.Grass)
            volume(x + 1, z + 1, 9, 1, 1, 1, BlockMaterial.Grass)
        }
        fun mountain(x: Int, z: Int) {
            // One-cell terraces let players climb the mountain from either axis.
            for (level in 0..5) volume(x + level, z + level, 3 + level,
                12 - 2 * level, 12 - 2 * level, 1, BlockMaterial.Stone)
        }
        for (z in 0 until map.depth step 48) for (x in 0 until map.width step 48) {
            val district = (x / 48 + z / 48 + map.width / 192) % 4
            when (district) {
                0 -> { house(x + 8, z + 8); garden(x + 27, z + 27) }
                1 -> { bridge(x + 8, z + 10); garden(x + 28, z + 28) }
                2 -> { garden(x + 8, z + 8); terraces(x + 28, z + 28) }
                else -> { terraces(x + 8, z + 8); house(x + 28, z + 28) }
            }
            // Populate the approach paths too; keep four cells clear at district boundaries.
            tree(x + 4, z + 19)
            tree(x + 19, z + 39)
            tree(x + 40, z + 16)
            mountain(x + 5, z + 33)
            volume(x + 39, z + 39, 3, 3, 3, 1, BlockMaterial.Stone)
            // Larger maps have extra landmarks but retain a clear district perimeter.
            if (map.width >= 384) bridge(x + 24, z + 5)
            if (map.width >= 576) volume(x + 4, z + 30, 3, 2, 2, 3, BlockMaterial.Stone)
        }
        map.setActiveSlice(WorldAxis.Latitude, 0, 0)
    }

    fun randomMammy(map: TileMap, random: Random = Random.Default, excluding: WorldLocation? = null): WorldLocation {
        // Sample uniformly from all clear, grounded 2x2 footprints, not from a fixed landmark.
        repeat(10000) {
            val lat = random.nextInt(map.width)
            val lon = random.nextInt(map.depth)
            if (excluding != null && lat == excluding.latitude && lon == excluding.longitude) return@repeat
            if ((0..1).all { dx -> (0..1).all { dz ->
                map.getVoxel(lat + dx, lon + dz, 2) &&
                    (3..5).none { y -> map.getVoxel(lat + dx, lon + dz, y) }
            } }) return WorldLocation(lat, lon, 3)
        }
        error("Map has no free Mammy spawn")
    }
}
