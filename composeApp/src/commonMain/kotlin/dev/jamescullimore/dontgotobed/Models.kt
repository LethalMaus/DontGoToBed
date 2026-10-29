package dev.jamescullimore.dontgotobed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class NpcState { Idle, WalkLeft, WalkRight, Chase, Airborne, Attacking }

// Inventory models
enum class ItemType { Block, Potion, Arrow, Map, Rope, Gloves, SpeedBoots, JumpBoots, Shield, Armour, ElectricArrow }

data class InventorySlot(
    val type: ItemType? = null,
    val count: Int = 0,
    val material: BlockMaterial? = null
) {
    val displayName: String get() = when (type) {
        ItemType.Block -> tr("${material?.name} block")
        ItemType.Potion -> tr("Potion")
        ItemType.Arrow -> tr("Arrow")
        ItemType.ElectricArrow -> tr("Electric arrow")
        ItemType.Map -> tr("Map")
        ItemType.Rope -> tr("Rope")
        ItemType.Gloves -> tr("Punching gloves")
        ItemType.SpeedBoots -> tr("Speed boots")
        ItemType.JumpBoots -> tr("Jump boots")
        ItemType.Shield -> tr("Shield")
        ItemType.Armour -> tr("Armour")
        null -> tr("Empty slot")
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

/** Presentation only; stable enum IDs preserve saved outfits and multiplayer compatibility. */
val Player.displayName: String get() = if (this == Player.Papa) tr("Daddy") else name

data class WorldConfig(
    val horizontalSize: Int = 192,
    val height: Int = 54,
    val initialZombies: Int = 5,
    val initialSkeletons: Int = 3,
    val nearbyEnemyRows: Int = 1,
    val nearbyTerrainRows: Int = 1,
    val exploration: Boolean = false,
    val unlimited: Boolean = false,
    /** Zero preserves the original layout in older saves; new sessions use a nonzero seed. */
    val portalSeed: Int = 0
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
    var patrolStrideRemainingPx: Float = 0f,
    var electricArrows: Boolean = false
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
    var otherVelocity: Float = 0f,
    val electric: Boolean = false
)

// Map membership alone does not observe changes to an existing peer. Rendering
// and support checks must also observe late positions and interpolated motion.
class RemotePeer(
    var latitudeMilli: Int? = null,
    var longitudeMilli: Int? = null,
    var projectedAxis: WorldAxis = WorldAxis.Latitude,
    var hp: Int = 5,
    latitude: Int? = null,
    longitude: Int? = null,
    worldXPx: Float = 0f,
    worldXDp: Dp = 0.dp,
    heightPx: Float = 0f,
    var targetXPx: Float = 0f,
    var targetHeightPx: Float = 0f,
    hasPos: Boolean = false,
    facingRight: Boolean = true,
    selected: Player? = null,
    walkCycle: WalkCycle = WalkCycle()
) {
    var latitude by mutableStateOf(latitude)
    var longitude by mutableStateOf(longitude)
    var worldXPx by mutableFloatStateOf(worldXPx)
    var worldXDp by mutableStateOf(worldXDp)
    var heightPx by mutableFloatStateOf(heightPx)
    var hasPos by mutableStateOf(hasPos)
    var facingRight by mutableStateOf(facingRight)
    var selected by mutableStateOf(selected)
    var outfit by mutableStateOf(PlayerOutfit.Original)
    var walkCycle by mutableStateOf(walkCycle)
}

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
