package dev.jamescullimore.dontgotobed

import androidx.compose.ui.unit.Dp
import kotlin.math.floor
import kotlin.random.Random

object EnemySpawns {
    fun randomLocation(map: TileMap, random: Random = Random.Default) =
        WorldLocation(random.nextInt(map.width), random.nextInt(map.depth), map.height + 6)

    fun zombie(map: TileMap, unitPx: Float, pxToDp: (Float) -> Dp, random: Random = Random.Default): Zombie {
        val location = randomLocation(map, random)
        val x = location.along(map.activeAxis) * unitPx
        return Zombie(worldXPx = x, worldXDp = pxToDp(x),
            otherAxisPx = location.across(map.activeAxis) * unitPx, bottomPx = location.row * unitPx,
            isAirborne = true, isEnteringWorld = true, state = NpcState.Airborne,
            facingRight = random.nextBoolean(), speedMul = 0.8f + random.nextFloat() * 0.3f)
    }

    fun skeleton(map: TileMap, unitPx: Float, pxToDp: (Float) -> Dp, random: Random = Random.Default): SkeletonArcher {
        val location = randomLocation(map, random)
        val x = location.along(map.activeAxis) * unitPx
        return SkeletonArcher(worldXPx = x, worldXDp = pxToDp(x),
            otherAxisPx = location.across(map.activeAxis) * unitPx, bottomPx = location.row * unitPx,
            isAirborne = true, isEnteringWorld = true, state = NpcState.Airborne,
            facingRight = random.nextBoolean())
    }

    data class DropStep(val bottomPx: Float, val velocityY: Float, val landed: Boolean)

    /** The supplied map is the enemy's collision view, never the player's camera slice. */
    fun advanceDrop(map: TileMap, x: Float, bottom: Float, velocityY: Float,
                    unitPx: Float, widthPx: Float, dt: Float,
                    gravity: Float, maxFallSpeed: Float): DropStep {
        val velocity = (velocityY + gravity * dt).coerceAtLeast(-maxFallSpeed)
        val next = bottom + velocity * dt
        val cols = floor(x / unitPx).toInt()..floor((x + widthPx - 0.001f) / unitPx).toInt()
        val startRow = minOf(map.height - 1, floor((bottom - 0.001f) / unitPx).toInt())
        val endRow = maxOf(0, floor((next - 0.001f) / unitPx).toInt())
        for (row in startRow downTo endRow) {
            val top = (row + 1) * unitPx
            if (bottom >= top && next <= top && cols.any { map.get(it, row) }) {
                return DropStep(top, 0f, true)
            }
        }
        return if (next <= 0f) DropStep(0f, 0f, true) else DropStep(next, velocity, false)
    }
}
