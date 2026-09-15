package dev.jamescullimore.dontgotobed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MammyCycleTest {
    @Test fun defaultsAndSpawnBoundaries() {
        assertEquals(5, WorldConfig().initialZombies)
        assertEquals(3, WorldConfig().initialSkeletons)
        val start = MammyCycle()
        assertEquals(300L, start.remainingSeconds)
        assertEquals(0, start.advance(29_999).wavesSince(start))
        assertEquals(1, start.advance(30_000).wavesSince(start))
        assertEquals(3, start.advance(90_000).wavesSince(start))
    }

    @Test fun nightfallKeepsSpawningAndDarkensGradually() {
        val start = MammyCycle()
        assertEquals(0.5f, start.advance(150_000).darkness)
        assertFalse(start.advance(299_999).hunting)
        val night = start.advance(300_000)
        assertTrue(night.hunting)
        assertEquals(0L, night.remainingSeconds)
        assertEquals(1f, night.darkness)
        assertEquals(10, night.wavesSince(start))
        assertEquals(1, night.advance(30_000).wavesSince(night))
    }

    @Test fun findingMammyGrantsFiveMinutesThenRestartsSearch() {
        val safe = MammyCycle(450_000).found()
        assertTrue(safe.safe)
        assertFalse(safe.hunting)
        assertEquals(0f, safe.darkness)
        assertEquals(300L, safe.remainingSeconds)
        assertEquals(0, safe.advance(299_999).wavesSince(safe))
        assertEquals(MammyCycle(), safe.advance(300_000))
        val restarted = safe.advance(330_000)
        assertEquals(1, restarted.wavesSince(safe))
        assertFalse(restarted.hunting)
    }
}
