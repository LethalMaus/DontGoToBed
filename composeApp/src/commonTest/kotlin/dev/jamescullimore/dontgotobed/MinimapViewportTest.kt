package dev.jamescullimore.dontgotobed

import androidx.compose.ui.geometry.Offset
import kotlin.test.*

class MinimapViewportTest {
    @Test fun explorationExcludesOtherRegionsAndProjectsLocally() {
        val viewport = minimapViewport(Offset(.5f, .75f), true)
        assertTrue(viewport.contains(Offset(.5f, .75f)))
        assertFalse(viewport.contains(Offset(.1f, .75f)))
        assertFalse(viewport.contains(Offset(.5f, .25f)))
        assertEquals(.5f, viewport.project(Offset(.5f, .75f)).x, .0001f)
        assertEquals(.5f, viewport.project(Offset(.5f, .75f)).y, .0001f)
    }
    @Test fun regionBoundariesBelongToOnlyOneRegion() {
        val left = minimapViewport(Offset(.1f, .1f), true)
        val right = minimapViewport(Offset(1f / 3f, .1f), true)
        assertFalse(left.contains(Offset(1f / 3f, .1f)))
        assertTrue(right.contains(Offset(1f / 3f, .1f)))
    }
    @Test fun classicWorldKeepsItsWholeMap() {
        val viewport = minimapViewport(Offset(.5f, .5f), false)
        assertTrue(viewport.contains(Offset(.01f, .99f)))
        assertEquals(Offset(.2f, .8f), viewport.project(Offset(.2f, .8f)))
    }
}
