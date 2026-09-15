package dev.jamescullimore.dontgotobed

import kotlin.math.max
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerPhysicsClockTest {
    @Test fun retainsFractionalTime() {
        val clock = PlayerPhysicsClock()
        assertEquals(0, clock.advance(0))
        assertEquals(0, clock.advance(15_900_000))
        assertEquals(1, clock.advance(16_100_000))
        assertEquals(1, clock.advance(32_000_000))
    }

    @Test fun boundsFreezesAndDiscardsExcessBacklog() {
        val clock = PlayerPhysicsClock()
        clock.reset(0)
        assertEquals(6, clock.advance(10_000_000_000))
        assertEquals(0, clock.advance(10_000_000_000))
        assertEquals(1, clock.advance(10_012_000_000))
    }

    @Test fun pauseBackgroundAndTurnResetBothElapsedAndFractionalTime() {
        val clock = PlayerPhysicsClock()
        clock.reset(0)
        assertEquals(0, clock.advance(15_000_000))
        clock.reset()
        assertEquals(0, clock.advance(30_000_000_000))
        assertEquals(0, clock.advance(30_001_000_000))
        assertEquals(1, clock.advance(30_016_000_000))
    }

    // Exercise the game's existing semi-implicit integration with identical inputs.
    // Collision geometry remains in GameScreen and needs device playtesting.
    private data class Flight(val travel: Float, val apex: Float, val landingStep: Int)

    private fun simulate(fps: Int, jumping: Boolean): Flight {
        val clock = PlayerPhysicsClock()
        clock.reset(0)
        var x = 0f
        var y = if (jumping) 0f else 400f
        var velocity = if (jumping) sqrt(2f * 3000f * 128f) else 0f
        var apex = y
        var step = 0
        var landingStep = 0
        for (frame in 1..fps * 2) {
            repeat(clock.advance(frame * 1_000_000_000L / fps)) {
                step++
                x += 420f * PlayerPhysicsClock.STEP_SECONDS
                if (landingStep == 0) {
                    velocity += -3000f * PlayerPhysicsClock.STEP_SECONDS
                    y += velocity * PlayerPhysicsClock.STEP_SECONDS
                    apex = max(apex, y)
                    if (y <= 0f) {
                        y = 0f
                        landingStep = step
                    }
                }
            }
        }
        return Flight(x, apex, landingStep)
    }

    @Test fun travelJumpAndFallStayConsistentAt60_30And20Fps() {
        for (jumping in listOf(true, false)) {
            val baseline = simulate(60, jumping)
            assertEquals(840f, baseline.travel, 0.01f)
            assertTrue(baseline.landingStep > 0)
            assertEquals(baseline, simulate(30, jumping))
            assertEquals(baseline, simulate(20, jumping))
        }
    }
}
