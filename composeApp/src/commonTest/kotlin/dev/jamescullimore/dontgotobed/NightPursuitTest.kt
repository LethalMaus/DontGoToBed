package dev.jamescullimore.dontgotobed

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NightPursuitTest {
    private fun distance(a: Float, b: Float, span: Float) = abs(((b - a) % span + span * 1.5f) % span - span * 0.5f)

    @Test fun joinsRowBeforeChasingHorizontallyAcrossWorldSeam() {
        val map = TileMap(48, 12).also { it.seedGroundBand() }
        var p = NightPursuit.advance(36f, 20f, 3f, map, 1f, 0.1f, 4f, 22f)
        assertEquals(36f, p.first)
        assertTrue(p.second > 20f)
        repeat(200) { p = NightPursuit.advance(p.first, p.second, p.third, map, 1f, 0.1f, 4f, 22f) }
        assertEquals(2f, p.first, 0.01f)
        assertTrue(WorldRows.same(p.second, 22f, 1f, 48))
    }

    @Test fun nearbyEnemyMovesAsideBeforeEnteringFromEitherSide() {
        for (other in listOf(20f, 24f)) {
            val map = TileMap(48, 12).also { it.seedGroundBand() }
            var p = Triple(4f, other, 3f)
            var entered = false
            repeat(120) {
                val wasOffRow = !WorldRows.same(p.second, 22f, 1f, 48)
                val previous = p
                p = NightPursuit.advance(p.first, p.second, p.third, map, 1f, 0.1f, 4f, 22f)
                if (wasOffRow && distance(previous.first, 4f, 48f) < 5.99f) {
                    assertEquals(previous.second, p.second)
                }
                if (wasOffRow && WorldRows.same(p.second, 22f, 1f, 48)) {
                    assertTrue(distance(p.first, 4f, 48f) >= 5.99f)
                    entered = true
                }
            }
            assertTrue(entered)
            assertEquals(2f, distance(p.first, 4f, 48f), 0.01f)
        }
    }

    @Test fun changingPlayerRowReplansWithoutJumpingOntoPlayer() {
        val map = TileMap(48, 12).also { it.seedGroundBand() }
        val p = NightPursuit.advance(4f, 20f, 3f, map, 1f, 0.1f, 4f, 21f)
        assertEquals(20f, p.second)
        assertTrue(p.first < 4f)
        // Following a camera turn the same rule applies in the exchanged coordinate frame.
        map.setActiveSlice(WorldAxis.Longitude, 0, 0)
        val rotated = NightPursuit.advance(20f, 4f, 3f, map, 1f, 0.1f, 21f, 4.99f)
        assertEquals(4f, rotated.second)
        assertFalse(distance(rotated.first, 21f, 48f) > 2f) // Already in the row: ordinary chase stop distance.
    }

    @Test fun climbsWallBeforeEnteringAndWorksAfterAxisSwap() {
        val map = TileMap(24, 12).also { it.seedGroundBand() }
        map.setActiveSlice(WorldAxis.Latitude, 0, 5)
        map.placeBlock(4, 3, 1, 4, BlockMaterial.Wood.color)
        val p = NightPursuit.advance(2f, 5f, 3f, map, 1f, 0.1f, 10f, 5f)
        assertEquals(2f, p.first)
        assertTrue(p.third > 3f && p.third < 7f)
        map.setActiveSlice(WorldAxis.Longitude, 2, 5)
        val swapped = NightPursuit.advance(5f, 2f, 3f, map, 1f, 0.1f, 5f, 10f)
        assertEquals(2f, swapped.second) // Too close horizontally: move aside before changing rows.
        assertTrue(swapped.first < 5f)
    }
}
