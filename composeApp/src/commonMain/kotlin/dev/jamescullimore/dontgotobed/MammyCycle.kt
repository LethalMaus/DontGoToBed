package dev.jamescullimore.dontgotobed

/** Active play time only. Safe time is followed by a fresh five-minute search. */
data class MammyCycle(val elapsedMs: Long = 0, val safe: Boolean = false, val unlimited: Boolean = false) {
    val hunting get() = !unlimited && !safe && elapsedMs >= ROUND_MS
    val remainingSeconds get() = ((ROUND_MS - elapsedMs).coerceAtLeast(0) + 999) / 1000
    val darkness get() = if (unlimited) .12f else if (safe) 0f else (elapsedMs.toFloat() / ROUND_MS).coerceIn(0f, 1f)
    fun found() = if(unlimited) this else MammyCycle(safe = true)
    fun advance(deltaMs: Long): MammyCycle {
        val next = elapsedMs + deltaMs.coerceAtLeast(0)
        return if (!unlimited && safe && next >= ROUND_MS) MammyCycle(next - ROUND_MS) else copy(elapsedMs = next)
    }
    fun wavesSince(previous: MammyCycle): Int = when {
        unlimited -> (elapsedMs / 60_000 - previous.elapsedMs / 60_000).toInt()
        safe -> 0
        previous.safe -> (elapsedMs / SPAWN_MS).toInt()
        else -> (elapsedMs / SPAWN_MS - previous.elapsedMs / SPAWN_MS).toInt()
    }
    companion object {
        const val ROUND_MS = 300_000L
        const val SPAWN_MS = 30_000L
    }
}
