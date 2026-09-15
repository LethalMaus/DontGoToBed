package dev.jamescullimore.dontgotobed

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals

class WorldMinimapTest {
    @Test fun switchingAxisKeepsWorldMarkersInPlace() {
        assertEquals(Offset(0.25f, 0.75f), minimapPosition(25f, 75f, WorldAxis.Latitude, 100f))
        assertEquals(Offset(0.25f, 0.75f), minimapPosition(75f, 25f, WorldAxis.Longitude, 100f))
    }

    @Test fun wrapsBothCoordinatesAtWorldEdges() {
        assertEquals(Offset(0.99f, 0.01f), minimapPosition(-1f, 101f, WorldAxis.Latitude, 100f))
        assertEquals(Offset.Zero, minimapPosition(100f, -100f, WorldAxis.Longitude, 100f))
    }
}
