package dev.jamescullimore.dontgotobed

import androidx.compose.ui.unit.dp
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnemySpawnsTest {
    @Test fun samplesTheEntireWorldWithoutCameraBias() {
        for (size in listOf(192, 384, 576)) {
            val map = TileMap(size, 12)
            val random = Random(123)
            val locations = List(4096) { EnemySpawns.randomLocation(map, random) }
            assertTrue(locations.all { it.latitude in 0 until size && it.longitude in 0 until size && it.row == 18 })
            assertTrue(locations.map { it.latitude }.toSet().size > size * 0.9)
            assertTrue(locations.map { it.longitude }.toSet().size > size * 0.9)
            val expected = EnemySpawns.randomLocation(map, Random(42))
            map.setActiveSlice(WorldAxis.Longitude, size - 1, size / 2)
            assertEquals(expected, EnemySpawns.randomLocation(map, Random(42)))
        }
    }

    @Test fun bothEnemyTypesEnterAboveWorldOnEitherAxis() {
        val map = TileMap(24, 12)
        for (axis in WorldAxis.entries) {
            map.setActiveSlice(axis, 6, 7)
            val location = EnemySpawns.randomLocation(map, Random(42))
            val zombie = EnemySpawns.zombie(map, 10f, { it.dp }, Random(42))
            val skeleton = EnemySpawns.skeleton(map, 10f, { it.dp }, Random(42))
            assertEquals(location.along(axis) * 10f, zombie.worldXPx)
            assertEquals(location.across(axis) * 10f, zombie.otherAxisPx)
            assertEquals(zombie.worldXPx, skeleton.worldXPx)
            assertEquals(zombie.otherAxisPx, skeleton.otherAxisPx)
            assertEquals(180f, zombie.bottomPx)
            assertEquals(180f, skeleton.bottomPx)
            assertEquals(0f, zombie.vY)
            assertEquals(0f, skeleton.vY)
            assertTrue(zombie.isEnteringWorld && zombie.isAirborne)
            assertTrue(skeleton.isEnteringWorld && skeleton.isAirborne)
            assertEquals(NpcState.Airborne, zombie.state)
            assertEquals(NpcState.Airborne, skeleton.state)
        }
    }

    private fun fall(map: TileMap, x: Float, bottom: Float, velocity: Float = 0f, dt: Float = 0.016f) =
        EnemySpawns.advanceDrop(map, x, bottom, velocity, 1f, 2f, dt, -40f, 8f)

    @Test fun dropUsesOwnSliceAndSurvivesAxisChangeAtWrappedRoof() {
        val world = TileMap(24, 12).also { it.seedGroundBand() }
        for (z in 7..8) {
            world.setActiveSlice(WorldAxis.Latitude, 0, z)
            world.placeBlock(23, 3, 2, 4, BlockMaterial.Wood.color)
        }
        val view = world.collisionView()
        view.setActiveSlice(WorldAxis.Latitude, 0, 7)
        world.setActiveSlice(WorldAxis.Latitude, 0, 0)
        world.placeBlock(23, 3, 2, 7, BlockMaterial.Stone.color) // Wrong slice must not catch the drop.
        var step = fall(view, 23f, 18f)
        assertTrue(step.bottomPx < 18f && step.bottomPx > 10f)
        assertFalse(step.landed)
        repeat(40) { step = fall(view, 23f, step.bottomPx, step.velocityY) }
        // Same physical position after swapping axes: latitude 23, longitude 7.
        world.setActiveSlice(WorldAxis.Longitude, 0, 0)
        view.setActiveSlice(WorldAxis.Longitude, 23, 7)
        repeat(200) { if (!step.landed) step = fall(view, 7f, step.bottomPx, step.velocityY) }
        assertTrue(step.landed)
        assertEquals(7f, step.bottomPx)
        assertEquals(0f, step.velocityY)
    }

    @Test fun sweptDescentCannotTunnelThroughRoofsOrMountains() {
        val map = TileMap(24, 12).also { it.seedGroundBand() }
        map.setActiveSlice(WorldAxis.Latitude, 0, 5)
        map.placeBlock(8, 7, 5, 1, BlockMaterial.Wood.color)
        map.placePiece(18, 3, BlockMaterial.Stone, BlockShape.RampLeft)
        // Deliberately cross several cells in one update to exercise collision sweeps.
        assertEquals(8f, fall(map, 8f, 9f, -8f, 1f).bottomPx)
        assertEquals(6f, fall(map, 19f, 7f, -8f, 1f).bottomPx)
        assertEquals(3f, fall(map, 2f, 4f, -8f, 1f).bottomPx)
    }
}
