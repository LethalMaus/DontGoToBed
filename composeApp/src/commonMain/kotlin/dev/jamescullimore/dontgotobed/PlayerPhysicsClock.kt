package dev.jamescullimore.dontgotobed

/** Fixed physics steps from monotonic elapsed time, independent of render/scheduler rate. */
internal class PlayerPhysicsClock {
    private var previousNs: Long? = null
    private var remainderNs = 0L

    fun reset(nowNs: Long? = null) {
        previousNs = nowNs
        remainderNs = 0L
    }

    fun advance(nowNs: Long): Int {
        val previous = previousNs
        previousNs = nowNs
        if (previous == null) return 0
        // Discard excess freeze time; retain sub-step time during ordinary gameplay.
        remainderNs = (remainderNs + (nowNs - previous).coerceIn(0L, MAX_CATCH_UP_NS))
            .coerceAtMost(MAX_CATCH_UP_NS)
        val steps = (remainderNs / STEP_NS).toInt()
        remainderNs %= STEP_NS
        return steps
    }

    companion object {
        private const val STEP_NS = 16_000_000L
        private const val MAX_CATCH_UP_NS = 100_000_000L
        const val STEP_SECONDS = 0.016f
    }
}
