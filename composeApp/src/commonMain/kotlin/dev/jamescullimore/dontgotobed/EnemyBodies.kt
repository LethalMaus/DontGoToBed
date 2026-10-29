package dev.jamescullimore.dontgotobed

import kotlin.math.abs

/** Swept, wrapped body collision. All enemy kinds use the same 2 × 1 × 3 footprint. */
internal object EnemyBodies {
    fun limit(from: Float, to: Float, a: Float, b: Float, obstacle: Float, oa: Float, ob: Float,
              size: Float, aSize: Float, bSize: Float, span: Float, wrapMovement: Boolean,
              wrapA: Boolean, wrapB: Boolean): Float {
        fun delta(x: Float, y: Float, wrap: Boolean) = if(wrap) ((y-x+span/2)%span+span)%span-span/2 else y-x
        if(abs(delta(a,oa,wrapA)) >= aSize-.001f || abs(delta(b,ob,wrapB)) >= bSize-.001f) return to
        val travel=delta(from,to,wrapMovement)
        val gap=delta(from,obstacle,wrapMovement)
        val allowed=when {
            gap >= size-.001f && travel>0 -> minOf(travel,(gap-size).coerceAtLeast(0f))
            gap <= -size+.001f && travel<0 -> maxOf(travel,(gap+size).coerceAtMost(0f))
            // Existing spawn overlap may separate, but never pass through the other body.
            abs(gap)<size && gap*travel>0 -> 0f
            else -> travel
        }
        val result=from+allowed
        return if(wrapMovement) (result%span+span)%span else result
    }
}

/** Keep a spotted player as a pursuit target briefly when they leave sight/arrow range. */
internal class PursuitMemory {
    private var lastSeen=Long.MIN_VALUE
    fun pursuing(now:Long,visible:Boolean,hunting:Boolean):Boolean {
        if(visible) lastSeen=now
        return hunting || visible || (lastSeen!=Long.MIN_VALUE && now-lastSeen in 0..5000)
    }
}
