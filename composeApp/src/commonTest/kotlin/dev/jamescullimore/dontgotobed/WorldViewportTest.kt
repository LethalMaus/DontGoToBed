package dev.jamescullimore.dontgotobed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorldViewportTest {
    @Test fun foundationUsesTheShortEdgeSoOrientationDoesNotChangeIt() {
        assertEquals(1, WorldViewport.foundationRows(956f, 440f))
        assertEquals(1, WorldViewport.foundationRows(440f, 956f))
        assertEquals(3, WorldViewport.foundationRows(1376f, 1032f))
        assertEquals(3, WorldViewport.foundationRows(1032f, 1376f))
        assertEquals(3, WorldViewport.foundationRows(960f, 600f))
    }

    @Test fun aimingUsesTheRaisedOriginOnBothDevices() {
        for (rows in listOf(1, 3)) {
            val unit = 30f
            val foundation = rows * WorldViewport.BLOCK_CELLS * unit
            val worldY = 7.5f * unit
            val screenY = 1400f - foundation - worldY
            assertEquals(worldY, WorldViewport.screenToWorldY(1400f, screenY, foundation))
            assertTrue(WorldViewport.screenToWorldY(1400f, 1400f - foundation / 2f, foundation) < 0f)
        }
    }
}
