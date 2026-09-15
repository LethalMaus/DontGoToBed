package dev.jamescullimore.dontgotobed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaperWorldTransitionTest {
    @Test fun endpointsAreUnfoldedAndUseNormalPose() {
        for (p in listOf(0f, 1f, -1f, 2f)) {
            val frame = PaperTurnFrame(p)
            assertEquals(1f, frame.widthScale, 0.0001f)
            assertEquals(1f, frame.columnScale, 0.0001f)
            assertEquals(0f, frame.frontPose, 0.0001f)
        }
    }
    @Test fun midpointKeepsStandingColumnVisibleAndFacesPlayerForward() {
        val frame = PaperTurnFrame(0.5f)
        assertEquals(1f, frame.frontPose)
        assertTrue(frame.columnScale > frame.widthScale)
        assertTrue(frame.columnScale > 0f)
        assertTrue(frame.opening)
    }
    @Test fun foldAndUnfoldMeetWithoutScaleJump() {
        for (p in listOf(0.1f, 0.25f, 0.49f)) {
            assertEquals(PaperTurnFrame(p).widthScale, PaperTurnFrame(1f - p).widthScale, 0.0001f)
            assertEquals(PaperTurnFrame(p).frontPose, PaperTurnFrame(1f - p).frontPose, 0.0001f)
        }
    }
}
