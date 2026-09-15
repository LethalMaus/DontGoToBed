package dev.jamescullimore.dontgotobed

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WalkCycleTest {
    @Test fun alternatesEveryQuarterSecondOfMovement() {
        var cycle = WalkCycle()
        assertFalse(cycle.showWalkingPose)
        cycle = cycle.advance(249, true)
        assertFalse(cycle.showWalkingPose)
        cycle = cycle.advance(1, true)
        assertTrue(cycle.showWalkingPose)
        cycle = cycle.advance(249, true)
        assertTrue(cycle.showWalkingPose)
        cycle = cycle.advance(1, true)
        assertFalse(cycle.showWalkingPose)
        assertTrue(cycle.advance(250, true).showWalkingPose)
    }

    @Test fun stoppingOrBeingBlockedResetsToStanding() {
        val walking = WalkCycle().advance(300, true)
        assertTrue(walking.showWalkingPose)
        val stopped = walking.advance(16, false)
        assertFalse(stopped.showWalkingPose)
        assertFalse(stopped.advance(249, true).showWalkingPose)
        assertTrue(stopped.advance(250, true).showWalkingPose)
    }

    @Test fun followsElapsedTimeRatherThanAssumingFrameRate() {
        var cycle = WalkCycle()
        repeat(5) { cycle = cycle.advance(50, true) }
        assertTrue(cycle.showWalkingPose)
        assertFalse(cycle.advance(250, true).showWalkingPose)
        assertTrue(cycle.advance(1000, true).showWalkingPose)
    }
}
