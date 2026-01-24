package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jamescullimore.dontgotobed.Direction.Companion.isFacingRight
import dev.jamescullimore.dontgotobed.ui.theme.DontGoToBedTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Random
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun GameScreen(
    player: Player,
    initialIsHostSelected: Boolean? = null,
    initialHostIp: String = MultiplayerManager.DEFAULT_HOST_IP,
    mp: MultiplayerManager?
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        // Lightweight state holder moved out of the composable to simplify this file
        val vm = remember { GameViewModel() }

        var isHostSelected by remember { mutableStateOf<Boolean?>(initialIsHostSelected) }
        var connectionStatus by remember { mutableStateOf(false) }
        var hostIp by remember { mutableStateOf(initialHostIp) }

        val unit = 15.dp
        val step = 16.dp
        val playerWidth = unit * 2
        val playerHeight = unit * 3
        val centerX = (maxWidth - playerWidth) / 2
        val screenWidth = maxWidth

        var joystickX by remember { mutableFloatStateOf(0f) }
        var joystickY by remember { mutableFloatStateOf(0f) }
        var previewSelectedAimCol by remember { mutableStateOf<Int?>(null) }
        var previewSelectedAimRow by remember { mutableStateOf<Int?>(null) }
        var previewSelectedFacing by remember { mutableStateOf<Direction?>(null) }

        val unitPx = with(density) { unit.toPx() }
        val playerWidthPx = with(density) { playerWidth.toPx() }
        val playerHeightPx = with(density) { playerHeight.toPx() }

        // Inform the VM about the player's horizontal speed (tiles/sec) so zombies can be slightly slower
        LaunchedEffect(unitPx) {
            val maxSpeedPxPerSec = with(density) { 420.dp.toPx() }
            val playerTilesPerSec = if (unitPx > 0f) maxSpeedPxPerSec / unitPx else 1f
            vm.setPlayerSpeedTilesPerSec(playerTilesPerSec)
        }

        // (moved below state declarations where helpers/vars are available)

        var playerHp by remember { mutableIntStateOf(5) }
        var playerFlashUntil by remember { mutableLongStateOf(0L) }

        val inventorySlots = vm.inventorySlots
        val inventoryStackLimit = vm.inventoryStackLimit
        val inventory = vm.inventory

        val zombies by vm.zombies.collectAsState()
        val skeletons by vm.skeletons.collectAsState()
        val arrows by vm.arrows.collectAsState()
        val potions by vm.potions.collectAsState()
        // Ticker to force recomposition for NPC visuals regardless of player input
        var npcFrameTick by remember { mutableStateOf(0) }

        // Listen for zombie attack damage events from ViewModel
        LaunchedEffect(Unit) {
            vm.playerDamage.collect { dmg ->
                if (dmg > 0) {
                    playerHp = (playerHp - dmg).coerceAtLeast(0)
                    playerFlashUntil = System.currentTimeMillis() + 150L
                }
            }
        }

        // (moved below after tileMap and movement vars are available)

        // Build sample map with 3x3 blocks on micro-grid (scale prior grid by 3)
        val tileMap by remember(maxWidth) {
            mutableStateOf(
                run {
                    val w = 64 * 3
                    val h = 18 * 3
                    val map = TileMap(w, h)
                    // Ground band (rows 0..2) tiled with adjacent 3x3 blocks
                    for (x in 0 until w step 3) map.placeBlock(x, 0, 3, 3, Color(0xFF2E8B57))
                    // Stacks and platforms analogous to previous positions (scaled by 3)
                    map.placeBlock(10*3, 1*3, 3, 3, Color(0xFF2E8B57))
                    map.placeBlock(10*3, 2*3, 3, 3, Color(0xFF2E8B57))
                    map.placeBlock(10*3, 3*3, 3, 3, Color(0xFF2E8B57))
                    map.placeBlock(18*3, 4*3, 3, 3, Color(0xFF2E8B57))
                    map.placeBlock(19*3, 4*3, 3, 3, Color(0xFF2E8B57))
                    map.placeBlock(20*3, 4*3, 3, 3, Color(0xFF2E8B57))
                    map.placeBlock(26*3, 1*3, 3, 3, Color(0xFF2E8B57))
                    map.placeBlock(27*3, 2*3, 3, 3, Color(0xFF2E8B57))
                    map.placeBlock(28*3, 3*3, 3, 3, Color(0xFF2E8B57))
                    // Gap in ground at 36..38 previously: remove those blocks in ground band
                    // By not placing at those x positions the ground band will have a gap
                    // Bridge above at row 2*3
                    map.placeBlock(36*3, 2*3, 3, 3, Color(0xFF2E8B57))
                    map.placeBlock(37*3, 2*3, 3, 3, Color(0xFF2E8B57))
                    map.placeBlock(38*3, 2*3, 3, 3, Color(0xFF2E8B57))
                    map.placeBlock(46*3, 1*3, 3, 3, Color(0xFF2E8B57))
                    map.placeBlock(46*3, 2*3, 3, 3, Color(0xFF2E8B57))
                    map
                }
            )
        }

        // moved below: requires playerWorldXDp/heightPx/isJumping which are declared later

        // Helpers: convert world x dp to pixel and back (must be declared before any usage)
        fun xDpToPx(x: Dp): Float = with(density) { x.toPx() }
        fun pxToDp(px: Float): Dp = with(density) { px.toDp() }

        // Player state
        var playerWorldXDp by remember { mutableStateOf(0.dp) } // horizontal position in world space
        var heightPx by remember { mutableStateOf(0f) } // vertical bottom height in px above ground
        var isJumping by remember { mutableStateOf(false) }

        // If a zombie under the player dies and is removed, re-check support so the player starts to fall
        LaunchedEffect(zombies) {
            // Determine if player is supported by tiles directly under their feet
            val cols = run {
                val xPx = xDpToPx(playerWorldXDp)
                val c0 = floor(xPx / unitPx).toInt()
                val c1 = floor((xPx + playerWidthPx - 0.001f) / unitPx).toInt()
                c0..c1
            }
            val rowUnder = floor((heightPx - 0.001f) / unitPx).toInt()
            var supportedByTiles = false
            if (tileMap.inY(rowUnder)) {
                for (c in cols) { if (tileMap.get(c, rowUnder)) { supportedByTiles = true; break } }
            }
            if (!supportedByTiles) {
                // Not supported by tiles; clear support to engage gravity next frame
                isJumping = false
                // Nudge slightly to ensure fall loop detects movement
                heightPx = (heightPx - 0.01f).coerceAtLeast(0f)
            }
        }
        var lastDirection by remember { mutableStateOf(Direction.Right) }
        var isPaused by remember { mutableStateOf(false) }

        // Remote peers map (id -> state)
        val peersState = vm.peersState

        // Lightweight notifications banner (join/leave/connection)
        val notices = vm.notices
        fun pushNotice(text: String) { vm.pushNotice(text) }

        // Increment to trigger recomposition when map content changes (e.g., tile destroyed)
        var mapVersion by remember { mutableStateOf(0) }

        // One-time spawn: start above the map and let gravity bring the player down onto the next platform or ground

        // Helpers are already declared above

        var didInitialSpawn by remember { mutableStateOf(false) }

        // Continuously report player's current world position to the ViewModel for zombie chasing
        LaunchedEffect(playerWorldXDp, heightPx, unitPx) {
            // Send immediately on any dependency change
            vm.setPlayerPositionPx(xDpToPx(playerWorldXDp), heightPx)
        }
        
        fun normalizeAngleDeg(a: Float): Float {
            var d = a % 360f
            if (d < 0f) d += 360f
            return d
        }
        fun angleToDirection(degRaw: Float): Direction {
            val d = normalizeAngleDeg(degRaw)
            return when (d) {
                !in 18f..<342f -> Direction.Right
                in 18f..<54f -> Direction.RightUp
                in 54f..<90f -> Direction.UpRight
                in 90f..<126f -> Direction.UpLeft
                in 126f..<162f -> Direction.LeftUp
                in 162f..<198f -> Direction.Left
                in 198f..<234f -> Direction.LeftDown
                in 234f..<270f -> Direction.DownLeft
                in 270f..<306f -> Direction.DownRight
                else -> Direction.RightDown // 306°..342°
            }
        }
        fun vectorToDirection(dx: Float, dy: Float): Direction {
            if (dx == 0f && dy == 0f) return lastDirection
            val angleRad = atan2(-dy, dx)
            val deg = Math.toDegrees(angleRad.toDouble()).toFloat()
            return angleToDirection(deg)
        }

        // Convert a 10-sector Direction to a unit vector (screen space: Up = negative Y).
        // Sectors are centered every 36°: Right=0°, RightUp=36°, UpRight=72°, UpLeft=108°,
        // LeftUp=144°, Left=180°, LeftDown=216°, DownLeft=252°, DownRight=288°, RightDown=324°.
        fun directionToVector(d: Direction): Pair<Float, Float> {
            val deg = when (d) {
                Direction.Right -> 0f
                Direction.RightUp -> 36f
                Direction.UpRight -> 72f
                Direction.UpLeft -> 108f
                Direction.LeftUp -> 144f
                Direction.Left -> 180f
                Direction.LeftDown -> 216f
                Direction.DownLeft -> 252f
                Direction.DownRight -> 288f
                Direction.RightDown -> 324f
            }
            val rad = Math.toRadians(deg.toDouble())
            val x = cos(rad).toFloat()
            val y = (-sin(rad)).toFloat()
            return x to y
        }

        fun respawnPlayerRandom() {
            val rng = Random()
            repeat(200) {
                val col = rng.nextInt(tileMap.width)
                var topRow: Int? = null
                for (r in (tileMap.height - 1) downTo 0) {
                    if (tileMap.get(col, r)) { topRow = r; break }
                }
                if (topRow != null) {
                    val groundTop = (topRow + 1) * unitPx
                    playerWorldXDp = pxToDp(col * unitPx)
                    heightPx = groundTop
                    isJumping = false
                    return
                }
            }
            // Fallback
            playerWorldXDp = 0.dp
            heightPx = 0f
            isJumping = false
        }

        fun spawnZombieRandom() {
            val pLeftPx = xDpToPx(playerWorldXDp)
            val pRightPx = pLeftPx + playerWidthPx
            vm.spawnZombieRandom(
                tileMap = tileMap,
                unitPx = unitPx,
                playerWidthPx = playerWidthPx,
                playerHeightPx = playerHeightPx,
                avoidLeftPx = pLeftPx,
                avoidRightPx = pRightPx,
                pxToDp = ::pxToDp
            )
        }

        fun spawnSkeletonRandom() {
            val pLeftPx = xDpToPx(playerWorldXDp)
            val pRightPx = pLeftPx + playerWidthPx
            vm.spawnSkeletonRandom(
                tileMap = tileMap,
                unitPx = unitPx,
                playerWidthPx = playerWidthPx,
                playerHeightPx = playerHeightPx,
                avoidLeftPx = pLeftPx,
                avoidRightPx = pRightPx,
                pxToDp = ::pxToDp
            )
        }

        LaunchedEffect(tileMap.width) {
            if (!didInitialSpawn) {
                // Position player high above the highest tiles so they "fall in" from the top
                heightPx = (tileMap.height * unitPx) + playerHeightPx * 2f
                // Keep isJumping false so the existing unsupported check triggers the falling coroutine
                isJumping = false
                didInitialSpawn = true

                repeat(2) { spawnZombieRandom() }
                repeat(2) { spawnSkeletonRandom() }
            }
        }

        // Respawn flow: when HP hits 0, clear inventory, restore hearts, and fall in from the top at the start
        LaunchedEffect(playerHp) {
            if (playerHp <= 0) {
                // Clear inventory (no items on respawn)
                for (i in 0 until inventorySlots) {
                    val s = inventory[i]
                    s.type = null
                    s.count = 0
                }
                vm.selectedSlot.intValue = 0

                // Reset hearts to full
                playerHp = 5

                // Teleport to start X and high Y, let gravity bring the player down
                playerWorldXDp = 0.dp
                heightPx = (tileMap.height * unitPx) + playerHeightPx * 2f
                isJumping = false

                // Reset aim/preview states
                previewSelectedAimCol = null
                previewSelectedAimRow = null
                previewSelectedFacing = null
            }
        }


        // Simple helper to compute overlapping columns/rows for an arbitrary AABB bottom and left in px
        fun aabbOverlappingColumns(leftPx: Float): IntRange {
            val c0 = floor(leftPx / unitPx).toInt()
            val c1 = floor((leftPx + playerWidthPx - 0.001f) / unitPx).toInt()
            return c0..c1
        }
        fun aabbOverlappingRows(bottomPx: Float): IntRange {
            val r0 = floor((bottomPx - 0.001f) / unitPx).toInt()
            val r1 = floor(((bottomPx + playerHeightPx - 0.001f) / unitPx)).toInt()
            return r0..r1
        }

        fun playerOverlappingColumns(xPx: Float): IntRange {
            val right = xPx + playerWidthPx - 0.001f
            val c0 = floor(xPx / unitPx).toInt()
            val c1 = floor(right / unitPx).toInt()
            return c0..c1
        }
        fun playerOverlappingRows(bottomPx: Float): IntRange {
            val top = bottomPx + playerHeightPx - 0.001f
            val r0 = floor(bottomPx / unitPx).toInt()
            val r1 = floor(top / unitPx).toInt()
            return r0..r1
        }

        // Potion collection logic
        LaunchedEffect(playerWorldXDp, heightPx, potions) {
            val pLeftPx = xDpToPx(playerWorldXDp)
            val pRows = playerOverlappingRows(heightPx)
            val pCols = playerOverlappingColumns(pLeftPx)
            potions.forEach { potion ->
                // Check if potion col/row is within player's footprint (wrap-aware)
                val inCol = pCols.any { ((it % tileMap.width) + tileMap.width) % tileMap.width == potion.col }
                if (inCol && potion.row in pRows) {
                    if (vm.collectPotion(potion.id)) {
                        pushNotice("Potion +1")
                    }
                }
            }
        }
        fun anySolidInColumnsRows(cols: IntRange, rows: IntRange): Boolean {
            for (c in cols) for (r in rows) if (tileMap.get(c, r)) return true
            return false
        }

        fun losClearSameRow(colFrom: Int, colTo: Int, row: Int): Boolean {
            val minC = min(colFrom, colTo)
            val maxC = max(colFrom, colTo)
            // Exclude the endpoints (occupied by actor AABBs); check strictly between
            if (maxC - minC <= 1) return true
            for (c in (minC + 1) until maxC) { if (tileMap.get(c, row)) return false }
            return true
        }

        fun npcApplyHorizontalCollision(prevPx: Float, nextPx: Float, bottomPx: Float): Float {
            var nxPx = nextPx
            val movingRight = nxPx > prevPx
            val trim = 2f
            val rows: IntRange = run {
                val bottom = (bottomPx + trim).coerceAtLeast(0f)
                val top = (bottomPx + playerHeightPx - trim).coerceAtLeast(bottom)
                val r0 = floor(bottom / unitPx).toInt()
                val r1 = floor(top / unitPx).toInt()
                r0..r1
            }
            if (movingRight) {
                val rightEdge = nxPx + playerWidthPx - 0.001f
                val col = floor(rightEdge / unitPx).toInt()
                if (anySolidInColumnsRows(col..col, rows)) {
                    val tileLeftPx = col * unitPx
                    nxPx = tileLeftPx - playerWidthPx
                }
            } else if (nxPx < prevPx) {
                val leftEdge = nxPx + 0.001f
                val col = floor(leftEdge / unitPx).toInt()
                if (anySolidInColumnsRows(col..col, rows)) {
                    val tileRightPx = (col + 1) * unitPx
                    nxPx = tileRightPx
                }
            }
            // Collide with local player AABB to avoid passing through
            val pLeft = xDpToPx(playerWorldXDp)
            val pRight = pLeft + playerWidthPx
            val pBottom = heightPx
            val pTop = pBottom + playerHeightPx
            val myTop = bottomPx + playerHeightPx
            val verticalOverlap = !(myTop <= pBottom || bottomPx >= pTop)
            if (verticalOverlap) {
                if (movingRight) {
                    val myRight = nxPx + playerWidthPx
                    if (myRight > pLeft && prevPx + playerWidthPx <= pLeft) nxPx = pLeft - playerWidthPx
                } else if (nxPx < prevPx) {
                    val myLeft = nxPx
                    if (myLeft < pRight && prevPx >= pRight) nxPx = pRight
                }
            }
            val worldWidthPx = tileMap.width * unitPx
            nxPx = ((nxPx % worldWidthPx) + worldWidthPx) % worldWidthPx
            return nxPx
        }

        // Horizontal collision using tile grid with wrap (Px version, no normalization)
        fun applyHorizontalCollisionPx(prevPx: Float, desiredNextPx: Float, bottomPx: Float): Float {
            var nxPx = desiredNextPx
            val movingRight = nxPx > prevPx
            // Use trimmed vertical span to allow threading small gaps while jumping.
            // Increase trim when airborne to avoid corner catches. Slightly higher to ensure 2-grid shafts are passable.
            val trim = max(2f, (unitPx * if (isJumping) 0.5f else 0.2f))
            val rows: IntRange = run {
                val bottom = (bottomPx + trim).coerceAtLeast(0f)
                val top = (bottomPx + playerHeightPx - trim).coerceAtLeast(bottom)
                val r0 = floor(bottom / unitPx).toInt()
                val r1 = floor(top / unitPx).toInt()
                r0..r1
            }
            if (movingRight) {
                val rightEdge = nxPx + playerWidthPx - 0.001f
                val col = floor(rightEdge / unitPx).toInt()
                // If the leading column has any solid in our rows, clamp to its left edge
                val cols = col..col
                if (anySolidInColumnsRows(cols, rows)) {
                    val tileLeftPx = col * unitPx
                    nxPx = tileLeftPx - playerWidthPx
                }
            } else if (nxPx < prevPx) {
                val leftEdge = nxPx + 0.001f // small epsilon to avoid false block when exactly aligned
                val col = floor(leftEdge / unitPx).toInt()
                val cols = col..col
                if (anySolidInColumnsRows(cols, rows)) {
                    val tileRightPx = (col + 1) * unitPx
                    nxPx = tileRightPx
                }
            }
            // Also collide with remote peers (no pass-through). Local player has priority.
            if (connectionStatus) {
                val myTop = bottomPx + playerHeightPx
                val epsV = 0.75f
                peersState.values.forEach { peer ->
                    val px = peer.worldXPx
                    val pRight = px + playerWidthPx
                    val pBottom = peer.heightPx
                    val pTop = peer.heightPx + playerHeightPx
                    val verticalOverlap = !((myTop - epsV) <= pBottom || (bottomPx + epsV) >= pTop)
                    if (!verticalOverlap) return@forEach
                    if (movingRight) {
                        val myRight = nxPx + playerWidthPx
                        if (myRight > px && prevPx + playerWidthPx <= px) {
                            nxPx = px - playerWidthPx
                        }
                    } else if (nxPx < prevPx) {
                        val myLeft = nxPx
                        if (myLeft < pRight && prevPx >= pRight) {
                            nxPx = pRight
                        }
                    }
                }
            }
            // Also collide with zombie NPCs (no pass-through) with wrap-aware images
            if (zombies.isNotEmpty()) {
                val myTop = bottomPx + playerHeightPx
                val epsV = 0.75f
                val worldWidthPx = tileMap.width * unitPx
                zombies.forEach { z ->
                    val zBottom = z.bottomPx
                    val zTop = zBottom + playerHeightPx
                    val verticalOverlap = !((myTop - epsV) <= zBottom || (bottomPx + epsV) >= zTop)
                    if (!verticalOverlap) return@forEach
                    // Consider three images of the zombie along X to account for wrap-around
                    val baseLeft = z.worldXPx
                    val candidates = floatArrayOf(baseLeft, baseLeft - worldWidthPx, baseLeft + worldWidthPx)
                    // Resolve against the first image that our sweep crosses this frame
                    for (zLeft in candidates) {
                        val zRight = zLeft + playerWidthPx
                        if (movingRight) {
                            val myRight = nxPx + playerWidthPx
                            val wasSeparated = (prevPx + playerWidthPx) <= zLeft
                            val willOverlap = myRight > zLeft
                            if (wasSeparated && willOverlap) {
                                nxPx = zLeft - playerWidthPx
                                break
                            }
                        } else if (nxPx < prevPx) {
                            val myLeft = nxPx
                            val wasSeparated = prevPx >= zRight
                            val willOverlap = myLeft < zRight
                            if (wasSeparated && willOverlap) {
                                nxPx = zRight
                                break
                            }
                        }
                    }
                }
            }
            return nxPx
        }

        // Backwards-compatible wrapper that normalizes and converts to Dp (kept for any other usages)
        fun applyHorizontalCollision(prevXDp: Dp, nextXDp: Dp, bottomPx: Float): Dp {
            val collidedPx = applyHorizontalCollisionPx(xDpToPx(prevXDp), xDpToPx(nextXDp), bottomPx)
            val worldWidthPx = tileMap.width * unitPx
            val norm = ((collidedPx % worldWidthPx) + worldWidthPx) % worldWidthPx
            return pxToDp(norm)
        }

        // --- Grid-quantized horizontal movement (2x3 cell footprint), smooth visuals ---
        // Player and zombies are 2 cells wide by 3 cells high. We keep pixel movement but
        // only allow crossing grid boundaries when the next cell band is unoccupied.
        fun colsRowsForFootprint(leftPx: Float, bottomPx: Float): Pair<IntRange, IntRange> {
            val leftCol = floor(leftPx / unitPx).toInt()
            val bottomRow = floor(bottomPx / unitPx).toInt()
            val cols = leftCol..(leftCol + 1)
            val rows = bottomRow..(bottomRow + 2)
            return cols to rows
        }

        fun isFootprintBlockedByTiles(leftCol: Int, bottomRow: Int): Boolean {
            val rows = bottomRow..(bottomRow + 2)
            val cols = leftCol..(leftCol + 1)
            for (c in cols) {
                val wc = ((c % tileMap.width) + tileMap.width) % tileMap.width
                for (r in rows) if (tileMap.get(wc, r)) return true
            }
            return false
        }

        // Determine if player is currently supported by tiles directly under feet
        fun isSupportedByTiles(leftPx: Float, bottomPx: Float): Boolean {
            val cols = run {
                val c0 = floor(leftPx / unitPx).toInt()
                val c1 = floor((leftPx + playerWidthPx - 0.001f) / unitPx).toInt()
                c0..c1
            }
            val rowUnder = floor((bottomPx - 0.001f) / unitPx).toInt()
            if (!tileMap.inY(rowUnder)) return false
            for (c in cols) if (tileMap.get(c, rowUnder)) return true
            return false
        }

        // Front-band blockers (single entering column) for grid-quantized horizontal movement
        fun isFrontBandBlockedByTiles(frontCol: Int, bottomRow: Int): Boolean {
            // When supported by floor tiles, ignore the lowest row to avoid treating the floor as a side blocker
            val startRow = if (isSupportedByTiles(xDpToPx(playerWorldXDp), heightPx)) bottomRow + 1 else bottomRow
            val rows = startRow..(bottomRow + 2)
            val wc = ((frontCol % tileMap.width) + tileMap.width) % tileMap.width
            for (r in rows) if (tileMap.get(wc, r)) return true
            return false
        }

        fun isFootprintBlockedByZombies(leftCol: Int, bottomRow: Int): Boolean {
            if (zombies.isEmpty()) return false
            val rows = bottomRow..(bottomRow + 2)
            val colsSet = hashSetOf<Int>()
            for (c in leftCol..(leftCol + 1)) colsSet.add(((c % tileMap.width) + tileMap.width) % tileMap.width)
            zombies.forEach { z ->
                val zLeftCol = floor(z.worldXPx / unitPx).toInt()
                val zBottomRow = floor(z.bottomPx / unitPx).toInt()
                val zCols = (((zLeftCol) % tileMap.width) + tileMap.width) % tileMap.width .. (((zLeftCol + 1) % tileMap.width) + tileMap.width) % tileMap.width
                val zRows = zBottomRow..(zBottomRow + 2)
                // rows intersect?
                val rowsOverlap = !(rows.last < zRows.first || rows.first > zRows.last)
                if (!rowsOverlap) return@forEach
                // columns intersect modulo wrap
                if (colsSet.contains((((zLeftCol) % tileMap.width) + tileMap.width) % tileMap.width) ||
                    colsSet.contains((((zLeftCol + 1) % tileMap.width) + tileMap.width) % tileMap.width)) {
                    return true
                }
            }
            return false
        }

        fun isFrontBandBlockedByZombies(frontCol: Int, bottomRow: Int): Boolean {
            if (zombies.isEmpty()) return false
            val rows = bottomRow..(bottomRow + 2)
            val fC = ((frontCol % tileMap.width) + tileMap.width) % tileMap.width
            zombies.forEach { z ->
                val zLeftCol = floor(z.worldXPx / unitPx).toInt()
                val zBottomRow = floor(z.bottomPx / unitPx).toInt()
                val zRows = zBottomRow..(zBottomRow + 2)
                val rowsOverlap = !(rows.last < zRows.first || rows.first > zRows.last)
                if (!rowsOverlap) return@forEach
                val zC0 = ((zLeftCol % tileMap.width) + tileMap.width) % tileMap.width
                val zC1 = (((zLeftCol + 1) % tileMap.width) + tileMap.width) % tileMap.width
                if (fC == zC0 || fC == zC1) return true
            }
            return false
        }

        fun isFootprintBlockedBySkeletons(leftCol: Int, bottomRow: Int): Boolean {
            if (skeletons.isEmpty()) return false
            val rows = bottomRow..(bottomRow + 2)
            val colsSet = hashSetOf<Int>()
            for (c in leftCol..(leftCol + 1)) colsSet.add(((c % tileMap.width) + tileMap.width) % tileMap.width)
            skeletons.forEach { s ->
                val sLeftCol = floor(s.worldXPx / unitPx).toInt()
                val sBottomRow = floor(s.bottomPx / unitPx).toInt()
                val sRows = sBottomRow..(sBottomRow + 2)
                val rowsOverlap = !(rows.last < sRows.first || rows.first > sRows.last)
                if (!rowsOverlap) return@forEach
                if (colsSet.contains((((sLeftCol) % tileMap.width) + tileMap.width) % tileMap.width) ||
                    colsSet.contains((((sLeftCol + 1) % tileMap.width) + tileMap.width) % tileMap.width)) {
                    return true
                }
            }
            return false
        }

        fun isFrontBandBlockedBySkeletons(frontCol: Int, bottomRow: Int): Boolean {
            if (skeletons.isEmpty()) return false
            val rows = bottomRow..(bottomRow + 2)
            val fC = ((frontCol % tileMap.width) + tileMap.width) % tileMap.width
            skeletons.forEach { s ->
                val sLeftCol = floor(s.worldXPx / unitPx).toInt()
                val sBottomRow = floor(s.bottomPx / unitPx).toInt()
                val sRows = sBottomRow..(sBottomRow + 2)
                val rowsOverlap = !(rows.last < sRows.first || rows.first > sRows.last)
                if (!rowsOverlap) return@forEach
                val sC0 = ((sLeftCol % tileMap.width) + tileMap.width) % tileMap.width
                val sC1 = (((sLeftCol + 1) % tileMap.width) + tileMap.width) % tileMap.width
                if (fC == sC0 || fC == sC1) return true
            }
            return false
        }

        fun isFootprintBlockedByPeers(leftCol: Int, bottomRow: Int): Boolean {
            if (!connectionStatus) return false
            val rows = bottomRow..(bottomRow + 2)
            val colsSet = hashSetOf<Int>()
            for (c in leftCol..(leftCol + 1)) colsSet.add(((c % tileMap.width) + tileMap.width) % tileMap.width)
            peersState.values.forEach { p ->
                val pLeftCol = floor(p.worldXPx / unitPx).toInt()
                val pBottomRow = floor(p.heightPx / unitPx).toInt()
                val pRows = pBottomRow..(pBottomRow + 2)
                val rowsOverlap = !(rows.last < pRows.first || rows.first > pRows.last)
                if (!rowsOverlap) return@forEach
                val c0 = (((pLeftCol) % tileMap.width) + tileMap.width) % tileMap.width
                val c1 = (((pLeftCol + 1) % tileMap.width) + tileMap.width) % tileMap.width
                if (colsSet.contains(c0) || colsSet.contains(c1)) return true
            }
            return false
        }

        fun isFrontBandBlockedByPeers(frontCol: Int, bottomRow: Int): Boolean {
            if (!connectionStatus) return false
            val rows = bottomRow..(bottomRow + 2)
            val fC = ((frontCol % tileMap.width) + tileMap.width) % tileMap.width
            peersState.values.forEach { p ->
                val pLeftCol = floor(p.worldXPx / unitPx).toInt()
                val pBottomRow = floor(p.heightPx / unitPx).toInt()
                val pRows = pBottomRow..(pBottomRow + 2)
                val rowsOverlap = !(rows.last < pRows.first || rows.first > pRows.last)
                if (!rowsOverlap) return@forEach
                val c0 = (((pLeftCol) % tileMap.width) + tileMap.width) % tileMap.width
                val c1 = (((pLeftCol + 1) % tileMap.width) + tileMap.width) % tileMap.width
                if (fC == c0 || fC == c1) return true
            }
            return false
        }

        fun isBlockedAt(leftCol: Int, bottomRow: Int): Boolean {
            return isFootprintBlockedByTiles(leftCol, bottomRow) ||
                    isFootprintBlockedByZombies(leftCol, bottomRow) ||
                    isFootprintBlockedBySkeletons(leftCol, bottomRow) ||
                    isFootprintBlockedByPeers(leftCol, bottomRow)
        }

        fun isFrontBandBlocked(frontCol: Int, bottomRow: Int): Boolean {
            return isFrontBandBlockedByTiles(frontCol, bottomRow) ||
                    isFrontBandBlockedByZombies(frontCol, bottomRow) ||
                    isFrontBandBlockedBySkeletons(frontCol, bottomRow) ||
                    isFrontBandBlockedByPeers(frontCol, bottomRow)
        }

        fun moveHorizontalSubStepped(currentXDp: Dp, deltaPx: Float, bottomPx: Float): Dp {
            if (deltaPx == 0f) return currentXDp
            val widthCells = 2
            var leftPx = xDpToPx(currentXDp)
            var remaining = deltaPx
            var guard = 0
            while (remaining != 0f && guard++ < 256) {
                val movingRight = remaining > 0f
                val leftCol = floor(leftPx / unitPx).toInt()
                val bottomRow = floor(bottomPx / unitPx).toInt()
                // Front column we would enter next
                val frontCol = if (movingRight) leftCol + widthCells else leftCol - 1
                // If the next band is blocked, clamp and stop
                if (isFrontBandBlocked(frontCol, bottomRow)) {
                    // Clamp to the boundary before the blocked band. Wrap-aware by using raw cols, then normalize at the end.
                    leftPx = if (movingRight) {
                        frontCol * unitPx - widthCells * unitPx
                    } else {
                        (frontCol + 1) * unitPx
                    }
                    remaining = 0f
                    break
                }
                // Distance to the next vertical grid line for left edge
                val nextBoundary = if (movingRight) {
                    (floor(leftPx / unitPx).toInt() + 1) * unitPx
                } else {
                    // When exactly on a boundary moving left, ensure progress by stepping to the previous line
                    val base = floor(leftPx / unitPx).toInt()
                    if (kotlin.math.abs(leftPx - base * unitPx) < 0.00001f) (base - 1) * unitPx else base * unitPx
                }
                val rawDist = kotlin.math.abs(nextBoundary - leftPx)
                val distToBoundary = if (rawDist < 0.0001f) 0.0001f else rawDist
                val step = kotlin.math.min(kotlin.math.abs(remaining), distToBoundary)
                leftPx += if (movingRight) step else -step
                remaining += if (movingRight) -step else step
            }
            // Normalize once at the end (wrap)
            val worldWidthPx = tileMap.width * unitPx
            val norm = ((leftPx % worldWidthPx) + worldWidthPx) % worldWidthPx
            return pxToDp(norm)
        }

        // Core action helpers (host-authoritative). They return true if world changed.
        fun performHitAt(xDp: Dp, bottomPx: Float, facing: Direction, aimCol: Int? = null, aimRow: Int? = null): Boolean {
            val xPx = xDpToPx(xDp)
            val centerXPx = xPx + playerWidthPx / 2f
            val colCenter = floor(centerXPx / unitPx).toInt()
            // Determine edge-adjacent target cell based on facing (use player edges, not center)
            val leftCol = floor((xPx) / unitPx).toInt()
            val rightCol = floor((xPx + playerWidthPx - 0.001f) / unitPx).toInt()
            val bottomRow = floor((bottomPx - 0.001f) / unitPx).toInt()
            val topRow = floor(((bottomPx + playerHeightPx - 0.001f) / unitPx)).toInt()
            val midRow = (bottomRow + topRow) / 2
            var targetCol: Int
            var targetRow: Int
            // If an aimed adjacent cell is provided, use it directly
            if (aimCol != null && aimRow != null) {
                val centerBand = (colCenter - 1)..(colCenter + 1)
                val isAdjacent = (
                        (aimCol == leftCol - 1 && aimRow in bottomRow..topRow) ||
                                (aimCol == rightCol + 1 && aimRow in bottomRow..topRow) ||
                                (aimRow == topRow + 1 && aimCol in centerBand) ||
                                (aimRow == bottomRow - 1 && aimCol in centerBand)
                        )
                if (isAdjacent && tileMap.inY(aimRow)) {
                    targetCol = aimCol
                    targetRow = aimRow
                } else {
                    // Fall back to facing-based targeting if aim isn't valid
                    when (facing) {
                        Direction.Left -> { targetCol = leftCol - 1; targetRow = midRow }
                        Direction.Right -> { targetCol = rightCol + 1; targetRow = midRow }
                        Direction.LeftUp -> { targetCol = leftCol - 1; targetRow = topRow }
                        Direction.UpLeft -> { targetCol = leftCol; targetRow = topRow + 1 }
                        Direction.RightUp -> { targetCol = rightCol + 1; targetRow = topRow }
                        Direction.UpRight -> { targetCol = rightCol; targetRow = topRow + 1 }
                        Direction.LeftDown -> { targetCol = leftCol - 1; targetRow = bottomRow}
                        Direction.DownLeft -> { targetCol = leftCol; targetRow = bottomRow - 1 }
                        Direction.RightDown -> { targetCol = rightCol + 1; targetRow = bottomRow}
                        Direction.DownRight -> { targetCol = rightCol; targetRow = bottomRow - 1 }
                    }
                }
            } else {
                when (facing) {
                    Direction.Left -> { targetCol = leftCol - 1; targetRow = midRow }
                    Direction.Right -> { targetCol = rightCol + 1; targetRow = midRow }
                    Direction.LeftUp -> { targetCol = leftCol - 1; targetRow = topRow }
                    Direction.UpLeft -> { targetCol = leftCol; targetRow = topRow + 1 }
                    Direction.RightUp -> { targetCol = rightCol + 1; targetRow = topRow }
                    Direction.UpRight -> { targetCol = rightCol; targetRow = topRow + 1 }
                    Direction.LeftDown -> { targetCol = leftCol - 1; targetRow = bottomRow}
                    Direction.DownLeft -> { targetCol = leftCol; targetRow = bottomRow - 1 }
                    Direction.RightDown -> { targetCol = rightCol + 1; targetRow = bottomRow}
                    Direction.DownRight -> { targetCol = rightCol; targetRow = bottomRow - 1 }
                }
            }
            if (!tileMap.inY(targetRow)) return false
            // Wrap X for cell computations
            val wrappedX = ((targetCol % tileMap.width) + tileMap.width) % tileMap.width
            var anythingHit = false

            // Damage tile if present
            if (tileMap.get(wrappedX, targetRow)) {
                val remaining = tileMap.damage(wrappedX, targetRow, 10)
                mapVersion++
                if (isHostSelected == true) {
                    if (remaining <= 0) mp?.sendTileDestroyed(wrappedX, targetRow)
                    else mp?.sendTileHealth(wrappedX, targetRow, remaining)
                }
                // If the block at this anchor was destroyed, grant a Block item to the player
                if (remaining <= 0) {
                    if (vm.addItemToInventory(ItemType.Block, 1)) {
                        pushNotice("Block +1")
                    }
                }
                anythingHit = true
            }

            // Independently damage any zombie or skeleton overlapping this cell (wrap-aware along X)
            run {
                val cellLeft = wrappedX * unitPx
                val cellRight = cellLeft + unitPx
                val cellBottom = targetRow * unitPx
                val cellTop = cellBottom + unitPx
                val worldWidthPx = tileMap.width * unitPx
                var localHit = false
                zombies.forEach { z ->
                    val zBottom = z.bottomPx
                    val zTop = zBottom + playerHeightPx
                    // Quick vertical reject
                    if (cellTop <= zBottom || cellBottom >= zTop) return@forEach
                    val baseLeft = z.worldXPx
                    val candidates = floatArrayOf(baseLeft, baseLeft - worldWidthPx, baseLeft + worldWidthPx)
                    for (zl in candidates) {
                        val zr = zl + playerWidthPx
                        if (!(cellRight <= zl || cellLeft >= zr)) {
                            // Overlaps
                            localHit = true
                            vm.damageZombie(z.id, unitPx, 1)
                            break
                        }
                    }
                }
                skeletons.forEach { s ->
                    val sBottom = s.bottomPx
                    val sTop = sBottom + playerHeightPx
                    // Quick vertical reject
                    if (cellTop <= sBottom || cellBottom >= sTop) return@forEach
                    val baseLeft = s.worldXPx
                    val candidates = floatArrayOf(baseLeft, baseLeft - worldWidthPx, baseLeft + worldWidthPx)
                    for (sl in candidates) {
                        val sr = sl + playerWidthPx
                        if (!(cellRight <= sl || cellLeft >= sr)) {
                            // Overlaps
                            localHit = true
                            vm.damageSkeleton(s.id, unitPx, 1)
                            break
                        }
                    }
                }
                if (localHit) {
                    anythingHit = true
                }
            }

            return anythingHit
        }

        fun performPlaceAt(xDp: Dp, bottomPx: Float, facing: Direction, aimCol: Int? = null, aimRow: Int? = null): Boolean {
            val xPx = xDpToPx(xDp)
            val centerXPx = xPx + playerWidthPx / 2f
            val colCenter = floor(centerXPx / unitPx).toInt()
            val leftCol = floor((xPx) / unitPx).toInt()
            val rightCol = floor((xPx + playerWidthPx - 0.001f) / unitPx).toInt()
            val bottomRow = floor((bottomPx - 0.001f) / unitPx).toInt()
            val topRow = floor(((bottomPx + playerHeightPx - 0.001f) / unitPx)).toInt()

            fun canPlaceAt(anchorCol: Int, anchorRow: Int): Boolean {
                // Vertical bounds for the full 3x3 block
                if (!tileMap.inY(anchorRow) || !tileMap.inY(anchorRow + 2)) return false
                // Prevent placing exactly where the player stands (disallow true AABB overlap)
                val playerCols = playerOverlappingColumns(xPx)
                val playerRows = playerOverlappingRows(bottomPx)
                val placeCols = anchorCol..(anchorCol + 2)
                val placeRows = anchorRow..(anchorRow + 2)
                val overlapCols = placeCols.any { it in playerCols }
                val overlapRows = placeRows.any { it in playerRows }
                if (overlapCols && overlapRows) return false
                // Prevent placing over remote peers (treat peers as 2x3 AABBs)
                if (connectionStatus) {
                    peersState.values.forEach { peer ->
                        val pCol0 = floor(peer.worldXPx / unitPx).toInt()
                        val pCol1 = floor((peer.worldXPx + playerWidthPx - 0.001f) / unitPx).toInt()
                        val pRow0 = floor((peer.heightPx - 0.001f) / unitPx).toInt()
                        val pRow1 = floor(((peer.heightPx + playerHeightPx - 0.001f) / unitPx)).toInt()
                        val colsOverlap = (anchorCol..(anchorCol + 2)).any { c ->
                            val cw = ((c % tileMap.width) + tileMap.width) % tileMap.width
                            cw in pCol0..pCol1
                        }
                        val rowsOverlap = (anchorRow..(anchorRow + 2)).any { r -> r in pRow0..pRow1 }
                        if (colsOverlap && rowsOverlap) return false
                    }
                }
                // Ensure the entire 3x3 region is empty (respect X wrapping)
                for (dx in 0..2) {
                    val cx = ((anchorCol + dx) % tileMap.width + tileMap.width) % tileMap.width
                    for (dy in 0..2) {
                        val ry = anchorRow + dy
                        if (!tileMap.inY(ry) || tileMap.get(cx, ry)) return false
                    }
                }
                return true
            }

            // Choose anchor for a 3x3 block. Prefer aimed cell if provided and adjacent; otherwise use facing.
            var targetCol = 0
            var targetRow = 0
            var hasTarget = false

            fun isAdjacentToPlayerCell(c: Int, r: Int): Boolean {
                val playerCols = playerOverlappingColumns(xPx)
                val playerRows = playerOverlappingRows(bottomPx)
                val centerBand = (colCenter - 1)..(colCenter + 1)
                val adjacentLeft = (c == leftCol - 1 && r in playerRows)
                val adjacentRight = (c == rightCol + 1 && r in playerRows)
                val adjacentUp = (r == topRow + 1 && c in centerBand)
                val adjacentDown = (r == bottomRow - 1 && c in centerBand)
                return adjacentLeft || adjacentRight || adjacentUp || adjacentDown
            }

            fun anchorFromAimCell(c: Int, r: Int): Pair<Int, Int>? {
                // Determine which side the aimed cell lies on
                return when {
                    c < leftCol -> Pair(c - 2, r) // left side: aimed cell is block's rightmost column
                    c > rightCol -> Pair(c, r)    // right side: aimed cell is block's leftmost column
                    r > topRow -> Pair(c - 1, r)  // above head: center horizontally on aim; aim is bottom row of block
                    r < bottomRow -> Pair(c - 1, r - 2) // below feet: aim is top row
                    else -> null
                }
            }

            if (aimCol != null && aimRow != null && isAdjacentToPlayerCell(aimCol, aimRow)) {
                val anchor = anchorFromAimCell(aimCol, aimRow)
                if (anchor != null) {
                    targetCol = anchor.first
                    targetRow = anchor.second
                    hasTarget = canPlaceAt(targetCol, targetRow)
                }
            }

            if (!hasTarget) {
                when (facing) {
                    Direction.Left -> {
                        targetCol = leftCol - 3
                        val pr = playerOverlappingRows(bottomPx)
                        targetRow = pr.first
                        val placeRows = targetRow..(targetRow + 2)
                        val touchesVertically = placeRows.any { it in pr }
                        hasTarget = touchesVertically && canPlaceAt(targetCol, targetRow)
                    }
                    Direction.Right -> {
                        targetCol = rightCol + 1
                        val pr = playerOverlappingRows(bottomPx)
                        targetRow = pr.first
                        val placeRows = targetRow..(targetRow + 2)
                        val touchesVertically = placeRows.any { it in pr }
                        hasTarget = touchesVertically && canPlaceAt(targetCol, targetRow)
                    }
                    // Diagonals: synthesize an aimed adjacent cell and reuse anchorFromAimCell logic
                    Direction.UpLeft -> {
                        val aimR = topRow + 1
                        anchorFromAimCell(leftCol, aimR)?.let { (ac, ar) ->
                            targetCol = ac; targetRow = ar; hasTarget = canPlaceAt(targetCol, targetRow)
                        }
                    }
                    Direction.LeftUp -> {
                        val aimC = leftCol - 1
                        anchorFromAimCell(aimC, topRow)?.let { (ac, ar) ->
                            targetCol = ac; targetRow = ar; hasTarget = canPlaceAt(targetCol, targetRow)
                        }
                    }
                    Direction.UpRight -> {
                        val aimR = topRow + 1
                        anchorFromAimCell(rightCol, aimR)?.let { (ac, ar) ->
                            targetCol = ac; targetRow = ar; hasTarget = canPlaceAt(targetCol, targetRow)
                        }
                    }
                    Direction.RightUp -> {
                        val aimC = rightCol + 1
                        anchorFromAimCell(aimC, topRow)?.let { (ac, ar) ->
                            targetCol = ac; targetRow = ar; hasTarget = canPlaceAt(targetCol, targetRow)
                        }
                    }
                    Direction.DownLeft -> {
                        val aimR = bottomRow - 1
                        anchorFromAimCell(leftCol, aimR)?.let { (ac, ar) ->
                            targetCol = ac; targetRow = ar; hasTarget = canPlaceAt(targetCol, targetRow)
                        }
                    }
                    Direction.LeftDown -> {
                        val aimC = leftCol - 1
                        anchorFromAimCell(aimC, bottomRow)?.let { (ac, ar) ->
                            targetCol = ac; targetRow = ar; hasTarget = canPlaceAt(targetCol, targetRow)
                        }
                    }
                    Direction.DownRight -> {
                        val aimR = bottomRow - 1
                        anchorFromAimCell(rightCol, aimR)?.let { (ac, ar) ->
                            targetCol = ac; targetRow = ar; hasTarget = canPlaceAt(targetCol, targetRow)
                        }
                    }
                    Direction.RightDown -> {
                        val aimC = rightCol + 1
                        anchorFromAimCell(aimC, bottomRow)?.let { (ac, ar) ->
                            targetCol = ac; targetRow = ar; hasTarget = canPlaceAt(targetCol, targetRow)
                        }
                    }
                }
            }

            if (!hasTarget) return false

            // Place the block
            val id = tileMap.placeBlock(targetCol, targetRow, 3, 3, Color(0xFF2E8B57))
            if (id != -1) {
                mapVersion++
                if (isHostSelected == true) {
                    val wrappedX = ((targetCol % tileMap.width) + tileMap.width) % tileMap.width
                    mp?.sendTilePlaced(wrappedX, targetRow)
                }
                return true
            }
            return false
        }

        // Hit logic: aim one of 10 adjacent cells (3 left, 3 right, 2 up, 2 down)
        // based on joystick (or last direction if neutral) and apply hit to whatever is there:
        // tiles and zombies alike.
        fun hit() {
            // Client just requests; host performs and broadcasts
            if (isHostSelected == false) {
                mp?.sendInput(left = false, right = false, jump = false, hit = true)
                return
            }
            // Compute the 10-cells candidate set and select best by joystick/last direction
            val xPx = xDpToPx(playerWorldXDp)
            val leftCol = floor((xPx) / unitPx).toInt()
            val rightCol = floor((xPx + playerWidthPx - 0.001f) / unitPx).toInt()
            val bottomRow = floor((heightPx - 0.001f) / unitPx).toInt()
            val topRow = floor(((heightPx + playerHeightPx - 0.001f) / unitPx)).toInt()
            val midRow = (bottomRow + topRow) / 2

            data class Cell(val c: Int, val r: Int)
            val candidates = buildList {
                // 3 Left (Up, Mid, Down)
                add(Cell(leftCol - 1, topRow))
                add(Cell(leftCol - 1, midRow))
                add(Cell(leftCol - 1, bottomRow))
                // 3 Right (Up, Mid, Down)
                add(Cell(rightCol + 1, topRow))
                add(Cell(rightCol + 1, midRow))
                add(Cell(rightCol + 1, bottomRow))
                // 2 Up (above player's top, aligned to player edges)
                add(Cell(leftCol, topRow + 1))
                add(Cell(rightCol, topRow + 1))
                // 2 Down (below player's bottom, aligned to player edges)
                add(Cell(leftCol, bottomRow - 1))
                add(Cell(rightCol, bottomRow - 1))
            }

            val playerCenterX = xPx + playerWidthPx / 2f
            val playerCenterY = heightPx + playerHeightPx / 2f
            val joyMag = sqrt((joystickX*joystickX + joystickY*joystickY).toDouble()).toFloat()
            val (dirX, dirY) = if (joyMag > 0.15f) {
                // Global: up on joystick should aim/select upper block (no inversion applied)
                joystickX to joystickY
            } else {
                directionToVector(lastDirection)
            }

            var best: Cell? = null
            var bestScore = Float.NEGATIVE_INFINITY
            candidates.forEach { cell ->
                if (!tileMap.inY(cell.r)) return@forEach
                val cx = ((((cell.c % tileMap.width) + tileMap.width) % tileMap.width) + 0.5f) * unitPx
                val cy = (cell.r + 0.5f) * unitPx
                var vx = cx - playerCenterX
                var vy = cy - playerCenterY
                val vm = sqrt(vx*vx + vy*vy)
                if (vm > 0f) { vx /= vm; vy /= vm }
                val score = vx*dirX + vy*dirY
                if (score > bestScore) { bestScore = score; best = cell }
            }
            val aimed = best ?: candidates[4] // default to right-mid (index 4) if selection failed
            val aimColWrapped = ((aimed.c % tileMap.width) + tileMap.width) % tileMap.width
            val aimRow = aimed.r

            // Update preview state so the UI reflects the selection during the press
            previewSelectedAimCol = aimColWrapped
            previewSelectedAimRow = aimRow
            previewSelectedFacing = vectorToDirection(((aimColWrapped + 0.5f) * unitPx - playerCenterX), ((aimRow + 0.5f) * unitPx - playerCenterY))

            // Apply hit via unified path (handles both tiles and zombies with wrap-aware overlap)
            performHitAt(playerWorldXDp, heightPx, previewSelectedFacing ?: lastDirection, aimColWrapped, aimRow)
        }

        // Place logic: place a 3x3 block into the adjacent cell (anchor) in last look direction
        fun place() {
            if (isHostSelected == false) {
                mp?.sendInput(left = false, right = false, jump = false, hit = false, place = true)
                return
            }
            val slot = inventory.getOrNull(vm.selectedSlot.intValue)
            if (slot == null || slot.type == null || slot.count <= 0) {
                // Nothing selected
                return
            }
            when (slot.type) {
                ItemType.Block -> {
                    val aimC = previewSelectedAimCol
                    val aimR = previewSelectedAimRow
                    val placed = performPlaceAt(playerWorldXDp, heightPx, lastDirection, aimC, aimR)
                    if (placed) {
                        slot.count -= 1
                        if (slot.count <= 0) { slot.type = null; slot.count = 0 }
                    }
                }
                ItemType.Potion -> {
                    if (playerHp < 5) {
                        playerHp += 1
                        slot.count -= 1
                        if (slot.count <= 0) { slot.type = null; slot.count = 0 }
                        pushNotice("+1 heart")
                    } else {
                        pushNotice("Hearts are full")
                    }
                }
                null -> { /* no-op */ }
            }
        }

        // Jump and vertical physics with tile collisions
        fun jump() {
            if (isJumping) return
            isJumping = true
            if (isHostSelected == false) {
                mp?.sendInput(left = false, right = false, jump = true, hit = false)
            }
            scope.launch {
                // Tune jump so player can clear a single 1-tile block but not 2 tiles.
                // With g = -3000 px/s^2, choose v0 ≈ 620 px/s for peak height ~64 px (≈1.6 tiles),
                // which is enough to hop a single block but insufficient for two stacked blocks during run-up.
                //TODO this needs to be calculated that the player can jump 4x1 grids
                var v = 1000f // px/s upward
                val g = -3000f // px/s^2
                val frameMs = 16L
                val dt = frameMs / 1000f
                while (true) {
                    if (isPaused) {
                        delay(frameMs); continue }
                    val prevBottom = heightPx
                    v += g * dt
                    heightPx += v * dt

                    val xPx = xDpToPx(playerWorldXDp)
                    val cols = playerOverlappingColumns(xPx)

                    // Ceiling collision when ascending: sweep all crossed rows and clamp to the first tile bottom
                    if (v > 0f) {
                        val prevTop = prevBottom + playerHeightPx
                        val nowTop = heightPx + playerHeightPx
                        val startRow = floor(prevTop / unitPx).toInt()
                        val endRow = floor(nowTop / unitPx).toInt()
                        if (endRow >= startRow) {
                            var hitRow: Int? = null
                            // Check every row boundary we crossed this frame (handles fast/tunneling motion)
                            for (row in (startRow + 1)..endRow) {
                                var hit = false
                                for (c in cols) {
                                    if (tileMap.get(c, row)) { hit = true; break }
                                }
                                if (hit) { hitRow = row; break }
                            }
                            if (hitRow != null) {
                                val tileBottomPx = hitRow!! * unitPx
                                heightPx = tileBottomPx - playerHeightPx
                                v = 0f
                            }
                        }
                        // Also collide with peers' bottoms when ascending (no pass-through)
                        if (connectionStatus) {
                            val myPrevTop = prevBottom + playerHeightPx
                            val myNowTop = heightPx + playerHeightPx
                            peersState.values.forEach { peer ->
                                val pBottom = peer.heightPx
                                val px = peer.worldXPx
                                val pCols = run {
                                    val c0 = floor(px / unitPx).toInt()
                                    val c1 = floor((px + playerWidthPx - 0.001f) / unitPx).toInt()
                                    c0..c1
                                }
                                val horizOverlap = cols.any { it in pCols }
                                if (horizOverlap && myPrevTop <= pBottom && myNowTop >= pBottom) {
                                    heightPx = pBottom - playerHeightPx
                                    v = 0f
                                }
                            }
                        }
                        // Also collide with any zombie bottom when ascending
                        zombies.forEach { z ->
                            val myPrevTop = prevBottom + playerHeightPx
                            val myNowTop = heightPx + playerHeightPx
                            val zBottom = z.bottomPx
                            val zCols = run {
                                val c0 = floor(z.worldXPx / unitPx).toInt()
                                val c1 = floor((z.worldXPx + playerWidthPx - 0.001f) / unitPx).toInt()
                                c0..c1
                            }
                            val horizOverlap = cols.any { it in zCols }
                            if (horizOverlap && myPrevTop <= zBottom && myNowTop >= zBottom) {
                                heightPx = zBottom - playerHeightPx
                                v = 0f
                            }
                        }
                        // Safety: if after integration our top is already inside a solid tile (e.g., block placed above), resolve
                        run {
                            val topRowNow = floor(((heightPx + playerHeightPx - 0.001f) / unitPx)).toInt()
                            var overlap = false
                            for (c in cols) { if (tileMap.get(c, topRowNow)) { overlap = true; break } }
                            if (overlap) {
                                val tileBottomPx = topRowNow * unitPx
                                heightPx = tileBottomPx - playerHeightPx
                                v = 0f
                            }
                        }
                    }

                    // Landing when descending: player's bottom crosses any tile top between prev and now
                    if (v <= 0f) {
                        val startRow = floor((prevBottom - 0.001f) / unitPx).toInt()
                        val endRow = floor((heightPx) / unitPx).toInt()
                        if (endRow < startRow || heightPx <= 0f) {
                            var landingTopPx: Float? = null
                            // Sweep all crossed row tops and find the highest top we intersected
                            for (row in startRow downTo max(endRow, 0)) {
                                var hit = false
                                for (c in cols) { if (tileMap.get(c, row)) { hit = true; break } }
                                if (hit) {
                                    val topPx = (row + 1) * unitPx
                                    if (topPx in heightPx..prevBottom) {
                                        landingTopPx =
                                            max(landingTopPx ?: Float.NEGATIVE_INFINITY, topPx)
                                    }
                                }
                            }
                            // Also consider landing on peers' tops (allow stacking)
                            if (connectionStatus) {
                                peersState.values.forEach { peer ->
                                    val px = peer.worldXPx
                                    val pCols = run {
                                        val c0 = floor(px / unitPx).toInt()
                                        val c1 = floor((px + playerWidthPx - 0.001f) / unitPx).toInt()
                                        c0..c1
                                    }
                                    val horizOverlap = cols.any { it in pCols }
                                    if (horizOverlap) {
                                        val peerTop = peer.heightPx + playerHeightPx
                                        if (peerTop in heightPx..prevBottom) {
                                            landingTopPx =
                                                max(landingTopPx ?: Float.NEGATIVE_INFINITY, peerTop)
                                        }
                                    }
                                }
                            }
                            // Also consider landing on any zombie's top
                            zombies.forEach { z ->
                                val zCols = run {
                                    val c0 = floor(z.worldXPx / unitPx).toInt()
                                    val c1 = floor((z.worldXPx + playerWidthPx - 0.001f) / unitPx).toInt()
                                    c0..c1
                                }
                                val horizOverlap = cols.any { it in zCols }
                                if (horizOverlap) {
                                    val zTop = z.bottomPx + playerHeightPx
                                    if (zTop in heightPx..prevBottom) {
                                        landingTopPx =
                                            max(landingTopPx ?: Float.NEGATIVE_INFINITY, zTop)
                                    }
                                }
                            }
                            // Also consider landing on any skeleton's top
                            skeletons.forEach { s ->
                                val sCols = run {
                                    val c0 = floor(s.worldXPx / unitPx).toInt()
                                    val c1 = floor((s.worldXPx + playerWidthPx - 0.001f) / unitPx).toInt()
                                    c0..c1
                                }
                                val horizOverlap = cols.any { it in sCols }
                                if (horizOverlap) {
                                    val sTop = s.bottomPx + playerHeightPx
                                    if (sTop in heightPx..prevBottom) {
                                        landingTopPx =
                                            max(landingTopPx ?: Float.NEGATIVE_INFINITY, sTop)
                                    }
                                }
                            }
                            if (landingTopPx != null) {
                                heightPx = landingTopPx
                                isJumping = false
                                break
                            }
                            if (heightPx <= 0f) {
                                heightPx = 0f
                                isJumping = false
                                break
                            }
                        }
                    }

                    // Bottom-overlap safety: if feet ended up inside a solid tile, snap to its top
                    var shouldBreak = false
                    run {
                        val bottomRowNow = floor((heightPx - 0.001f) / unitPx).toInt()
                        if (bottomRowNow >= 0) {
                            var overlap = false
                            for (c in cols) { if (tileMap.get(c, bottomRowNow)) { overlap = true; break } }
                            if (overlap) {
                                val topPx = (bottomRowNow + 1) * unitPx
                                heightPx = topPx
                                if (v < 0f) v = 0f
                                // Consider this a landing to avoid re-entering the tile next frame
                                isJumping = false
                                shouldBreak = true
                            }
                        }
                    }
                    if (shouldBreak) break

                    delay(frameMs)
                }
            }
        }

        // Attach gameplay listener to existing multiplayer session
        LaunchedEffect(mp, isHostSelected) {
            mp?.updateListener(object : MultiplayerManager.Listener {
                override fun onPeerInput(id: Int, left: Boolean, right: Boolean, jump: Boolean, hit: Boolean, place: Boolean) {
                    if (isHostSelected == true) {
                        val selfId = mp.getSelfId()
                        if (id == selfId) return
                        val peer = peersState.getOrPut(id) { RemotePeer() }
                        if (left) peer.facingRight = false
                        if (right) peer.facingRight = true
                        if (hit) {
                            val facing = if (left) Direction.Left else if (right) Direction.Right else if (peer.facingRight) Direction.Right else Direction.Left
                            // Use current pixel position to avoid stale dp and convert on the fly.
                            // performHitAt() handles both tiles and zombies in the targeted cell.
                            performHitAt(pxToDp(peer.worldXPx), peer.heightPx, facing)
                        }
                        if (place) {
                            val facing = if (left) Direction.Left else if (right) Direction.Right else if (peer.facingRight) Direction.Right else Direction.Left
                            performPlaceAt(pxToDp(peer.worldXPx), peer.heightPx, facing)
                        }
                    }
                }
                override fun onPeerPos(id: Int, col: Int, row: Int, facingRight: Boolean) {
                    val selfId = mp.getSelfId()
                    if (id == selfId) return
                    val peer = peersState.getOrPut(id) { RemotePeer() }
                    peer.targetXPx = col * unitPx
                    peer.targetHeightPx = row * unitPx
                    if (!peer.hasPos) {
                        peer.worldXPx = peer.targetXPx
                        peer.heightPx = peer.targetHeightPx
                        peer.worldXDp = pxToDp(peer.worldXPx)
                        peer.hasPos = true
                    }
                    peer.facingRight = facingRight
                }
                override fun onTileDestroyed(x: Int, y: Int) {
                    if (isHostSelected == false) {
                        tileMap.set(x, y, false)
                        mapVersion++
                    }
                }
                override fun onTilePlaced(x: Int, y: Int) {
                    if (isHostSelected == false) {
                        tileMap.set(x, y, true)
                        mapVersion++
                    }
                }
                override fun onTileHealth(x: Int, y: Int, health: Int) {
                    if (isHostSelected == false) {
                        tileMap.setHealth(x, y, health)
                        mapVersion++
                    }
                }
                override fun onPeerConnected(id: Int) {
                    val selfId = mp.getSelfId()
                    if (id == selfId) return
                    peersState.getOrPut(id) { RemotePeer() }
                    pushNotice("Player #$id joined")
                    // If host, briefly pause and send a world snapshot so new client syncs existing blocks
                    if (isHostSelected == true) {
                        isPaused = true
                        scope.launch {
                            // Send all current blocks as TILE|place and their health
                            tileMap.snapshotBlocks().forEach { b ->
                                mp.sendTilePlaced(b.col, b.row)
                                if (b.health < b.maxHealth) {
                                    mp.sendTileHealth(b.col, b.row, b.health)
                                }
                            }
                            // small pause window to allow client to apply
                            delay(200L)
                            isPaused = false
                        }
                    }
                }
                override fun onPeerDisconnected(id: Int) {
                    val selfId = mp.getSelfId()
                    if (id == selfId) return
                    peersState.remove(id)
                    pushNotice("Player #$id left")
                }
                override fun onConnectionChanged(connected: Boolean) {
                    connectionStatus = connected
                    pushNotice(if (connected) "Connected" else "Connection lost")
                    if (connected) {
                        mp.sendSelectedPlayer(player.name)
                    }
                }
                override fun onPeerSelectedPlayer(id: Int, name: String) {
                    val selfId = mp.getSelfId()
                    if (id == selfId) return
                    val p = when (name) {
                        Player.Leo.name -> Player.Leo
                        Player.Ian.name -> Player.Ian
                        Player.Papa.name -> Player.Papa
                        else -> null
                    }
                    peersState.getOrPut(id) { RemotePeer() }.selected = p
                }
            })
        }

        // Send our position to peers only when it changes (reduce network noise)
        var lastSentCol by remember { mutableStateOf<Int?>(null) }
        var lastSentRow by remember { mutableStateOf<Int?>(null) }
        var lastSentFacing by remember { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(connectionStatus, isHostSelected, playerWorldXDp, heightPx, lastDirection) {
            if (connectionStatus) {
                val xPx = xDpToPx(playerWorldXDp)
                val col = floor(xPx / unitPx).toInt()
                val row = floor((heightPx) / unitPx).toInt()
                val wrappedCol = ((col % tileMap.width) + tileMap.width) % tileMap.width
                val facingRightFlag = (lastDirection == Direction.Right)
                if (lastSentCol != wrappedCol || lastSentRow != row || lastSentFacing != facingRightFlag) {
                    mp?.sendPos(wrappedCol, row, facingRightFlag)
                    lastSentCol = wrappedCol
                    lastSentRow = row
                    lastSentFacing = facingRightFlag
                }
            } else {
                lastSentCol = null; lastSentRow = null; lastSentFacing = null
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // Multiplayer role selection overlay
            if (isHostSelected == null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Multiplayer: choose role", color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Button(onClick = { isHostSelected = true }) { Text("Host") }
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = { isHostSelected = false }) { Text("Join") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(text = "Host binds 0.0.0.0:${MultiplayerManager.DEFAULT_PORT}", color = Color.White)
                    Text(text = "Join connects to ${MultiplayerManager.DEFAULT_HOST_IP}:${MultiplayerManager.DEFAULT_PORT}", color = Color.White)
                }
            } else {
                val status = if (connectionStatus) "connected" else "connecting..."
                val label = (if (isHostSelected == true) "Host: $status" else "Client: $status") + " | peers: ${peersState.size}"
                Text(
                    text = label,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                )
            }

            // Play area fills whole screen
            var aimCol by remember { mutableStateOf<Int?>(null) }
            var aimRow by remember { mutableStateOf<Int?>(null) }
            var isPlacePreviewing by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFF87CEEB))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { pos ->
                            // Convert screen tap to world grid (consider camera offset)
                            val cameraOffsetXDpNow = playerWorldXDp - centerX
                            val cameraOffsetXPxNow = xDpToPx(cameraOffsetXDpNow)
                            val worldX = pos.x + cameraOffsetXPxNow
                            val col = floor(worldX / unitPx).toInt()
                            val row = floor((size.height - pos.y) / unitPx).toInt()
                            aimCol = ((col % tileMap.width) + tileMap.width) % tileMap.width
                            aimRow = row
                            // Set facing based on precise angle from player center to tapped cell center
                            run {
                                val playerCenterX = xDpToPx(playerWorldXDp) + playerWidthPx / 2f
                                val playerCenterY = heightPx + playerHeightPx / 2f
                                val cellCenterX = (aimCol!! + 0.5f) * unitPx
                                val cellCenterY = (aimRow!! + 0.5f) * unitPx
                                previewSelectedFacing = vectorToDirection(cellCenterX - playerCenterX, cellCenterY - playerCenterY)
                            }
                        })
                    }
            ) {
                // Camera offset in dp (world x at player minus screen center)
                val cameraOffsetXDp = playerWorldXDp - centerX
                val cameraOffsetXPx = xDpToPx(cameraOffsetXDp)

                // Observe mapVersion so UI updates when tiles are destroyed
                val _mv = mapVersion
                // Observe NPC ticker to ensure UI frames advance even without player input
                val _npcTick = npcFrameTick
                // Render visible tiles with horizontal wrapping
                val viewportWidthPx = xDpToPx(screenWidth)
                // Draw blocks with horizontal wrapping copies so blocks before index 0 are visible to the left
                val blocksSnapshot = tileMap.snapshotBlocks()
                val worldWidthPx = tileMap.width * unitPx
                for (b in blocksSnapshot) {
                    val hpRatio = if (b.maxHealth > 0) b.health.toFloat() / b.maxHealth.toFloat() else 0f
                    val blockColor = Color.hsl(120f, hpRatio.coerceIn(0f, 1f), 0.42f)
                    val baseLeftPx = (b.col * unitPx) - cameraOffsetXPx
                    val blockWidthPx = b.w * unitPx
                    val candidates = floatArrayOf(baseLeftPx, baseLeftPx - worldWidthPx, baseLeftPx + worldWidthPx)
                    val blockBottomDp = pxToDp(b.row * unitPx)
                    for (leftPx in candidates) {
                        val rightPx = leftPx + blockWidthPx
                        // Only draw if it intersects the viewport horizontally
                        if (rightPx >= 0f && leftPx <= viewportWidthPx) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .offset(x = pxToDp(leftPx), y = -blockBottomDp)
                                    .size(width = unit * b.w, height = unit * b.h)
                                    .background(blockColor)
                            )
                        }
                    }
                }

                // Long-press Place preview ghost: show all touching 1x1 options; select via joystick and show ghost if valid
                if (isPlacePreviewing && vm.hasSelectedBlock()) {
                    val xPx = xDpToPx(playerWorldXDp)
                    val colCenter = floor((xPx + playerWidthPx / 2f) / unitPx).toInt()
                    val leftCol = floor(xPx / unitPx).toInt()
                    val rightCol = floor((xPx + playerWidthPx - 0.001f) / unitPx).toInt()
                    val bottomRow = floor((heightPx - 0.001f) / unitPx).toInt()
                    val topRow = floor(((heightPx + playerHeightPx - 0.001f) / unitPx)).toInt()
                    val playerRows = playerOverlappingRows(heightPx)
                    val playerCols = playerOverlappingColumns(xPx)
                    var ghostCol = 0
                    var ghostRow = 0
                    var showGhost = false

                    fun canPlaceAtPreview(anchorCol: Int, anchorRow: Int): Boolean {
                        if (!tileMap.inY(anchorRow) || !tileMap.inY(anchorRow + 2)) return false
                        // disallow overlap with player
                        val placeCols = anchorCol..(anchorCol + 2)
                        val placeRows = anchorRow..(anchorRow + 2)
                        val overlapCols = placeCols.any { it in playerCols }
                        val overlapRows = placeRows.any { it in playerRows }
                        if (overlapCols && overlapRows) return false
                        // disallow overlap with peers
                        if (connectionStatus) {
                            peersState.values.forEach { peer ->
                                val pCol0 = floor(peer.worldXPx / unitPx).toInt()
                                val pCol1 = floor((peer.worldXPx + playerWidthPx - 0.001f) / unitPx).toInt()
                                val pRow0 = floor((peer.heightPx - 0.001f) / unitPx).toInt()
                                val pRow1 = floor(((peer.heightPx + playerHeightPx - 0.001f) / unitPx)).toInt()
                                val colsOverlap = placeCols.any { c -> ((c % tileMap.width) + tileMap.width) % tileMap.width in pCol0..pCol1 }
                                val rowsOverlap = placeRows.any { r -> r in pRow0..pRow1 }
                                if (colsOverlap && rowsOverlap) return false
                            }
                        }
                        // disallow overlap with skeletons
                        skeletons.forEach { s ->
                            val sCol0 = floor(s.worldXPx / unitPx).toInt()
                            val sCol1 = floor((s.worldXPx + playerWidthPx - 0.001f) / unitPx).toInt()
                            val sRow0 = floor((s.bottomPx - 0.001f) / unitPx).toInt()
                            val sRow1 = floor(((s.bottomPx + playerHeightPx - 0.001f) / unitPx)).toInt()
                            val colsOverlap = placeCols.any { c -> ((c % tileMap.width) + tileMap.width) % tileMap.width in sCol0..sCol1 }
                            val rowsOverlap = placeRows.any { r -> r in sRow0..sRow1 }
                            if (colsOverlap && rowsOverlap) return false
                        }
                        // region emptiness
                        for (dx in 0..2) {
                            val cx = ((anchorCol + dx) % tileMap.width + tileMap.width) % tileMap.width
                            for (dy in 0..2) {
                                val ry = anchorRow + dy
                                if (!tileMap.inY(ry) || tileMap.get(cx, ry)) return false
                            }
                        }
                        return true
                    }
                    
                    val candidates = mutableListOf<Cell>()
                    // Left strip
                    for (r in playerRows) { candidates += Cell(leftCol - 1, r) }
                    // Right strip
                    for (r in playerRows) { candidates += Cell(rightCol + 1, r) }
                    // Up strip (use 3 cells centered on player center)
                    for (c in (colCenter - 1)..(colCenter + 1)) { candidates += Cell(c, topRow + 1) }
                    // Down strip (use 3 cells centered on player center)
                    for (c in (colCenter - 1)..(colCenter + 1)) { candidates += Cell(c, bottomRow - 1) }

                    // Choose a selected cell using joystick direction (fallback to last facing)
                    val playerCenterX = xPx + playerWidthPx / 2f
                    val playerCenterY = heightPx + playerHeightPx / 2f
                    val joyMag = sqrt(joystickX*joystickX + joystickY*joystickY)
                    var best: Cell? = null
                    var bestScore = Float.NEGATIVE_INFINITY
                    val (dirX, dirY) = if (joyMag > 0.15f) {
                        // Global: up on joystick should select/aim upper block (no inversion)
                        joystickX to joystickY
                    } else {
                        directionToVector(lastDirection)
                    }
                    candidates.forEach { cell ->
                        if (!tileMap.inY(cell.r)) return@forEach
                        val cx = (cell.c + 0.5f) * unitPx
                        val cy = (cell.r + 0.5f) * unitPx
                        var vx = cx - playerCenterX
                        var vy = cy - playerCenterY
                        val vm = sqrt(vx*vx + vy*vy)
                        if (vm > 0f) { vx /= vm; vy /= vm }
                        val score = vx*dirX + vy*dirY
                        if (score > bestScore) { bestScore = score; best = cell }
                    }
                    val selected = best ?: candidates.first()
                    val selCol = ((selected.c % tileMap.width) + tileMap.width) % tileMap.width
                    val selRow = selected.r
                    previewSelectedAimCol = selCol
                    previewSelectedAimRow = selRow
                    // Infer facing from precise angle to the selected cell center (10-sector mapping)
                    run {
                        val cx = (selCol + 0.5f) * unitPx
                        val cy = (selRow + 0.5f) * unitPx
                        previewSelectedFacing = vectorToDirection(cx - playerCenterX, cy - playerCenterY)
                    }

                    // Highlight selected cell thicker
                    run {
                        val aimLeftPx = ((selCol * unitPx) - cameraOffsetXPx)
                        val aimBottomDp = pxToDp(selRow * unitPx)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .offset(x = pxToDp(aimLeftPx), y = -aimBottomDp)
                                .size(unit)
                                .border(width = 2.dp, color = Color.Red)
                        )
                    }

                    // Compute ghost placement from the selected aim cell using the same rules as actual placement
                    fun anchorFromAimCellPreview(c: Int, r: Int): Pair<Int, Int>? {
                        return when {
                            c < leftCol -> Pair(c - 2, r)
                            c > rightCol -> Pair(c, r)
                            r > topRow -> Pair(c - 1, r)
                            r < floor((heightPx) / unitPx).toInt() -> Pair(c - 1, r - 2)
                            else -> null
                        }
                    }
                    val anchor = anchorFromAimCellPreview(selCol, selRow)
                    if (anchor != null) {
                        val ac = anchor.first
                        val ar = anchor.second
                        if (canPlaceAtPreview(ac, ar)) {
                            ghostCol = ac
                            ghostRow = ar
                            showGhost = true
                        }
                    }

                    if (showGhost) {
                        val ghostLeftPx = (ghostCol * unitPx) - cameraOffsetXPx
                        val ghostBottomDp = pxToDp(ghostRow * unitPx)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .offset(x = pxToDp(ghostLeftPx), y = -ghostBottomDp)
                                .size(width = unit * 3, height = unit * 3)
                                .background(Color.White.copy(alpha = 0.2f))
                                .border(width = 2.dp, color = Color.Red)
                        )
                    }
                }

                // Notices banner (top center)
                LaunchedEffect(Unit) {
                    while (true) {
                        val now = System.currentTimeMillis()
                        notices.removeAll { now - it.createdAt > 3500 }
                        delay(500)
                    }
                }
                Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    notices.forEach { n ->
                        Box(modifier = Modifier
                            .padding(2.dp)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(n.text, color = Color.White)
                        }
                    }
                }

                // Player sprite (always centered)
                val heightDp = pxToDp(heightPx)
                Image(
                    painter = painterResource(id = when (player) {
                        Player.Leo -> R.drawable.leo_front
                        Player.Ian -> R.drawable.ian_front
                        Player.Papa -> R.drawable.papa_front
                    }),
                    contentDescription = "Player",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = centerX, y = -heightDp)
                        .height(playerHeight)
                        .graphicsLayer { scaleX = if (lastDirection.isFacingRight()) -1f else 1f },
                    colorFilter = if (System.currentTimeMillis() <= playerFlashUntil) ColorFilter.tint(Color.Red) else null
                )

                // Render all remote peers (host and client) with wrap-aware positioning
                if (connectionStatus) {
                    val worldWidthDp = pxToDp(tileMap.width * unitPx)
                    peersState.forEach { (id, peer) ->
                        val baseX = centerX + (peer.worldXDp - playerWorldXDp)
                        val candidates = listOf(baseX, baseX - worldWidthDp, baseX + worldWidthDp)
                        val peerHeightDp = pxToDp(peer.heightPx)
                        val peerRes = when (peer.selected) {
                            Player.Leo -> R.drawable.leo_front
                            Player.Ian -> R.drawable.ian_front
                            Player.Papa -> R.drawable.papa_front
                            else -> R.drawable.ian_front
                        }
                        candidates.forEach { candX ->
                            // Only draw if intersects the viewport horizontally
                            if (candX > -playerWidth && candX < screenWidth + playerWidth) {
                                Image(
                                    painter = painterResource(id = peerRes),
                                    contentDescription = "Peer $id",
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .offset(x = candX, y = -peerHeightDp)
                                        .height(playerHeight)
                                        .graphicsLayer { scaleX = if (peer.facingRight) -1f else 1f }
                                )
                            }
                        }
                    }
                }

                // Render Zombies if present
                if (zombies.isNotEmpty()) {
                    val worldWidthDp = pxToDp(tileMap.width * unitPx)
                    zombies.forEach { z ->
                        val zXDp = pxToDp(z.worldXPx)
                        val baseX = centerX + (zXDp - playerWorldXDp)
                        val candidates = listOf(baseX, baseX - worldWidthDp, baseX + worldWidthDp)
                        val zHeightDp = pxToDp(z.bottomPx)
                        candidates.forEach { candX ->
                            if (candX > -playerWidth && candX < screenWidth + playerWidth) {
                                Image(
                                    painter = painterResource(id = R.drawable.zombie_walk),
                                    contentDescription = "Zombie",
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .offset(x = candX, y = -zHeightDp)
                                        .height(playerHeight)
                                        .graphicsLayer {
                                            // Flip polarity to match player/peer sprites
                                            scaleX = if (z.facingRight) 1f else -1f },
                                    colorFilter = if (System.currentTimeMillis() <= z.flashUntil) ColorFilter.tint(Color.Red) else null
                                )
                            }
                        }
                    }
                }

                // Render Skeletons if present
                if (skeletons.isNotEmpty()) {
                    val worldWidthDp = pxToDp(tileMap.width * unitPx)
                    skeletons.forEach { s ->
                        val sXDp = pxToDp(s.worldXPx)
                        val baseX = centerX + (sXDp - playerWorldXDp)
                        val candidates = listOf(baseX, baseX - worldWidthDp, baseX + worldWidthDp)
                        val sHeightDp = pxToDp(s.bottomPx)
                        candidates.forEach { candX ->
                            if (candX > -playerWidth && candX < screenWidth + playerWidth) {
                                Image(
                                    painter = painterResource(id = R.drawable.skeleton_archer),
                                    contentDescription = "Skeleton Archer",
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .offset(x = candX, y = -sHeightDp)
                                        .height(playerHeight)
                                        .graphicsLayer {
                                            scaleX = if (s.facingRight) 1f else -1f
                                        },
                                    colorFilter = if (System.currentTimeMillis() <= s.flashUntil) {
                                        ColorFilter.tint(Color.Red)
                                    } else if (s.pulseAmount > 0f) {
                                        ColorFilter.tint(Color.White.copy(alpha = s.pulseAmount), BlendMode.SrcAtop)
                                    } else null
                                )
                            }
                        }
                    }
                }

                // Render Arrows if present
                if (arrows.isNotEmpty()) {
                    val worldWidthDp = pxToDp(tileMap.width * unitPx)
                    arrows.forEach { arrow ->
                        val aXDp = pxToDp(arrow.worldXPx)
                        val baseX = centerX + (aXDp - playerWorldXDp)
                        val candidates = listOf(baseX, baseX - worldWidthDp, baseX + worldWidthDp)
                        val aHeightDp = pxToDp(arrow.bottomPx)
                        candidates.forEach { candX ->
                            if (candX > -unit && candX < screenWidth + unit) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .offset(x = candX, y = -aHeightDp)
                                        .height(2.dp)
                                        .width(unit)
                                        .background(Color.DarkGray)
                                )
                            }
                        }
                    }
                }

                // Render Potions if present
                if (potions.isNotEmpty()) {
                    val worldWidthDp = pxToDp(tileMap.width * unitPx)
                    potions.forEach { p ->
                        val pXDp = pxToDp(p.col * unitPx)
                        val baseX = centerX + (pXDp - playerWorldXDp)
                        val candidates = listOf(baseX, baseX - worldWidthDp, baseX + worldWidthDp)
                        val pHeightDp = pxToDp(p.row * unitPx)
                        candidates.forEach { candX ->
                            if (candX > -unit && candX < screenWidth + unit) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .offset(x = candX, y = -pHeightDp)
                                        .size(unit)
                                        .background(Color.Magenta),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("✚", color = Color.White, fontSize = with(LocalDensity.current) { (unit * 0.8f).toSp() })
                                }
                            }
                        }
                    }
                }

                // HUD: Player hearts at top center
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(5) { i ->
                        val filled = i < playerHp
                        Text(text = if (filled) "❤" else "♡", color = if (filled) Color.Red else Color.Gray)
                    }
                }

                // Inventory HUD: 5 slots at bottom center
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(inventorySlots) { i ->
                        val slot = inventory[i]
                        val isSelected = i == vm.selectedSlot.intValue
                        val borderColor = if (isSelected) Color.Yellow else Color.White.copy(alpha = 0.6f)
                        val label = when (slot.type) {
                            ItemType.Block -> "▧"
                            ItemType.Potion -> "✚"
                            null -> ""
                        }
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .border(2.dp, borderColor)
                                .background(Color.Black.copy(alpha = 0.25f))
                                .pointerInput(i) {
                                    detectTapGestures(onTap = { vm.selectedSlot.intValue = i })
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (slot.type != null && slot.count > 0) {
                                Text(text = label, color = Color.White)
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(2.dp)
                                ) {
                                    Text(text = slot.count.toString(), color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // Periodic spawner: every 60 seconds add another zombie and skeleton
            LaunchedEffect(Unit) {
                while (true) {
                    delay(60000L)
                    spawnZombieRandom()
                    spawnSkeletonRandom()
                }
            }

            // Independent NPC UI ticker to ensure continuous redraws regardless of player input
            LaunchedEffect(Unit) {
                while (true) {
                    npcFrameTick = (npcFrameTick + 1) and 0x7FFFFFFF.toInt()
                    delay(16L)
                }
            }

            // Zombie AI loop moved into GameViewModel (per-zombie jobs)

            // A small ticker to force recomposition as we smooth remote peers
            var frameTick by remember { mutableStateOf(0) }
            // Smoothly interpolate remote players' movement to avoid jumpy motion
            LaunchedEffect(connectionStatus, unitPx) {
                while (connectionStatus) {
                    val worldWidthPxF = tileMap.width * unitPx
                    fun adjustTarget(current: Float, target: Float): Float {
                        var t = target
                        val d = t - current
                        if (abs(d) > worldWidthPxF / 2f) {
                            t += if (d > 0f) -worldWidthPxF else worldWidthPxF
                        }
                        return t
                    }
                    val alpha = 0.25f
                    peersState.values.forEach { peer ->
                        val adjTargetX = adjustTarget(peer.worldXPx, peer.targetXPx)
                        val adjTargetY = peer.targetHeightPx
                        peer.worldXPx += (adjTargetX - peer.worldXPx) * alpha
                        peer.heightPx += (adjTargetY - peer.heightPx) * alpha
                        val worldWidthPx = tileMap.width * unitPx
                        peer.worldXPx = ((peer.worldXPx % worldWidthPx) + worldWidthPx) % worldWidthPx
                        peer.worldXDp = pxToDp(peer.worldXPx)
                    }
                    // bump a frame tick to trigger recomposition even if local state didn't change
                    frameTick = (frameTick + 1) and 0x7FFFFFFF.toInt()
                    delay(16L)
                }
            }
            // Read tick so compose observes it
            val _tick = frameTick

            // Start falling if leaving support (walk off edges)
            LaunchedEffect(playerWorldXDp, isJumping) {
                if (!isJumping) {
                    val xPx = xDpToPx(playerWorldXDp)
                    val cols = playerOverlappingColumns(xPx)
                    val supportedTopPxCandidates = mutableListOf<Float>()
                    for (c in cols) {
                        // Support exists if there is a solid tile directly under the player's bottom
                        val rowUnder = floor((heightPx - 0.001f) / unitPx).toInt()
                        if (rowUnder >= 0 && tileMap.get(c, rowUnder)) {
                            supportedTopPxCandidates += (rowUnder + 1) * unitPx
                        } else if (heightPx == 0f) {
                            supportedTopPxCandidates += 0f
                        }
                    }
                    // Also treat zombies' tops as valid support
                    if (zombies.isNotEmpty()) {
                        zombies.forEach { z ->
                            val zCols = run {
                                val c0 = floor(z.worldXPx / unitPx).toInt()
                                val c1 = floor((z.worldXPx + playerWidthPx - 0.001f) / unitPx).toInt()
                                c0..c1
                            }
                            val horizOverlap = cols.any { it in zCols }
                            if (horizOverlap) {
                                val zTop = z.bottomPx + playerHeightPx
                                supportedTopPxCandidates += zTop
                            }
                        }
                    }
                    // Also treat skeletons' tops as valid support
                    if (skeletons.isNotEmpty()) {
                        skeletons.forEach { s ->
                            val sCols = run {
                                val c0 = floor(s.worldXPx / unitPx).toInt()
                                val c1 = floor((s.worldXPx + playerWidthPx - 0.001f) / unitPx).toInt()
                                c0..c1
                            }
                            val horizOverlap = cols.any { it in sCols }
                            if (horizOverlap) {
                                val sTop = s.bottomPx + playerHeightPx
                                supportedTopPxCandidates += sTop
                            }
                        }
                    }
                    val eps = 0.5f
                    val supported = supportedTopPxCandidates.any { abs(it - heightPx) < eps }
                    if (!supported) {
                        isJumping = true
                        scope.launch {
                            var v = 0f
                            val g = -3000f
                            val frameMs = 16L
                            val dt = frameMs / 1000f
                            while (true) {
                                val prevBottom = heightPx
                                v += g * dt
                                heightPx += v * dt

                                val landingCols = playerOverlappingColumns(xPx)
                                var landingTopPx: Float? = null
                                val startRow = floor((prevBottom - 0.001f) / unitPx).toInt()
                                val endRow = floor((heightPx) / unitPx).toInt()
                                for (row in startRow downTo max(endRow, 0)) {
                                    var hit = false
                                    for (c in landingCols) { if (tileMap.get(c, row)) { hit = true; break } }
                                    if (hit) {
                                        val topPx = (row + 1) * unitPx
                                        if (prevBottom >= topPx && heightPx <= topPx) {
                                            landingTopPx =
                                                max(landingTopPx ?: Float.NEGATIVE_INFINITY, topPx)
                                        }
                                    }
                                }
                                // Consider landing on peers' tops as support
                                if (connectionStatus) {
                                    peersState.values.forEach { peer ->
                                        val px = peer.worldXPx
                                        val pCols = run {
                                            val c0 = floor(px / unitPx).toInt()
                                            val c1 = floor((px + playerWidthPx - 0.001f) / unitPx).toInt()
                                            c0..c1
                                        }
                                        val horizOverlap = landingCols.any { it in pCols }
                                        if (horizOverlap) {
                                            val peerTop = peer.heightPx + playerHeightPx
                                            if (prevBottom >= peerTop && heightPx <= peerTop) {
                                                landingTopPx = max(landingTopPx ?: Float.NEGATIVE_INFINITY, peerTop)
                                            }
                                        }
                                    }
                                }
                                if (landingTopPx != null) {
                                    heightPx = landingTopPx!!
                                    isJumping = false
                                    break
                                }
                                if (heightPx <= 0f) {
                                    heightPx = 0f
                                    isJumping = false
                                    break
                                }
                                // Bottom-overlap safety in passive fall: if feet ended up inside a tile, snap out and stop falling
                                var stopFall = false
                                run {
                                    val bottomRowNow = floor((heightPx - 0.001f) / unitPx).toInt()
                                    if (bottomRowNow >= 0) {
                                        var overlap = false
                                        for (c in landingCols) { if (tileMap.get(c, bottomRowNow)) { overlap = true; break } }
                                        if (overlap) {
                                            val topPx = (bottomRowNow + 1) * unitPx
                                            heightPx = topPx
                                            isJumping = false
                                            stopFall = true
                                        }
                                    }
                                }
                                if (stopFall) break
                                delay(frameMs)
                            }
                        }
                    }
                }
            }

            // Controls overlay
            // Bottom-left: circular joystick
            val joystickSize = 160.dp
            val thumbSize = 48.dp
            val trackPadding = 12.dp
            var knobOffsetPx by remember { mutableStateOf(Offset.Zero) } // offset from center
            var joystickX by remember { mutableStateOf(0f) } // -1..1 (horizontal component)
            var joystickY by remember { mutableStateOf(0f) } // -1..1 (vertical component; +up after global inversion)

            // Continuous movement loop driven by joystick horizontal value
            LaunchedEffect(Unit) {
                val frameMs = 16L
                val dt = frameMs / 1000f
                val maxSpeedPxPerSec = with(density) { 420.dp.toPx() } // tune max speed
                while (true) {
                    val v = if (isPlacePreviewing) 0f else joystickX
                    if (abs(v) > 0.01f) {
                        val deltaPx = v * maxSpeedPxPerSec * dt
                        playerWorldXDp = moveHorizontalSubStepped(playerWorldXDp, deltaPx, heightPx)
                        // TODO this needs to be converted to all directions, not just lef and right
                        lastDirection = if (v > 0f) Direction.Right else if (v < 0f) Direction.Left else lastDirection
                    }
                    delay(frameMs)
                }
            }

            val sizePx = with(density) { joystickSize.toPx() }
            val centerPx = sizePx / 2f
            val thumbRadiusPx = with(density) { (thumbSize.toPx() / 2f) }
            val paddingPx = with(density) { trackPadding.toPx() }
            val movementRadiusPx = centerPx - thumbRadiusPx - paddingPx

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .size(joystickSize)
                    .drawBehind {
                        // Outer circular track (very translucent)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.12f),
                            radius = size.minDimension / 2f,
                            center = Offset(size.width / 2f, size.height / 2f)
                        )
                        // Inner ring to indicate boundary
                        drawCircle(
                            color = Color.White.copy(alpha = 0.25f),
                            radius = (size.minDimension / 2f) - with(density) { 2.dp.toPx() },
                            center = Offset(size.width / 2f, size.height / 2f),
                            style = Stroke(width = with(density) { 3.dp.toPx() })
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { /* no-op */ },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                // Update knob offset and clamp to circular radius
                                val proposed = knobOffsetPx + Offset(dragAmount.x, dragAmount.y)
                                val dx = proposed.x
                                val dy = proposed.y
                                val mag = sqrt(dx*dx + dy*dy)
                                val clamped = if (mag > movementRadiusPx && mag > 0f) {
                                    val scale = movementRadiusPx / mag
                                    Offset(dx * scale, dy * scale)
                                } else proposed
                                knobOffsetPx = clamped

                                // Normalized components -1..1
                                joystickX = if (movementRadiusPx > 0f) (clamped.x / movementRadiusPx) else 0f
                                // Global inversion: pushing up should yield positive joystickY
                                joystickY = if (movementRadiusPx > 0f) (-clamped.y / movementRadiusPx) else 0f

                                // Determine last direction using 12-sector mapping (0°=Right, 90°=Up)
                                if (mag > movementRadiusPx * 0.1f) {
                                    // Global inversion: pass inverted Y so pushing up yields Up
                                    val dir = vectorToDirection(clamped.x, -clamped.y)
                                    val prevDir = lastDirection
                                    lastDirection = dir
                                    if (prevDir != lastDirection && connectionStatus) {
                                        // Immediately send updated facing/position so peers reflect true direction even if we don't move
                                        val xPxNow = xDpToPx(playerWorldXDp)
                                        val colNow = floor(xPxNow / unitPx).toInt()
                                        val rowNow = floor((heightPx) / unitPx).toInt()
                                        val wrappedCol = ((colNow % tileMap.width) + tileMap.width) % tileMap.width
                                        mp?.sendPos(wrappedCol, rowNow, lastDirection.isFacingRight())
                                    }
                                }
                            },
                            onDragEnd = {
                                knobOffsetPx = Offset.Zero
                                joystickX = 0f
                                joystickY = 0f
                            },
                            onDragCancel = {
                                knobOffsetPx = Offset.Zero
                                joystickX = 0f
                                joystickY = 0f
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Thumb (circular, translucent)
                Box(
                    modifier = Modifier
                        .offset(x = pxToDp(knobOffsetPx.x), y = pxToDp(knobOffsetPx.y))
                        .size(thumbSize)
                        .background(Color.White.copy(alpha = 0.3f), shape = CircleShape)
                )
            }

            // Bottom-right: Jump and Hit buttons, transparent containers
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                Button(
                    onClick = { jump() },
                    enabled = !isJumping,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
                ) { Text("Jump") }
                Spacer(modifier = Modifier.height(8.dp))
                val hitInteraction = remember { MutableInteractionSource() }
                val isHitPressed by hitInteraction.collectIsPressedAsState()
                LaunchedEffect(isHitPressed) {
                    while (isHitPressed) {
                        hit()
                        delay(700)
                    }
                }
                Button(
                    onClick = { hit() },
                    interactionSource = hitInteraction,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
                ) { Text("Hit") }
                Spacer(modifier = Modifier.height(8.dp))
                val placeInteraction = remember { MutableInteractionSource() }
                val isPlacePressed by placeInteraction.collectIsPressedAsState()
                LaunchedEffect(isPlacePressed) {
                    if (isPlacePressed) {
                        isPlacePreviewing = true
                    } else {
                        if (isPlacePreviewing) {
                            // On release, aim: adopt preview-selected facing if available
                            previewSelectedFacing?.let { lastDirection = it }
                            // attempt to place
                            place()
                        }
                        isPlacePreviewing = false
                        previewSelectedAimCol = null
                        previewSelectedAimRow = null
                        previewSelectedFacing = null
                    }
                }
                Button(
                    onClick = { place() },
                    interactionSource = placeInteraction,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
                ) { Text("Place") }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GameScreenPreview() {
    DontGoToBedTheme {
        GameScreen(player = Player.Leo, mp = null)
    }
}