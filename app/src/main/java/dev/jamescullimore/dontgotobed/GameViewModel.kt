package dev.jamescullimore.dontgotobed

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor

/**
 * Lightweight state holder to move non‑UI game state out of the composable.
 * Not an Android ViewModel to avoid adding dependencies; it's a simple class remembered in Compose.
 */
class GameViewModel(
    val inventorySlots: Int = 5,
    val inventoryStackLimit: Int = 20
) {
    // Inventory
    val inventory = mutableStateListOf<InventorySlot>().apply {
        repeat(inventorySlots) { add(InventorySlot()) }
    }
    var selectedSlot = mutableIntStateOf(0)

    fun hasSelectedBlock(): Boolean {
        val s = inventory.getOrNull(selectedSlot.intValue)
        return s?.type == ItemType.Block && (s.count > 0)
    }

    fun addItemToInventory(type: ItemType, count: Int = 1): Boolean {
        var remaining = count
        // Fill existing stacks first
        for (i in 0 until inventorySlots) {
            val s = inventory[i]
            if (s.type == type && s.count < inventoryStackLimit) {
                val space = inventoryStackLimit - s.count
                val toAdd = kotlin.math.min(space, remaining)
                s.count += toAdd
                remaining -= toAdd
                if (remaining <= 0) return true
            }
        }
        // Use empty slots
        for (i in 0 until inventorySlots) {
            val s = inventory[i]
            if (s.type == null || s.count <= 0) {
                s.type = type
                val toAdd = kotlin.math.min(inventoryStackLimit, remaining)
                s.count = toAdd
                remaining -= toAdd
                if (remaining <= 0) return true
            }
        }
        return count != remaining
    }

    // Remote peers (networked players)
    val peersState = mutableStateMapOf<Int, RemotePeer>()

    // Notices
    val notices = mutableStateListOf<Notice>()
    fun pushNotice(text: String) { notices.add(Notice(System.nanoTime(), text)) }

    // -------------------- Zombies (moved from GameScreen) --------------------
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val zombieJobs = mutableMapOf<Long, Job>()
    private val skeletonJobs = mutableMapOf<Long, Job>()

    // Backing state for UI collection
    private val _zombies = MutableStateFlow<List<Zombie>>(emptyList())
    val zombies = _zombies.asStateFlow()

    private val _skeletons = MutableStateFlow<List<SkeletonArcher>>(emptyList())
    val skeletons = _skeletons.asStateFlow()

    private val _arrows = MutableStateFlow<List<Arrow>>(emptyList())
    val arrows = _arrows.asStateFlow()

    private val _potions = MutableStateFlow<List<Potion>>(emptyList())
    val potions = _potions.asStateFlow()

    // Player hit events emitted by zombies (UI consumes to reduce HP/flash)
    private val _playerDamage = kotlinx.coroutines.flow.MutableSharedFlow<Int>(extraBufferCapacity = 16)
    val playerDamage = _playerDamage as kotlinx.coroutines.flow.SharedFlow<Int>

    // Player speed (tiles/sec) so zombies can run slightly slower than the player
    @Volatile private var playerSpeedTilesPerSec: Float = 1.0f
    fun setPlayerSpeedTilesPerSec(speedTilesPerSec: Float) {
        playerSpeedTilesPerSec = speedTilesPerSec.coerceAtLeast(0.1f)
    }

    // Player world position so zombies can chase
    @Volatile private var playerWorldXPx: Float = 0f
    @Volatile private var playerBottomPx: Float = 0f
    fun setPlayerPositionPx(xPx: Float, bottomPx: Float) {
        playerWorldXPx = xPx
        playerBottomPx = bottomPx
    }

    // Utility helpers matching GameScreen assumptions (player/zombie share size)
    private fun aabbOverlappingColumns(leftPx: Float, unitPx: Float, widthPx: Float): IntRange {
        val c0 = floor(leftPx / unitPx).toInt()
        val c1 = floor((leftPx + widthPx - 0.001f) / unitPx).toInt()
        return c0..c1
    }
    private fun aabbOverlappingRows(bottomPx: Float, unitPx: Float, heightPx: Float): IntRange {
        val r0 = floor((bottomPx - 0.001f) / unitPx).toInt()
        val r1 = floor(((bottomPx + heightPx - 0.001f) / unitPx)).toInt()
        return r0..r1
    }

    private fun anySolidIn(tileMap: TileMap, cols: IntRange, rows: IntRange): Boolean {
        for (c in cols) for (r in rows) if (tileMap.get(c, r)) return true
        return false
    }

    // Basic horizontal collision for NPCs (no player/zombie pushback, just tile blocking)
    private fun npcApplyHorizontalCollision(
        tileMap: TileMap,
        prevPx: Float,
        nextPx: Float,
        bottomPx: Float,
        unitPx: Float,
        widthPx: Float,
        heightPx: Float
    ): Float {
        if (nextPx == prevPx) return nextPx
        // Use a trimmed vertical range to avoid treating the ground directly under the feet
        // as an overlapping obstacle during horizontal sweeps.
        val rows: IntRange = run {
            val trim = 2f // px
            val bottom = (bottomPx + trim).coerceAtLeast(0f)
            val top = (bottomPx + heightPx - trim).coerceAtLeast(bottom)
            val r0 = floor(bottom / unitPx).toInt()
            val r1 = floor(top / unitPx).toInt()
            r0..r1
        }
        return if (nextPx > prevPx) {
            // Moving right: sweep the right edge across crossed columns
            val prevRight = prevPx + widthPx - 0.001f
            val nextRight = nextPx + widthPx - 0.001f
            val startCol = floor(prevRight / unitPx).toInt() + 1
            val endCol = floor(nextRight / unitPx).toInt()
            for (col in startCol..endCol) {
                if (anySolidIn(tileMap, col..col, rows)) {
                    val tileLeftPx = col * unitPx
                    return tileLeftPx - widthPx
                }
            }
            // Final overlap safeguard at destination
            val colAtEnd = floor(nextRight / unitPx).toInt()
            if (anySolidIn(tileMap, colAtEnd..colAtEnd, rows)) {
                val tileLeftPx = colAtEnd * unitPx
                tileLeftPx - widthPx
            } else nextPx
        } else {
            // Moving left: sweep the left edge across crossed columns
            val prevLeft = prevPx + 0.001f
            val nextLeft = nextPx + 0.001f
            val startCol = floor(prevLeft / unitPx).toInt() - 1
            val endCol = floor(nextLeft / unitPx).toInt()
            for (col in startCol downTo endCol) {
                if (anySolidIn(tileMap, col..col, rows)) {
                    val tileRightPx = (col + 1) * unitPx
                    return tileRightPx
                }
            }
            // Final overlap safeguard at destination
            val colAtEnd = floor(nextLeft / unitPx).toInt()
            if (anySolidIn(tileMap, colAtEnd..colAtEnd, rows)) {
                val tileRightPx = (colAtEnd + 1) * unitPx
                tileRightPx
            } else nextPx
        }
    }

    private fun isSupported(tileMap: TileMap, leftPx: Float, unitPx: Float, widthPx: Float, bottomPx: Float): Boolean {
        val cols = aabbOverlappingColumns(leftPx, unitPx, widthPx)
        // Tile directly under the feet (no off-by-one)
        val rowUnder = floor((bottomPx - 0.001f) / unitPx).toInt()
        if (!tileMap.inY(rowUnder)) return false
        for (c in cols) if (tileMap.get(c, rowUnder)) return true
        return false
    }

    // Tunable NPC physics constants (expressed in tiles/second, tiles/second^2)
    private companion object {
        // Baseline cap; actual speed is derived relative to the player's speed
        const val ZOMBIE_SPEED_TILES_PER_SEC = 0.25f     // fallback/base patrol speed (tiles/sec)
        // Make zombies ~150% faster than before; allow them to slightly exceed player speed during chase
        const val ZOMBIE_RELATIVE_SPEED = 0.8f           // patrol speed relative to player
        const val SKELETON_RELATIVE_SPEED = 1.0f         // skeleton speed relative to player
        const val CHASE_RELATIVE_SPEED = 0.9f           // chase speed relative to player
        const val HORIZONTAL_CAP_TILES = 1.0f            // allow larger per-frame moves but keep a safety cap
        const val CHASE_RANGE_TILES = 2000f              // effectively unlimited horizontal trigger (capped by world wrap)
        const val CHASE_VERTICAL_TILES = 32f             // generous vertical tolerance
        const val GRAVITY_TILES_PER_SEC2 = -40f          // downward acceleration
        const val MAX_FALL_TILES_PER_SEC = 8f            // terminal velocity magnitude
        const val MIN_DT_SEC = 0.005f                    // 5 ms
        const val MAX_DT_SEC = 0.033f                    // 33 ms (avoid big jumps when app hiccups)
        // Attacks
        const val ATTACK_COOLDOWN_MS = 1500L
        const val ATTACK_DAMAGE = 1
        // Patrol / chase tuning
        const val PATROL_DECISION_MS_MIN = 1000L
        const val PATROL_DECISION_MS_MAX = 2000L
        const val PATROL_STRIDE_TILES_MIN = 1
        const val PATROL_STRIDE_TILES_MAX = 6
        const val CHASE_MEMORY_MS = 5000L
        const val LOS_ROW_PAD_CELLS = 1
        const val REACHABLE_LEDGE_MAX_HEIGHT_CELLS = 2
        const val SKELETON_LOS_RANGE_UNITS = 24f
    }

    // --- AI helpers: wrap-aware line of sight and reachability ---
    private fun hasLineOfSight(
        zXPx: Float,
        zBottomPx: Float,
        tileMap: TileMap,
        unitPx: Float,
        widthPx: Float,
        heightPx: Float,
        maxDistPx: Float = Float.MAX_VALUE
    ): Boolean {
        val worldWidth = tileMap.width
        // Columns/rows covered by zombie and player AABBs (2×3 footprint)
        val zCols = aabbOverlappingColumns(zXPx, unitPx, widthPx)
        val zRows = aabbOverlappingRows(zBottomPx, unitPx, heightPx)
        val pColsBase = aabbOverlappingColumns(playerWorldXPx, unitPx, widthPx)
        val pRows = aabbOverlappingRows(playerBottomPx, unitPx, heightPx)

        // Pick the player's wrapped image that is horizontally closest to the zombie
        fun rangeCenterX(cols: IntRange): Float = ((cols.first + cols.last) * 0.5f) * unitPx
        val zCenterX = zXPx + widthPx * 0.5f
        var bestOffsetCols = 0
        run {
            val worldWidthPx = tileMap.width * unitPx
            val baseCenter = rangeCenterX(pColsBase)
            var bestDist = abs(baseCenter - zCenterX)
            var bestOff = 0
            for (off in intArrayOf(-worldWidth, 0, worldWidth)) {
                val shifted = baseCenter + off * unitPx
                val d = abs(shifted - zCenterX)
                if (d < bestDist) { bestDist = d; bestOff = off }
            }
            if (bestDist > maxDistPx) return false
            bestOffsetCols = bestOff
        }
        val pCols = (pColsBase.first + bestOffsetCols)..(pColsBase.last + bestOffsetCols)

        // Vertical band where we test for blocking tiles.
        // Start one row ABOVE the feet to skip the ground they stand on.
        val rowMin = minOf(pRows.first, zRows.first) + 1
        val rowMax = maxOf(pRows.last, zRows.last)
        if (rowMin > rowMax) return false
        val rows = rowMin..rowMax

        // Determine traversal direction across columns along the chosen (non-wrapping) path
        val goRight = pCols.first > zCols.last
        val startCol = if (goRight) zCols.last + 1 else zCols.first - 1
        val endCol = if (goRight) pCols.first - 1 else pCols.last + 1
        val step = if (goRight) 1 else -1
        var col = startCol
        while (true) {
            if ((goRight && col > endCol) || (!goRight && col < endCol)) break
            val wc = ((col % worldWidth) + worldWidth) % worldWidth
            // If any solid tile lies in the overlapping vertical rows, LoS is blocked
            for (r in rows) {
                if (!tileMap.inY(r)) continue
                if (tileMap.get(wc, r)) return false
            }
            col += step
        }
        return true
    }

    private fun hasLineOfSight(
        z: Zombie,
        tileMap: TileMap,
        unitPx: Float,
        widthPx: Float,
        heightPx: Float
    ): Boolean = hasLineOfSight(z.worldXPx, z.bottomPx, tileMap, unitPx, widthPx, heightPx, CHASE_RANGE_TILES * unitPx)

    private fun isPlayerReachable(
        z: Zombie,
        tileMap: TileMap,
        unitPx: Float,
        widthPx: Float,
        heightPx: Float
    ): Boolean {
        // Vertical proximity: mid-height difference small → reachable
        val zMid = z.bottomPx + heightPx * 0.5f
        val pMid = playerBottomPx + heightPx * 0.5f
        val vertTiles = kotlin.math.abs(zMid - pMid) / unitPx
        if (vertTiles <= REACHABLE_LEDGE_MAX_HEIGHT_CELLS) return true
        // If player is below and no tile under zombie (drop), treat as reachable (will fall to chase)
        if (pMid < zMid) {
            val rowUnder = kotlin.math.floor((z.bottomPx - 0.001f) / unitPx).toInt()
            val cols = aabbOverlappingColumns(z.worldXPx, unitPx, widthPx)
            var supported = false
            for (c in cols) if (tileMap.get(c, rowUnder)) { supported = true; break }
            if (!supported) return true
        }
        return false
    }

    fun spawnZombieRandom(
        tileMap: TileMap,
        unitPx: Float,
        playerWidthPx: Float,
        playerHeightPx: Float,
        avoidLeftPx: Float,
        avoidRightPx: Float,
        pxToDp: (Float) -> androidx.compose.ui.unit.Dp
    ) {
        // Try to find a ground column not overlapping the avoid range (player)
        val rng = java.util.Random()
        repeat(200) {
            val col = rng.nextInt(tileMap.width)
            var topRow: Int? = null
            for (r in (tileMap.height - 1) downTo 0) {
                if (tileMap.get(col, r)) { topRow = r; break }
            }
            if (topRow != null) {
                val zBottom = (topRow!! + 1) * unitPx
                val zLeftPx = col * unitPx
                val zOverlapWithAvoid = !(zLeftPx + playerWidthPx < avoidLeftPx || zLeftPx > avoidRightPx)
                if (!zOverlapWithAvoid) {
                    val faceRight = rng.nextBoolean()
                    val now = System.currentTimeMillis()
                    val z = Zombie(
                        worldXDp = pxToDp(zLeftPx),
                        worldXPx = zLeftPx,
                        bottomPx = zBottom,
                        facingRight = faceRight,
                        hp = 3,
                        isAirborne = false,
                        vY = 0f,
                        state = NpcState.Idle,
                        stepBudget = 0,
                        pauseUntil = 0L,
                        speedMul = 0.8f + rng.nextFloat() * 0.3f, // 0.8x..1.1x variation
                        nextPatrolDecisionMs = now, // trigger immediate decision on first tick
                    )
                    addZombie(z)
                    // Start movement job with simple patrol AI
                    startZombieJob(z.id, tileMap, unitPx, playerWidthPx, playerHeightPx, pxToDp)
                    return
                }
            }
        }
        // Fallback: spawn at origin
        val z = Zombie(worldXDp = pxToDp(0f), worldXPx = 0f, bottomPx = 0f)
        addZombie(z)
        startZombieJob(z.id, tileMap, unitPx, playerWidthPx, playerHeightPx, pxToDp)
    }

    private fun addZombie(z: Zombie) {
        _zombies.update { it + z }
    }

    private fun removeZombieById(id: Long) {
        zombieJobs.remove(id)?.cancel()
        _zombies.update { list -> list.filterNot { it.id == id } }
    }

    fun spawnPotion(col: Int, row: Int) {
        _potions.update { it + Potion(col = col, row = row) }
    }

    fun collectPotion(id: Long): Boolean {
        var collected = false
        _potions.update { current ->
            val p = current.find { it.id == id }
            if (p != null && addItemToInventory(ItemType.Potion, 1)) {
                collected = true
                current.filterNot { it.id == id }
            } else {
                current
            }
        }
        return collected
    }

    fun damageZombie(id: Long, unitPx: Float, amount: Int = 1) {
        _zombies.update { list ->
            val newList = list.toMutableList()
            val idx = newList.indexOfFirst { it.id == id }
            if (idx != -1) {
                val z = newList[idx]
                if (z.hp > 0) {
                    z.hp = (z.hp - amount).coerceAtLeast(0)
                    z.flashUntil = System.currentTimeMillis() + 150L
                    newList[idx] = z
                    if (z.hp <= 0) {
                        removeZombieById(id)
                        spawnPotion(
                            floor(z.worldXPx / unitPx).toInt(),
                            floor(z.bottomPx / unitPx).toInt()
                        )
                        // The removal will be handled by removeZombieById which calls zombieJobs.remove(id)?.cancel()
                        // and then filters _zombies.value. 
                        // Wait, removeZombieById also updates _zombies.value!
                        // This might cause another race!
                    }
                }
            }
            newList
        }
    }

    private fun addSkeleton(s: SkeletonArcher) {
        _skeletons.update { it + s }
    }

    private fun removeSkeletonById(id: Long) {
        skeletonJobs.remove(id)?.cancel()
        _skeletons.update { list -> list.filterNot { it.id == id } }
    }

    fun damageSkeleton(id: Long, unitPx: Float, amount: Int = 1) {
        _skeletons.update { list ->
            val newList = list.toMutableList()
            val idx = newList.indexOfFirst { it.id == id }
            if (idx != -1) {
                val s = newList[idx]
                if (s.hp > 0) {
                    s.hp = (s.hp - amount).coerceAtLeast(0)
                    s.flashUntil = System.currentTimeMillis() + 150L
                    newList[idx] = s
                    if (s.hp <= 0) {
                        removeSkeletonById(id)
                        spawnPotion(
                            floor(s.worldXPx / unitPx).toInt(),
                            floor(s.bottomPx / unitPx).toInt()
                        )
                    }
                }
            }
            newList
        }
    }

    private fun spawnArrow(x: Float, y: Float, targetX: Float, targetY: Float, unitPx: Float, worldWidthPx: Float, ownerId: Long = 0L) {
        val wrappedX = ((x % worldWidthPx) + worldWidthPx) % worldWidthPx
        val dx = targetX - wrappedX
        // Handle wrap around for arrow direction
        val adjustedDx = if (abs(dx) > worldWidthPx / 2f) {
            if (dx > 0) dx - worldWidthPx else dx + worldWidthPx
        } else dx

        val dy = targetY - y
        val mag = kotlin.math.sqrt(adjustedDx * adjustedDx + dy * dy)
        val velocity = 30f * unitPx // 3 blocks per second
        val vX = if (mag > 0) (adjustedDx / mag) * velocity else velocity
        val vY = if (mag > 0) (dy / mag) * velocity else 0f

        val arrow = Arrow(worldXPx = wrappedX, bottomPx = y, vX = vX, vY = vY, ownerId = ownerId)
        _arrows.update { it + arrow }
    }

    private fun updateArrows(tileMap: TileMap, unitPx: Float, widthPx: Float, heightPx: Float, dt: Float) {
        _arrows.update { currentArrows ->
            val newList = currentArrows.toMutableList()
            val arrowsToRemove = mutableListOf<Arrow>()
            val worldWidthPx = tileMap.width * unitPx

            for (i in newList.indices) {
                val arrow = newList[i].copy()
                arrow.worldXPx = ((arrow.worldXPx + arrow.vX * dt) % worldWidthPx + worldWidthPx) % worldWidthPx
                arrow.bottomPx += arrow.vY * dt

                val dist = kotlin.math.sqrt(arrow.vX * arrow.vX + arrow.vY * arrow.vY) * dt
                arrow.distanceTravelled += dist

                // Gravity: drop 1 block (unitPx) every 15 blocks travelled
                if (arrow.distanceTravelled - arrow.lastDropDistance >= 15 * unitPx) {
                    arrow.bottomPx -= unitPx
                    arrow.lastDropDistance += 15 * unitPx
                }

                // Collision detection
                val col = floor(arrow.worldXPx / unitPx).toInt()
                val row = floor(arrow.bottomPx / unitPx).toInt()

                var hit = false
                // Hit block
                if (tileMap.inY(row) && tileMap.get(col, row)) {
                    tileMap.damage(col, row, 1)
                    hit = true
                }

                // Hit player
                if (!hit) {
                    val pCols = aabbOverlappingColumns(playerWorldXPx, unitPx, widthPx)
                    val pRows = aabbOverlappingRows(playerBottomPx, unitPx, heightPx)
                    if (col in pCols && row in pRows) {
                        _playerDamage.tryEmit(1)
                        hit = true
                    }
                }

                // Hit Zombie
                if (!hit) {
                    val zList = _zombies.value
                    for (z in zList) {
                        val zCols = aabbOverlappingColumns(z.worldXPx, unitPx, widthPx)
                        val zRows = aabbOverlappingRows(z.bottomPx, unitPx, heightPx)
                        if (col in zCols && row in zRows) {
                            damageZombie(z.id, unitPx, 1)
                            hit = true
                            break
                        }
                    }
                }

                // Hit Skeleton
                if (!hit) {
                    val sList = _skeletons.value
                    for (s in sList) {
                        if (s.id == arrow.ownerId) continue
                        val sCols = aabbOverlappingColumns(s.worldXPx, unitPx, widthPx)
                        val sRows = aabbOverlappingRows(s.bottomPx, unitPx, heightPx)
                        if (col in sCols && row in sRows) {
                            damageSkeleton(s.id, unitPx, 1)
                            hit = true
                            break
                        }
                    }
                }

                // Remove arrow if it hit something or went out of bounds (Y)
                if (hit || !tileMap.inY(row)) {
                    arrowsToRemove.add(newList[i])
                } else {
                    newList[i] = arrow
                }
            }
            newList.filter { it !in arrowsToRemove }
        }
    }

    private fun startSkeletonArcherJob(
        id: Long,
        tileMap: TileMap,
        unitPx: Float,
        playerWidthPx: Float,
        playerHeightPx: Float,
        pxToDp: (Float) -> androidx.compose.ui.unit.Dp
    ) {
        if (skeletonJobs.containsKey(id)) return
        val job = scope.launch {
            val rng = java.util.Random()
            var local: SkeletonArcher? = _skeletons.value.firstOrNull { it.id == id }
            var vY = 0f
            var lastTimeNs = System.nanoTime()
            val frameMs = 16L

            while (local != null) {
                val nowMs = System.currentTimeMillis()
                val nowNs = System.nanoTime()
                var dtSec = (nowNs - lastTimeNs) / 1_000_000_000.0f
                if (dtSec.isNaN() || dtSec.isInfinite()) dtSec = 0.016f
                dtSec = dtSec.coerceIn(MIN_DT_SEC, MAX_DT_SEC)
                lastTimeNs = nowNs
                var s = local!!

                // Gravity + vertical collisions
                val gravPxPerSec2 = GRAVITY_TILES_PER_SEC2 * unitPx
                val maxFallPxPerSec = MAX_FALL_TILES_PER_SEC * unitPx
                val wasSupported = isSupported(tileMap, s.worldXPx, unitPx, playerWidthPx, s.bottomPx)

                vY = (vY + gravPxPerSec2 * dtSec).coerceAtLeast(-maxFallPxPerSec)
                val prevBottom = s.bottomPx
                var nextBottom = prevBottom + vY * dtSec

                var landed = false
                if (vY <= 0f) {
                    val cols = aabbOverlappingColumns(s.worldXPx, unitPx, playerWidthPx)
                    val startRow = floor((prevBottom - 0.001f) / unitPx).toInt()
                    val endRow = floor((nextBottom - 0.001f) / unitPx).toInt()
                    var landingTopPx: Float? = null
                    for (row in startRow downTo maxOf(endRow, 0)) {
                        var hit = false
                        for (c in cols) { if (tileMap.get(c, row)) { hit = true; break } }
                        if (hit) {
                            val topPx = (row + 1) * unitPx
                            if (prevBottom >= topPx && nextBottom <= topPx) {
                                landingTopPx = maxOf(landingTopPx ?: Float.NEGATIVE_INFINITY, topPx)
                            }
                        }
                    }
                    if (landingTopPx != null) {
                        nextBottom = landingTopPx!!
                        vY = 0f
                        landed = true
                    }
                }
                s.bottomPx = nextBottom.coerceAtLeast(0f)
                s.isAirborne = !landed && !isSupported(tileMap, s.worldXPx, unitPx, playerWidthPx, s.bottomPx)
                if (landed) s.isAirborne = false
                if (!s.isAirborne) vY = 0f

                // AI Logic
                val hasLos = hasLineOfSight(s.worldXPx, s.bottomPx, tileMap, unitPx, playerWidthPx, playerHeightPx, SKELETON_LOS_RANGE_UNITS * unitPx)
                if (hasLos) {
                    if (!s.isAiming) {
                        s.isAiming = true
                        s.aimStartTime = nowMs
                        s.lastShotTime = nowMs // wait 3s for the first shot
                    }

                    val timeSinceLastShot = nowMs - s.lastShotTime
                    val cycleDuration = 3000L
                    val progress = (timeSinceLastShot % cycleDuration).toFloat() / cycleDuration
                    
                    // Pulse faster and faster
                    // Phase formula: 2 * PI * (3*P + 6*P^2) for 1Hz -> 5Hz over 3s
                    val p = progress.toDouble()
                    val phase = 2.0 * kotlin.math.PI * (3.0 * p + 6.0 * p * p)
                    s.pulseAmount = ((1.0 - kotlin.math.cos(phase)) / 2.0).toFloat()

                    if (timeSinceLastShot >= 3000L) {
                        val playerTopPx = playerBottomPx + playerHeightPx
                        val worldWidthPx = tileMap.width * unitPx
                        val pCenterX = playerWorldXPx + playerWidthPx / 2f
                        val pCenterY = playerTopPx - unitPx / 2f

                        val arrowSpawnX = if (s.facingRight) {
                            s.worldXPx + 2.5f * unitPx
                        } else {
                            s.worldXPx - 0.5f * unitPx
                        }
                        val arrowSpawnY = s.bottomPx + playerHeightPx * 0.7f

                        spawnArrow(
                            arrowSpawnX,
                            arrowSpawnY,
                            pCenterX,
                            pCenterY,
                            unitPx,
                            worldWidthPx,
                            ownerId = s.id
                        )
                        s.lastShotTime = nowMs
                    }

                    // Face player
                    val dx = playerWorldXPx - s.worldXPx
                    val worldWidthPx = tileMap.width * unitPx
                    val adjustedDx = if (abs(dx) > worldWidthPx / 2f) {
                        if (dx > 0) dx - worldWidthPx else dx + worldWidthPx
                    } else dx
                    s.facingRight = adjustedDx > 0
                    s.state = NpcState.Idle
                    s.patrolStrideRemainingPx = 0f // reset patrol when chasing/aiming
                } else {
                    s.isAiming = false
                    s.pulseAmount = 0f
                    
                    // Wandering / Patrol logic
                    if (!s.isAirborne && nowMs >= s.pauseUntil) {
                        if (nowMs >= s.nextPatrolDecisionMs || s.patrolStrideRemainingPx <= 0f) {
                            val rngVal = rng.nextFloat()
                            if (rngVal < 0.15f) {
                                // Pause
                                s.state = NpcState.Idle
                                s.pauseUntil = nowMs + 500 + rng.nextInt(1500)
                            } else {
                                // Start walking
                                s.facingRight = rng.nextBoolean()
                                s.state = if (s.facingRight) NpcState.WalkRight else NpcState.WalkLeft
                                s.patrolStrideRemainingPx = (PATROL_STRIDE_TILES_MIN + rng.nextFloat() * (PATROL_STRIDE_TILES_MAX - PATROL_STRIDE_TILES_MIN)) * unitPx
                            }
                            s.nextPatrolDecisionMs = nowMs + PATROL_DECISION_MS_MIN + rng.nextInt((PATROL_DECISION_MS_MAX - PATROL_DECISION_MS_MIN).toInt())
                        }
                        
                        if (s.state == NpcState.WalkLeft || s.state == NpcState.WalkRight) {
                            val speed = SKELETON_RELATIVE_SPEED
                            val dir = if (s.state == NpcState.WalkRight) 1f else -1f
                            val moveDist = speed * playerSpeedTilesPerSec * unitPx * dtSec
                            val nextX = s.worldXPx + dir * moveDist
                            
                            val resolvedX = npcApplyHorizontalCollision(tileMap, s.worldXPx, nextX, s.bottomPx, unitPx, playerWidthPx, playerHeightPx)
                            val actualMove = abs(resolvedX - s.worldXPx)
                            
                            // Wrap around
                            val worldWidthPx = tileMap.width * unitPx
                            s.worldXPx = (resolvedX + worldWidthPx) % worldWidthPx
                            s.patrolStrideRemainingPx -= actualMove
                            
                            if (actualMove < 0.1f && moveDist > 0.1f) {
                                // Blocked by wall, stop patrol stride
                                s.patrolStrideRemainingPx = 0f
                            }
                        }
                    }
                }

                _skeletons.update { list ->
                    val newList = list.toMutableList()
                    val idx = newList.indexOfFirst { it.id == s.id }
                    if (idx != -1) {
                        s.worldXDp = pxToDp(s.worldXPx)
                        newList[idx] = s
                    }
                    newList
                }
                local = _skeletons.value.firstOrNull { it.id == s.id }

                if (_skeletons.value.firstOrNull()?.id == id) {
                    updateArrows(tileMap, unitPx, playerWidthPx, playerHeightPx, dtSec)
                }
                delay(frameMs)
            }
        }
        skeletonJobs[id] = job
    }

    fun spawnSkeletonRandom(
        tileMap: TileMap,
        unitPx: Float,
        playerWidthPx: Float,
        playerHeightPx: Float,
        avoidLeftPx: Float,
        avoidRightPx: Float,
        pxToDp: (Float) -> androidx.compose.ui.unit.Dp
    ) {
        val rng = java.util.Random()
        repeat(200) {
            val col = rng.nextInt(tileMap.width)
            var topRow: Int? = null
            for (r in (tileMap.height - 1) downTo 0) {
                if (tileMap.get(col, r)) { topRow = r; break }
            }
            if (topRow != null) {
                val sBottom = (topRow!! + 1) * unitPx
                val sLeftPx = col * unitPx
                val sOverlapWithAvoid = !(sLeftPx + playerWidthPx < avoidLeftPx || sLeftPx > avoidRightPx)
                if (!sOverlapWithAvoid) {
                    val faceRight = rng.nextBoolean()
                    val s = SkeletonArcher(
                        worldXDp = pxToDp(sLeftPx),
                        worldXPx = sLeftPx,
                        bottomPx = sBottom,
                        facingRight = faceRight,
                        hp = 3
                    )
                    addSkeleton(s)
                    startSkeletonArcherJob(s.id, tileMap, unitPx, playerWidthPx, playerHeightPx, pxToDp)
                    return
                }
            }
        }
    }

    private fun startZombieJob(
        id: Long,
        tileMap: TileMap,
        unitPx: Float,
        widthPx: Float,
        heightPx: Float,
        pxToDp: (Float) -> androidx.compose.ui.unit.Dp,
    ) {
        if (zombieJobs.containsKey(id)) return
        val job = scope.launch {
            val rng = java.util.Random()
            var local: Zombie? = _zombies.value.firstOrNull { it.id == id }
            var vY = 0f // px/s
            var lastTimeNs = System.nanoTime()
            val startMs = System.currentTimeMillis()
            // Ensure a fresh cooldown whenever this job starts (or restarts)
            local?.let { z0 ->
                z0.nextAttackReadyAt = startMs + ATTACK_COOLDOWN_MS
                val list = _zombies.value.toMutableList()
                val idx = list.indexOfFirst { it.id == z0.id }
                if (idx != -1) list[idx] = z0
                _zombies.value = list
            }
            while (local != null) {
                val nowMs = System.currentTimeMillis()
                val nowNs = System.nanoTime()
                var dtSec = (nowNs - lastTimeNs) / 1_000_000_000.0f
                if (dtSec.isNaN() || dtSec.isInfinite()) dtSec = 0.016f
                dtSec = dtSec.coerceIn(MIN_DT_SEC, MAX_DT_SEC)
                lastTimeNs = nowNs
                var z = local!!

                // Gravity + vertical collisions (floor/ceiling)
                val gravPxPerSec2 = GRAVITY_TILES_PER_SEC2 * unitPx
                val maxFallPxPerSec = MAX_FALL_TILES_PER_SEC * unitPx
                val wasSupported = isSupported(tileMap, z.worldXPx, unitPx, widthPx, z.bottomPx)

                // Integrate velocity
                vY = (vY + gravPxPerSec2 * dtSec).coerceAtLeast(-maxFallPxPerSec)
                val prevBottom = z.bottomPx
                var nextBottom = prevBottom + vY * dtSec

                var landed = false
                if (vY <= 0f) {
                    // Descending: sweep crossed rows and snap to the highest top we intersected
                    val cols = aabbOverlappingColumns(z.worldXPx, unitPx, widthPx)
                    val startRow = floor((prevBottom - 0.001f) / unitPx).toInt()
                    val endRow = floor((nextBottom - 0.001f) / unitPx).toInt()
                    var landingTopPx: Float? = null
                    for (row in startRow downTo maxOf(endRow, 0)) {
                        var hit = false
                        for (c in cols) { if (tileMap.get(c, row)) { hit = true; break } }
                        if (hit) {
                            val topPx = (row + 1) * unitPx
                            if (prevBottom >= topPx && nextBottom <= topPx) {
                                landingTopPx = maxOf(landingTopPx ?: Float.NEGATIVE_INFINITY, topPx)
                            }
                        }
                    }
                    if (landingTopPx != null) {
                        nextBottom = landingTopPx!!
                        vY = 0f
                        landed = true
                    }
                } else {
                    // Ascending (rare for zombies since they don't jump): prevent clipping into ceilings
                    val cols = aabbOverlappingColumns(z.worldXPx, unitPx, widthPx)
                    val prevTop = prevBottom + heightPx
                    val nowTop = nextBottom + heightPx
                    val startRow = floor(prevTop / unitPx).toInt()
                    val endRow = floor(nowTop / unitPx).toInt()
                    if (endRow >= startRow) {
                        for (row in (startRow + 1)..endRow) {
                            var hit = false
                            for (c in cols) { if (tileMap.get(c, row)) { hit = true; break } }
                            if (hit) {
                                val tileBottomPx = row * unitPx
                                nextBottom = tileBottomPx - heightPx
                                vY = 0f
                                break
                            }
                        }
                    }
                }

                // Apply vertical result
                z.bottomPx = nextBottom.coerceAtLeast(0f)
                z.isAirborne = !landed && !isSupported(tileMap, z.worldXPx, unitPx, widthPx, z.bottomPx)
                if (landed) z.isAirborne = false
                if (!z.isAirborne) vY = 0f

                // Respect pauses inserted by patrol/block handling
                val paused = nowMs < z.pauseUntil
                if (paused) {
                    z.state = NpcState.Idle
                    val list = _zombies.value.toMutableList()
                    val idx = list.indexOfFirst { it.id == z.id }
                    if (idx != -1) list[idx] = z
                    _zombies.value = list
                    local = z
                    continue
                }

                // Patrol decision scheduling (random, unbiased)
                if (!z.isAirborne && !paused) {
                    if (z.nextPatrolDecisionMs == 0L) {
                        z.nextPatrolDecisionMs = nowMs
                    }
                    if (nowMs >= z.nextPatrolDecisionMs || z.patrolStrideRemainingPx <= 0f) {
                        val decideIn = PATROL_DECISION_MS_MIN + rng.nextInt((PATROL_DECISION_MS_MAX - PATROL_DECISION_MS_MIN).toInt()).toLong()
                        z.nextPatrolDecisionMs = nowMs + decideIn
                        val tiles = PATROL_STRIDE_TILES_MIN + rng.nextInt(PATROL_STRIDE_TILES_MAX - PATROL_STRIDE_TILES_MIN + 1)
                        z.patrolStrideRemainingPx = tiles * unitPx
                        z.facingRight = rng.nextBoolean()
                    }
                }

                // Horizontal step only when grounded to prevent "air surfing" across gaps
                if (!z.isAirborne && !paused) {
                    val worldWidthPx = tileMap.width * unitPx
                    fun shortestDxToPlayer(fromPx: Float, toPx: Float): Float {
                        // compute minimal wrapped delta along X (normalize inputs to world width)
                        val f = ((fromPx % worldWidthPx) + worldWidthPx) % worldWidthPx
                        val t = ((toPx % worldWidthPx) + worldWidthPx) % worldWidthPx
                        var dx = t - f
                        if (abs(dx) > worldWidthPx / 2f) {
                            dx += if (dx > 0) -worldWidthPx else worldWidthPx
                        }
                        return dx
                    }

                    // Determine LoS and reachability for chase state
                    val playerXWrapped = ((playerWorldXPx % worldWidthPx) + worldWidthPx) % worldWidthPx
                    val dxToPlayer = shortestDxToPlayer(z.worldXPx, playerXWrapped)
                    val distTilesAbs = abs(dxToPlayer) / unitPx
                    val verticalTilesAbs = abs((playerBottomPx - z.bottomPx)) / unitPx
                    // Use half-world width as effective max horizontal gating; generous vertical band
                    val halfWorldTiles = tileMap.width * 0.5f
                    val inRangeRough = (distTilesAbs <= halfWorldTiles && verticalTilesAbs <= CHASE_VERTICAL_TILES)
                    var shouldChase = false
                    if (inRangeRough) {
                        shouldChase = true
                        z.aiMemoryUntilMs = nowMs + CHASE_MEMORY_MS
                    } else if (nowMs < z.aiMemoryUntilMs) {
                        // memory phase
                        shouldChase = true
                    }

                    // Derive zombie speed: slightly slower than the player's speed
                    val relSpeed = if (shouldChase) CHASE_RELATIVE_SPEED else ZOMBIE_RELATIVE_SPEED
                    val targetTilesPerSec = (playerSpeedTilesPerSec * relSpeed)
                    val baseTilesPerSec = maxOf(0.1f, minOf(targetTilesPerSec, 6.0f)) // allow higher speeds
                    val speedPxPerSec = baseTilesPerSec * z.speedMul * unitPx
                    // Frame displacement from speed
                    val moveRight = if (shouldChase) (dxToPlayer > 0f) else z.facingRight
                    val rawDx = if (moveRight) speedPxPerSec * dtSec else -speedPxPerSec * dtSec
                    // Safety cap based on tiles
                    val capPx = HORIZONTAL_CAP_TILES * unitPx
                    val dxSpeed = rawDx.coerceIn(-capPx, capPx)

                    // Respect patrol stride budget unless in chase mode (incl. memory)
                    val stridePx = if (shouldChase) Float.POSITIVE_INFINITY else z.patrolStrideRemainingPx.coerceAtLeast(0f)
                    val dxBudget = if (dxSpeed >= 0f) minOf(dxSpeed, stridePx) else -minOf(-dxSpeed, stridePx)

                    // Grid-quantized horizontal sweep with smooth motion (2x3 footprint)
                    val widthCells = 2
                    val heightCells = 3
                    fun isTilesBlocked(frontCol: Int, bottomRow: Int, supported: Boolean): Boolean {
                        // When supported by floor tiles, ignore the lowest row of our footprint so the ground
                        // underfoot does not act as a lateral blocker (matches player floor-aware logic)
                        val startRow = if (supported) bottomRow + 1 else bottomRow
                        val rows = startRow..(bottomRow + heightCells - 1)
                        val cols = frontCol..frontCol // only the band we will enter
                        for (c in cols) {
                            val wc = ((c % tileMap.width) + tileMap.width) % tileMap.width
                            for (r in rows) if (tileMap.get(wc, r)) return true
                        }
                        return false
                    }
                    fun isPlayerBlocked(frontCol: Int, bottomRow: Int): Boolean {
                        val rows = bottomRow..(bottomRow + heightCells - 1)
                        val pLeftCol = floor(playerWorldXPx / unitPx).toInt()
                        val pBottomRow = floor(playerBottomPx / unitPx).toInt()
                        val pRows = pBottomRow..(pBottomRow + heightCells - 1)
                        val rowsOverlap = !(rows.last < pRows.first || rows.first > pRows.last)
                        if (!rowsOverlap) return false
                        val pC0 = (((pLeftCol) % tileMap.width) + tileMap.width) % tileMap.width
                        val pC1 = (((pLeftCol + 1) % tileMap.width) + tileMap.width) % tileMap.width
                        val fC = (((frontCol) % tileMap.width) + tileMap.width) % tileMap.width
                        return fC == pC0 || fC == pC1
                    }
                    fun isZombieBlocked(frontCol: Int, bottomRow: Int): Boolean {
                        val rows = bottomRow..(bottomRow + heightCells - 1)
                        val fC = (((frontCol) % tileMap.width) + tileMap.width) % tileMap.width
                        val list = _zombies.value
                        if (list.isEmpty()) return false
                        for (o in list) {
                            if (o.id == id) continue
                            val oLeftCol = floor(o.worldXPx / unitPx).toInt()
                            val oBottomRow = floor(o.bottomPx / unitPx).toInt()
                            val oRows = oBottomRow..(oBottomRow + heightCells - 1)
                            val rowsOverlap = !(rows.last < oRows.first || rows.first > oRows.last)
                            if (!rowsOverlap) continue
                            val oC0 = (((oLeftCol) % tileMap.width) + tileMap.width) % tileMap.width
                            val oC1 = (((oLeftCol + 1) % tileMap.width) + tileMap.width) % tileMap.width
                            if (fC == oC0 || fC == oC1) return true
                        }
                        return false
                    }
                    fun isBlocked(frontCol: Int, bottomRow: Int, supported: Boolean): Boolean {
                        return isTilesBlocked(frontCol, bottomRow, supported) || isPlayerBlocked(frontCol, bottomRow) || isZombieBlocked(frontCol, bottomRow)
                    }

                    var leftPx = z.worldXPx
                    var remaining = dxBudget
                    var movedPx = 0f
                    var safety = 0
                    while (remaining != 0f && safety++ < 256) {
                        val movingRight = remaining > 0f
                        val leftCol = floor(leftPx / unitPx).toInt()
                        val bottomRow = floor(z.bottomPx / unitPx).toInt()
                        val frontCol = if (movingRight) leftCol + widthCells else leftCol - 1
                        val supportedNow = isSupported(tileMap, leftPx, unitPx, widthPx, z.bottomPx)
                        val tilesBlocked = isTilesBlocked(frontCol, bottomRow, supportedNow)
                        val blocked = tilesBlocked || isPlayerBlocked(frontCol, bottomRow) || isZombieBlocked(frontCol, bottomRow)
                        if (blocked) {
                            // clamp to boundary before the blocked band and stop
                            leftPx = if (movingRight) frontCol * unitPx - widthCells * unitPx else (frontCol + 1) * unitPx
                            remaining = 0f
                            break
                        }
                        val nextBoundary = if (movingRight) {
                            (floor(leftPx / unitPx).toInt() + 1) * unitPx
                        } else {
                            floor(leftPx / unitPx).toInt() * unitPx
                        }
                        val distToBoundary = kotlin.math.abs(nextBoundary - leftPx).coerceAtLeast(0.0001f)
                        val step = kotlin.math.min(kotlin.math.abs(remaining), distToBoundary)
                        leftPx += if (movingRight) step else -step
                        remaining += if (movingRight) -step else step
                    }
                    var clamped = leftPx
                    movedPx = clamped - z.worldXPx

                    // Prevent passing through the player: resolve horizontal AABB overlap vs local player
                    run {
                        val pLeft = playerWorldXPx
                        val pRight = pLeft + widthPx
                        val pBottom = playerBottomPx
                        val pTop = pBottom + heightPx
                        val myTop = z.bottomPx + heightPx
                        val verticalOverlap = !(myTop <= pBottom || z.bottomPx >= pTop)
                        if (verticalOverlap) {
                            // Consider three images for wrap-around and choose the nearest for collision resolution
                            val worldWidthPxLocal = tileMap.width * unitPx
                            val candidates = floatArrayOf(pLeft, pLeft - worldWidthPxLocal, pLeft + worldWidthPxLocal)
                            var resolved: Float? = null
                            for (candLeft in candidates) {
                                val candRight = candLeft + widthPx
                                val willOverlap = !(clamped + widthPx <= candLeft || clamped >= candRight)
                                val wasSeparated = (z.worldXPx + widthPx <= candLeft) || (z.worldXPx >= candRight)
                                if (willOverlap && wasSeparated) {
                                    resolved = if (movedPx >= 0f) {
                                        candLeft - widthPx
                                    } else {
                                        candRight
                                    }
                                    break
                                }
                            }
                            if (resolved != null) {
                                clamped = resolved!!
                                movedPx = clamped - z.worldXPx
                            }
                        }
                    }

                    z.worldXPx = clamped
                    // Keep X bounded to world width to avoid drift for off-screen roamers
                    if (z.worldXPx >= worldWidthPx || z.worldXPx < 0f) {
                        z.worldXPx = ((z.worldXPx % worldWidthPx) + worldWidthPx) % worldWidthPx
                    }

                    // Handle blockage: if movement is effectively zero, force an end and flip often
                    val blocked = abs(movedPx) < 0.05f

                    // Deplete patrol stride budget by distance actually moved (in px)
                    if (!shouldChase && z.patrolStrideRemainingPx > 0f) {
                        z.patrolStrideRemainingPx = (z.patrolStrideRemainingPx - abs(movedPx)).coerceAtLeast(0f)
                        z.stepBudget = kotlin.math.floor(z.patrolStrideRemainingPx / unitPx).toInt()
                        if (z.patrolStrideRemainingPx <= 0f || blocked) {
                            // Small pause and likely flip on blockage; mild pause at stride end
                            if (blocked) {
                                if (rng.nextFloat() < 0.8f) z.facingRight = !z.facingRight
                                z.pauseUntil = nowMs + 30L
                                z.patrolStrideRemainingPx = 0f
                                z.stepBudget = 0
                            } else {
                                z.pauseUntil = nowMs + (60L + rng.nextInt(60))
                            }
                        }
                    }

                    // In chase, if blocked, try quick flip to navigate around; no long pauses
                    if (shouldChase) {
                        z.state = NpcState.Chase
                        if (blocked) {
                            if (rng.nextFloat() < 0.7f) z.facingRight = !z.facingRight
                            z.pauseUntil = nowMs + 30L
                        } else {
                            z.pauseUntil = 0L
                        }
                    } else {
                        // Patrol state
                        z.state = if (abs(movedPx) > 0.01f) {
                            if (movedPx > 0f) NpcState.WalkRight else NpcState.WalkLeft
                        } else NpcState.Idle
                    }

                    // Ensure sprite facing reflects actual recent movement when significant
                    if (abs(movedPx) > 0.01f) {
                        z.facingRight = movedPx > 0f
                    }
                }

        // Attack the player using the same 10-cell targeting pattern as the player.
                run {
                    // Build 10 candidates around zombie bounds
                    val leftCol = floor((z.worldXPx) / unitPx).toInt()
                    val rightCol = floor((z.worldXPx + widthPx - 0.001f) / unitPx).toInt()
                    val bottomRow = floor((z.bottomPx - 0.001f) / unitPx).toInt()
                    val topRow = floor(((z.bottomPx + heightPx - 0.001f) / unitPx)).toInt()
                    val midRow = (bottomRow + topRow) / 2
                    val candidates = kotlin.collections.buildList<Cell> {
                        add(Cell(leftCol - 1, topRow))
                        add(Cell(leftCol - 1, midRow))
                        add(Cell(leftCol - 1, bottomRow))
                        add(Cell(rightCol + 1, topRow))
                        add(Cell(rightCol + 1, midRow))
                        add(Cell(rightCol + 1, bottomRow))
                        add(Cell(leftCol, topRow + 1))
                        add(Cell(rightCol, topRow + 1))
                        add(Cell(leftCol, bottomRow - 1))
                        add(Cell(rightCol, bottomRow - 1))
                    }

                    // Direction preference: vector toward player center
                    val zCenterX = z.worldXPx + widthPx / 2f
                    val zCenterY = z.bottomPx + heightPx / 2f
                    // Choose nearest wrapped player X for direction
                    val worldWidthPxLocal = tileMap.width * unitPx
                    val pLeft = playerWorldXPx
                    val pRight = pLeft + widthPx
                    val pCenterCandidates = floatArrayOf(pLeft + widthPx/2f, pLeft - worldWidthPxLocal + widthPx/2f, pLeft + worldWidthPxLocal + widthPx/2f)
                    var pCenterX = pCenterCandidates[0]
                    run {
                        var bestDx = kotlin.math.abs(pCenterX - zCenterX)
                        for (cand in pCenterCandidates) {
                            val dx = kotlin.math.abs(cand - zCenterX)
                            if (dx < bestDx) { bestDx = dx; pCenterX = cand }
                        }
                    }
                    val pCenterY = playerBottomPx + heightPx / 2f
                    val dirX = (pCenterX - zCenterX)
                    val dirY = (pCenterY - zCenterY)
                    val dirLen = kotlin.math.sqrt(dirX*dirX + dirY*dirY)
                    val ndx = if (dirLen > 0f) dirX / dirLen else 0f
                    val ndy = if (dirLen > 0f) dirY / dirLen else 0f

                    // Select best candidate by directional dot product
                    var best: Cell? = null
                    var bestScore = Float.NEGATIVE_INFINITY
                    for (c in candidates) {
                        if (!tileMap.inY(c.r)) continue
                        val cx = ((((c.c % tileMap.width) + tileMap.width) % tileMap.width) + 0.5f) * unitPx
                        val cy = (c.r + 0.5f) * unitPx
                        var vx = cx - zCenterX
                        var vy = cy - zCenterY
                        val vm = kotlin.math.sqrt(vx*vx + vy*vy)
                        if (vm > 0f) { vx /= vm; vy /= vm }
                        val score = vx*ndx + vy*ndy
                        if (score > bestScore) { bestScore = score; best = c }
                    }
                    val aimed = best
                    if (aimed != null && tileMap.inY(aimed.r)) {
                        val aimColWrapped = ((aimed.c % tileMap.width) + tileMap.width) % tileMap.width
                        val cellLeft = aimColWrapped * unitPx
                        val cellRight = cellLeft + unitPx
                        val cellBottom = aimed.r * unitPx
                        val cellTop = cellBottom + unitPx
                        // Overlap with player AABB (using chosen wrapped image)
                        var overlapping = false
                        run {
                            val pLeftBase = playerWorldXPx
                            val candLefts = floatArrayOf(pLeftBase, pLeftBase - worldWidthPxLocal, pLeftBase + worldWidthPxLocal)
                            for (pl in candLefts) {
                                val pr = pl + widthPx
                                val pb = playerBottomPx
                                val pt = pb + heightPx
                                if (!(cellRight <= pl || cellLeft >= pr || cellTop <= pb || cellBottom >= pt)) { overlapping = true; break }
                            }
                        }
                        val canAttackNow = overlapping
                        if (canAttackNow) {
                            if (!z.inAttackRange) {
                                z.inAttackRange = true
                                // wind-up before the first swing after entering range
                                z.nextAttackReadyAt = maxOf(z.nextAttackReadyAt, nowMs + ATTACK_COOLDOWN_MS)
                            }
                            if (nowMs >= z.nextAttackReadyAt) {
                                z.nextAttackReadyAt = nowMs + ATTACK_COOLDOWN_MS
                                z.state = NpcState.Attacking
                                z.attackInProgress = true
                                z.attackCastStart = nowMs
                                _playerDamage.tryEmit(ATTACK_DAMAGE)
                            }
                        } else {
                            z.inAttackRange = false
                            z.nextAttackReadyAt = nowMs + ATTACK_COOLDOWN_MS
                        }
                    } else {
                        z.inAttackRange = false
                    }
                }

                // Prevent zombie–zombie pass-through: resolve horizontal overlaps deterministically
                run {
                    val list = _zombies.value
                    if (list.size > 1) {
                        for (other in list) {
                            if (other.id == id) continue
                            // Only resolve if vertical AABBs overlap
                            val myTop = z.bottomPx + heightPx
                            val oTop = other.bottomPx + heightPx
                            val verticalOverlap = !(myTop <= other.bottomPx || z.bottomPx >= oTop)
                            if (!verticalOverlap) continue
                            // Consider wrap images of other; resolve against the nearest image that overlaps
                            val worldWidthPxLocal = tileMap.width * unitPx
                            val otherLeftBase = other.worldXPx
                            val candidates = floatArrayOf(otherLeftBase, otherLeftBase - worldWidthPxLocal, otherLeftBase + worldWidthPxLocal)
                            for (ol in candidates) {
                                val oright = ol + widthPx
                                val willOverlap = !(z.worldXPx + widthPx <= ol || z.worldXPx >= oright)
                                if (willOverlap) {
                                    // Separate along our recent movement direction (approx by facingRight)
                                    if (z.facingRight) {
                                        z.worldXPx = ol - widthPx
                                    } else {
                                        z.worldXPx = oright
                                    }
                                    break
                                }
                            }
                        }
                    }
                }

                // Publish update
                _zombies.update { list ->
                    val newList = list.toMutableList()
                    val i = newList.indexOfFirst { it.id == id }
                    if (i != -1) {
                        // Align worldXDp to px for consistency
                        z.worldXDp = pxToDp(z.worldXPx)
                        newList[i] = z
                    }
                    newList
                }

                // Exit if dead or removed
                local = _zombies.value.firstOrNull { it.id == id }
                delay(16L)
            }
        }
        zombieJobs[id] = job
    }

    fun clear() {
        zombieJobs.values.forEach { it.cancel() }
        zombieJobs.clear()
        skeletonJobs.values.forEach { it.cancel() }
        skeletonJobs.clear()
        scope.cancel()
    }
}
