package dev.jamescullimore.dontgotobed

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class NpcState { Idle, WalkLeft, WalkRight, Chase, Airborne, Attacking }

// Inventory models
enum class ItemType { Block, Potion, Arrow, Map }

data class InventorySlot(
    val type: ItemType? = null,
    val count: Int = 0,
    val material: BlockMaterial? = null
) {
    val displayName: String get() = when (type) {
        ItemType.Block -> "${material?.name} block"
        ItemType.Potion -> "Potion"
        ItemType.Arrow -> "Arrow"
        ItemType.Map -> "Map"
        null -> "Empty slot"
    }
}

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

data class WorldConfig(
    val horizontalSize: Int = 192,
    val height: Int = 54,
    val initialZombies: Int = 5,
    val initialSkeletons: Int = 3,
    val nearbyEnemyRows: Int = 1,
    val nearbyTerrainRows: Int = 1
)

data class Zombie(
    val id: Long = nanoTime(),
    var worldXDp: Dp = 0.dp,
    var worldXPx: Float = 0f,
    /** Coordinate on the currently inactive horizontal world axis. */
    var otherAxisPx: Float = 0f,
    var bottomPx: Float = 0f,
    var targetWorldXPx: Float = worldXPx,
    var targetBottomPx: Float = bottomPx,
    var facingRight: Boolean = true,
    var hp: Int = 3,
    var isAirborne: Boolean = false,
    var isEnteringWorld: Boolean = false,
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

data class SkeletonArcher(
    val id: Long = nanoTime(),
    var worldXDp: Dp = 0.dp,
    var worldXPx: Float = 0f,
    /** Coordinate on the currently inactive horizontal world axis. */
    var otherAxisPx: Float = 0f,
    var bottomPx: Float = 0f,
    var targetWorldXPx: Float = worldXPx,
    var targetBottomPx: Float = bottomPx,
    var facingRight: Boolean = true,
    var hp: Int = 3,
    var isAirborne: Boolean = false,
    var isEnteringWorld: Boolean = false,
    var vY: Float = 0f,
    var state: NpcState = NpcState.Idle,
    var pauseUntil: Long = 0L,
    var flashUntil: Long = 0L,
    var lastShotTime: Long = 0L,
    var isAiming: Boolean = false,
    var aimStartTime: Long = 0L,
    var pulseAmount: Float = 0f,
    var nextPatrolDecisionMs: Long = 0L,
    var patrolStrideRemainingPx: Float = 0f
)

data class Arrow(
    val id: Long = nanoTime(),
    val ownerId: Long = 0L,
    var worldXPx: Float,
    var bottomPx: Float,
    var vX: Float,
    var vY: Float = 0f,
    var targetWorldXPx: Float = worldXPx,
    var targetBottomPx: Float = bottomPx,
    var distanceTravelled: Float = 0f,
    var lastDropDistance: Float = 0f,
    var otherAxisPx: Float = 0f,
    var otherVelocity: Float = 0f
)

// Remote peer model (networked players) extracted from GameScreen for reuse
data class RemotePeer(
    var latitudeMilli: Int? = null,
    var longitudeMilli: Int? = null,
    var projectedAxis: WorldAxis = WorldAxis.Latitude,
    var hp: Int = 5,
    var latitude: Int? = null,
    var longitude: Int? = null,
    var worldXPx: Float = 0f,
    var worldXDp: Dp = 0.dp,
    var heightPx: Float = 0f,
    var targetXPx: Float = 0f,
    var targetHeightPx: Float = 0f,
    var hasPos: Boolean = false,
    var facingRight: Boolean = true,
    var selected: Player? = null,
    var walkCycle: WalkCycle = WalkCycle()
)

// Lightweight notification banner item extracted from GameScreen
data class Notice(
    val id: Long,
    val text: String,
    val createdAt: Long = currentTimeMillis()
)

data class Cell(val c: Int, val r: Int)

data class Potion(
    val id: Long = nanoTime(),
    val col: Int,
    val row: Int,
    val type: ItemType = ItemType.Potion,
    val otherCol: Int = 0
)

data class Block(
    val id: Int,
    val col: Int,
    val row: Int,
    val w: Int,
    val h: Int,
    var health: Int,
    val maxHealth: Int,
    val color: Color,
    val material: BlockMaterial = BlockMaterial.Grass,
    val shape: BlockShape = BlockShape.Square,
    val axis: WorldAxis = WorldAxis.Latitude,
    val fixedCoordinate: Int = 0,
    val managed: Boolean = false
)
