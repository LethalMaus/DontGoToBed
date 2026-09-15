package dev.jamescullimore.dontgotobed

/** Infer facing from the completed movement, including crossing either world seam. */
object EnemyFacing {
    fun afterMovement(previousX: Float, nextX: Float, worldWidth: Float, whenStill: Boolean): Boolean {
        val delta = ((nextX - previousX) % worldWidth + worldWidth * 1.5f) % worldWidth - worldWidth * 0.5f
        return when {
            delta > 0.01f -> true
            delta < -0.01f -> false
            else -> whenStill
        }
    }
}
