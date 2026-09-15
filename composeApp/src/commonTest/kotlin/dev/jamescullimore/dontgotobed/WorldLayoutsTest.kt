package dev.jamescullimore.dontgotobed

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorldLayoutsTest {
    @Test fun mammyCatchesFastFallsButOnlyAtHerActualLocation() {
        val p = WorldLocation(25, 80, 3)
        assertEquals(6f, p.landingTop(10f, 2f, 25..26, WorldAxis.Latitude, 80, 192))
        assertEquals(6f, p.landingTop(6f, 5.99f, 80..81, WorldAxis.Longitude, 25, 192))
        assertEquals(null, p.landingTop(10f, 2f, 25..26, WorldAxis.Latitude, 0, 192))
        assertEquals(null, p.landingTop(10f, 2f, 28..29, WorldAxis.Latitude, 80, 192))
        assertEquals(null, p.landingTop(2f, 7f, 25..26, WorldAxis.Latitude, 80, 192))
        val wrapped = WorldLocation(191, 80, 3)
        assertEquals(6f, wrapped.landingTop(7f, 5f, 0..1, WorldAxis.Latitude, 80, 192))
    }
    @Test fun everyMapHasContinuousWrappedFloorAndSafeRandomSpawns() {
        for (size in listOf(192, 384, 576)) {
            val map = TileMap(size, 54)
            WorldLayouts.populate(map)
            for (z in 0 until size) for (x in 0 until size) {
                assertTrue(map.getVoxel(x, z, 0), "Missing floor: $size/$x/$z")
            }
            for (axis in WorldAxis.entries) {
                map.setActiveSlice(axis, size - 1, size - 1)
                assertTrue(map.get(-1, 0))
                assertTrue(map.get(size, 0))
                assertTrue(map.get(size + 20, 0))
                val floor = map.snapshotRenderableBlocks().filter { it.row == 0 }
                assertTrue(floor.sumOf { it.w } >= size)
            }
            val random = Random(123)
            val spawns = (0 until 40).map { WorldLayouts.randomMammy(map, random) }
            assertTrue(spawns.toSet().size > 30)
            for (p in spawns) for (dx in 0..1) for (dz in 0..1) {
                assertTrue(map.getVoxel(p.latitude + dx, p.longitude + dz, 2))
                for (y in 3..5) assertFalse(map.getVoxel(p.latitude + dx, p.longitude + dz, y))
            }
        }
    }

    @Test fun layoutsAreDeterministicButDifferentForEachSize() {
        val first = TileMap(192, 12).also(WorldLayouts::populate)
        val second = TileMap(192, 12).also(WorldLayouts::populate)
        for (z in 0 until 48) for (x in 0 until 48) for (y in 3..8) {
            assertEquals(first.getVoxel(x, z, y), second.getVoxel(x, z, y))
        }
        assertEquals(3, listOf(192, 384, 576).map(WorldLayouts::name).toSet().size)
        // Small-map bridge, medium-map tree garden, large-map terraces.
        assertTrue(first.getVoxel(8, 10, 3))
        assertFalse(first.getVoxel(8, 8, 3))
    }

    @Test fun mammyIsOneLocationAndOnlyIntersectsHerActualPlanes() {
        val p = WorldLocation(25, 80, 3)
        assertEquals(25, p.along(WorldAxis.Latitude))
        assertEquals(80, p.along(WorldAxis.Longitude))
        assertTrue(p.intersects(WorldAxis.Latitude, 80, 192, 2))
        assertTrue(p.intersects(WorldAxis.Longitude, 26, 192, 2))
        assertFalse(p.intersects(WorldAxis.Latitude, 25, 192, 2))
        assertFalse(p.intersects(WorldAxis.Longitude, 80, 192, 2))
        assertTrue(WorldLocation(191, 191, 3).intersects(WorldAxis.Longitude, 0, 192, 2))
    }

    @Test fun collisionViewsKeepTheirOwnPlaneAndShareDestruction() {
        val map = TileMap(24, 12)
        map.setActiveSlice(WorldAxis.Latitude, 0, 5)
        map.placePiece(23, 3, BlockMaterial.Stone, BlockShape.RampLeft)
        val view = map.collisionView()
        view.setActiveSlice(WorldAxis.Latitude, 0, 5)
        map.setActiveSlice(WorldAxis.Longitude, 0, 20)
        assertTrue(view.get(23, 3))
        assertTrue(view.hasPieceAt(23, 5)) // Empty half of a wrapped ramp.
        view.damagePieceAt(23, 5, 100)
        for (x in 23..25) for (y in 3..5) assertFalse(view.get(x, y))
        assertFalse(map.getVoxel(0, 5, 4))
        assertFalse(view.hasPieceAt(23, 5))
    }

    @Test fun mammyRelocatesAndNewLandmarksRemainClimbable() {
        val map = TileMap(192, 12).also(WorldLayouts::populate)
        val previous = WorldLayouts.randomMammy(map, Random(42))
        val next = WorldLayouts.randomMammy(map, Random(42), previous)
        assertTrue(previous != next)
        // Stair-stepped mountain and full-height tree, with clear district paths.
        assertTrue(map.getVoxel(5, 33, 3))
        assertTrue(map.getVoxel(10, 38, 8))
        assertTrue(map.getVoxel(5, 20, 9))
        for (x in 0 until map.width) assertFalse(map.getVoxel(x, 0, 3))
    }

    @Test fun breakingOneVoxelRemovesTheSameBlockFromBothViews() {
        val map = TileMap(192, 12)
        map.setActiveSlice(WorldAxis.Latitude, 25, 80)
        map.placePiece(25, 3, BlockMaterial.Stone, BlockShape.Single)
        map.setActiveSlice(WorldAxis.Longitude, 25, 80)
        assertTrue(map.get(80, 3))
        map.damagePieceAt(80, 3, 100)
        assertFalse(map.get(80, 3))
        map.setActiveSlice(WorldAxis.Latitude, 25, 80)
        assertFalse(map.get(25, 3))
    }
}
