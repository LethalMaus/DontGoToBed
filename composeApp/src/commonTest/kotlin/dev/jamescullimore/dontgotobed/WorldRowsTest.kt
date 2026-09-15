package dev.jamescullimore.dontgotobed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorldRowsTest {
    @Test fun neighborsAreNotTheActiveRowEvenWhenTheyAreVeryClose() {
        assertFalse(WorldRows.same(9.99f, 10f, 1f, 24))
        assertFalse(WorldRows.same(11f, 10f, 1f, 24))
        assertTrue(WorldRows.same(10.99f, 10f, 1f, 24))
        assertFalse(WorldRows.same(23.99f, 0f, 1f, 24))
    }

    @Test fun wrapsNegativeAndPositiveCoordinatesConsistently() {
        assertEquals(23, WorldRows.index(-0.1f, 1f, 24))
        assertTrue(WorldRows.same(-0.1f, 23.5f, 1f, 24))
        assertTrue(WorldRows.same(240f, 0f, 10f, 24))
    }
    @Test fun previewOpacityNeverChangesWhichRowCanInteract() {
        assertEquals(1f, WorldRows.previewAlpha(10.5f, 10f, 1f, 24, 0))
        assertEquals(0f, WorldRows.previewAlpha(11f, 10f, 1f, 24, 0))
        assertEquals(0.18f, WorldRows.previewAlpha(9.5f, 10f, 1f, 24, 1))
        assertEquals(0.18f, WorldRows.previewAlpha(11f, 10f, 1f, 24, 1))
        assertEquals(0f, WorldRows.previewAlpha(12f, 10f, 1f, 24, 1))
        assertEquals(0.08f, WorldRows.previewAlpha(12f, 10f, 1f, 24, 2))
        assertEquals(0f, WorldRows.previewAlpha(13f, 10f, 1f, 24, 2))
        assertEquals(0.18f, WorldRows.previewAlpha(23f, 0f, 1f, 24, 1))
        assertFalse(WorldRows.same(23f, 0f, 1f, 24))
    }
}
