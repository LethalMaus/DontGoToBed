package dev.jamescullimore.dontgotobed

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class NpcState { Idle, WalkLeft, WalkRight, Chase, Airborne, Attacking }

// Inventory models
enum class ItemType { Block, Potion }

data class InventorySlot(var type: ItemType? = null, var count: Int = 0)

enum class Direction {
    UpLeft, UpRight,
    DownLeft, DownRight,
    Left, LeftUp, LeftDown,
    Right, RightUp, RightDown;

    companion object {
        fun Direction.isFacingRight(): Boolean {
            return this == UpRight || this == DownRight || this == Right || this == RightUp || this == RightDown
        }
    }

}

enum class Player { Leo, Ian, Papa }

data class Zombie(
    val id: Long = System.nanoTime(),
    var worldXDp: Dp = 0.dp,
    var worldXPx: Float = 0f,
    var bottomPx: Float = 0f,
    var facingRight: Boolean = true,
    var hp: Int = 3,
    var isAirborne: Boolean = false,
    var vY: Float = 0f,
    var state: NpcState = NpcState.Idle,
    var stepBudget: Int = 0,
    var pauseUntil: Long = 0L,
    var flashUntil: Long = 0L,
    // Movement tuning per zombie (multiplier applied to base speed)
    var speedMul: Float = 1f,
    // Pixel-based remaining distance for current stride (replaces tile-int budgeting)
    var strideRemainingPx: Float = 0f,
    // Patrol timers/stride
    var nextPatrolDecisionMs: Long = 0L,
    var patrolStrideRemainingPx: Float = 0f,
    // Chase memory (linger chasing after losing LoS)
    var aiMemoryUntilMs: Long = 0L,
    // RNG seed (optional; falls back to global RNG if 0)
    var rngSeed: Long = 0L,
    // Attack state
    var attackInProgress: Boolean = false,
    var attackCastStart: Long = 0L,
    // Wind-up timing: first attack after entering range is delayed by cooldown
    var nextAttackReadyAt: Long = 0L,
    var inAttackRange: Boolean = false,
)

// Remote peer model (networked players) extracted from GameScreen for reuse
data class RemotePeer(
    var worldXPx: Float = 0f,
    var worldXDp: Dp = 0.dp,
    var heightPx: Float = 0f,
    var targetXPx: Float = 0f,
    var targetHeightPx: Float = 0f,
    var hasPos: Boolean = false,
    var facingRight: Boolean = true,
    var selected: Player? = null
)

// Lightweight notification banner item extracted from GameScreen
data class Notice(
    val id: Long,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class Cell(val c: Int, val r: Int)

data class Block(
    val id: Int,
    val col: Int,
    val row: Int,
    val w: Int,
    val h: Int,
    var health: Int,
    val maxHealth: Int,
    val color: Color
)