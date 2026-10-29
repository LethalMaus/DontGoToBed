package dev.jamescullimore.dontgotobed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorldCameraTest {
    @Test fun zoomPreservesTheWorldPointUnderThePlayer() {
        for (zoom in WorldCamera.zoomLevels) {
            val screenCentre = 800f / 2f
            assertEquals(1230f, WorldCamera.worldX(screenCentre, 800f, 1200f, 60f, zoom), 0.001f)
            val bottom = WorldCamera.bottom(600f, 400f, 2000f, zoom)
            val screenFeet = 400f - (600f - bottom) * zoom
            assertEquals(600f, WorldCamera.worldY(screenFeet, 400f, bottom, zoom), 0.001f)
        }
    }

    @Test fun cameraFollowsClimbingAndDescendingWithoutLookingBelowTheWorld() {
        assertEquals(0f, WorldCamera.bottom(45f, 400f, 2000f, 1f))
        assertTrue(WorldCamera.bottom(900f, 400f, 2000f, 1f) > WorldCamera.bottom(300f, 400f, 2000f, 1f))
        assertEquals(1600f, WorldCamera.bottom(2500f, 400f, 2000f, 1f))
        assertEquals(0f, WorldCamera.bottom(100f, 800f, 400f, 1f))
    }
}
