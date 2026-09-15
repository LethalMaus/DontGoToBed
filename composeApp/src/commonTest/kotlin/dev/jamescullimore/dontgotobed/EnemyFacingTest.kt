package dev.jamescullimore.dontgotobed

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnemyFacingTest {
    @Test fun actualMovementOverridesAnOpposingPatrolOrChaseDirection() {
        assertTrue(EnemyFacing.afterMovement(10f, 11f, 100f, false))
        assertFalse(EnemyFacing.afterMovement(11f, 10f, 100f, true))
    }

    @Test fun crossingWorldSeamKeepsCorrectFacing() {
        assertTrue(EnemyFacing.afterMovement(99f, 1f, 100f, false))
        assertFalse(EnemyFacing.afterMovement(1f, 99f, 100f, true))
        assertFalse(EnemyFacing.afterMovement(0f, -1f, 100f, true))
    }

    @Test fun fallingOrStandingStillPreservesAimWithoutFlickering() {
        assertTrue(EnemyFacing.afterMovement(20f, 20f, 100f, true))
        assertFalse(EnemyFacing.afterMovement(20f, 20f, 100f, false))
        assertTrue(EnemyFacing.afterMovement(20f, 19.999f, 100f, true))
    }
}
