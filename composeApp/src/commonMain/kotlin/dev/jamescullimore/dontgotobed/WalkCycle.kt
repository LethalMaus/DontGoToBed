package dev.jamescullimore.dontgotobed

/** A quarter-second standing, a quarter-second walking; stopping resets to the standing pose. */
data class WalkCycle(val movingMs: Long = 0L) {
    val showWalkingPose: Boolean get() = movingMs >= 250L

    fun advance(elapsedMs: Long, moving: Boolean): WalkCycle = if (moving) {
        WalkCycle((movingMs + elapsedMs.coerceAtLeast(0L)) % 500L)
    } else WalkCycle()
}
