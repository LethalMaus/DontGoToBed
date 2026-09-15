package dev.jamescullimore.dontgotobed

import kotlin.time.Clock
import kotlin.time.TimeSource

actual fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
private val monotonicOrigin = TimeSource.Monotonic.markNow()
actual fun nanoTime(): Long = monotonicOrigin.elapsedNow().inWholeNanoseconds
