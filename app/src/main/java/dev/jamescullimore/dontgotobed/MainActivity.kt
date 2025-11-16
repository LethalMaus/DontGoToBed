package dev.jamescullimore.dontgotobed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import dev.jamescullimore.dontgotobed.ui.theme.DontGoToBedTheme

enum class Direction { Up, Down, Left, Right }

enum class Player { Leo, Ian, Papa }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Keep the screen awake during gameplay
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Use immersive fullscreen: hide status and navigation bars
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        setContent {
            DontGoToBedTheme {
                val context = LocalContext.current
                var selected by remember { mutableStateOf<Player?>(null) }
                // Multiplayer pre-selection captured on the character screen
                var initialIsHost by remember { mutableStateOf<Boolean?>(null) }
                var initialHostIp by remember { mutableStateOf(MultiplayerManager.DEFAULT_HOST_IP) }
                val localIp = remember { getLocalIpAddress() }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Shared MultiplayerManager instance for the whole app session (persists while app is alive)
                        var sharedMp by remember { mutableStateOf<MultiplayerManager?>(null) }
                        if (selected == null) {
                            SelectCharacterScreen(
                                localIp = localIp,
                                mp = sharedMp,
                                setMp = { newMp -> sharedMp = newMp },
                                onSelect = { player, isHost, joinIp ->
                                    selected = player
                                    initialIsHost = isHost
                                    if (!isHost) {
                                        // For client, use entered IP if provided
                                        initialHostIp = joinIp?.ifBlank { MultiplayerManager.DEFAULT_HOST_IP } ?: MultiplayerManager.DEFAULT_HOST_IP
                                    } else {
                                        // Host doesn't need hostIp to bind; keep default
                                        initialHostIp = MultiplayerManager.DEFAULT_HOST_IP
                                    }
                                }
                            )
                        } else {
                            GameScreen(
                                player = selected!!,
                                initialIsHostSelected = initialIsHost,
                                initialHostIp = initialHostIp,
                                mp = sharedMp
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Re-apply immersive mode when window regains focus
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
            insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }
}

private fun getLocalIpAddress(): String {
    return try {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
        val list = java.util.Collections.list(interfaces)
        for (ni in list) {
            if (!ni.isUp || ni.isLoopback) continue
            val addrs = java.util.Collections.list(ni.inetAddresses)
            for (addr in addrs) {
                if (addr is java.net.Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                    return addr.hostAddress
                }
            }
        }
        "0.0.0.0"
    } catch (e: Exception) {
        "0.0.0.0"
    }
}

@Composable
fun SelectCharacterScreen(localIp: String, mp: MultiplayerManager?, setMp: (MultiplayerManager?) -> Unit, onSelect: (Player, Boolean, String?) -> Unit) {
    var roleHost by remember { mutableStateOf<Boolean?>(null) }
    var joinIp by remember { mutableStateOf("") }

    // UI state only
    var testing by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var peerSelected by remember { mutableStateOf<String?>(null) }

    fun attachUiListener(manager: MultiplayerManager?) {
        manager?.updateListener(object : MultiplayerManager.Listener {
            override fun onConnectionChanged(connectedNow: Boolean) {
                connected = connectedNow
                if (connectedNow) testing = false
            }
            override fun onPeerSelectedPlayer(name: String) {
                peerSelected = name
            }
        })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Choose your character", color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))

            // Multiplayer options inline on selection screen
            Text(text = "Multiplayer", color = Color.White)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    if (roleHost != true) {
                        roleHost = true
                        testing = true
                        // Create or reuse shared manager as Host
                        val mgr = MultiplayerManager(
                            isHost = true,
                            hostIp = MultiplayerManager.DEFAULT_HOST_IP,
                            port = MultiplayerManager.DEFAULT_PORT,
                            initialListener = object : MultiplayerManager.Listener {
                                override fun onConnectionChanged(connectedNow: Boolean) {
                                    connected = connectedNow
                                    if (connectedNow) testing = false
                                }
                            }
                        )
                        setMp(mgr)
                        mgr.start()
                    } else {
                        // Already host, just ensure we receive callbacks
                        attachUiListener(mp)
                    }
                }) { Text("Host") }
                Spacer(Modifier.width(12.dp))
                Button(onClick = {
                    if (roleHost != false) {
                        roleHost = false
                        testing = false
                        connected = false
                        // Do not stop shared connection here; user will press Test to connect as client
                    }
                }) { Text("Join") }
            }
            Spacer(Modifier.height(6.dp))
            if (roleHost == true) {
                Text(text = "Your IP: $localIp  Port: ${MultiplayerManager.DEFAULT_PORT}", color = Color.White)
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    // Reattach listener if already hosting
                    attachUiListener(mp)
                }) { Text(if (!connected && !testing) "Waiting for client..." else if (testing) "Waiting for client..." else "Connected!") }
            } else if (roleHost == false) {
                androidx.compose.material3.TextField(
                    value = joinIp,
                    onValueChange = { joinIp = it },
                    label = { Text("Enter Host IP") }
                )
                Spacer(Modifier.height(4.dp))
                Text(text = "Port: ${MultiplayerManager.DEFAULT_PORT} (fixed)", color = Color.White)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        testing = true
                        val ip = joinIp.ifBlank { MultiplayerManager.DEFAULT_HOST_IP }
                        val mgr = MultiplayerManager(
                            isHost = false,
                            hostIp = ip,
                            port = MultiplayerManager.DEFAULT_PORT,
                            initialListener = object : MultiplayerManager.Listener {
                                override fun onConnectionChanged(connectedNow: Boolean) {
                                    connected = connectedNow
                                    testing = false
                                }
                            }
                        )
                        setMp(mgr)
                        mgr.start()
                    },
                    enabled = joinIp.isNotBlank() && !connected && !testing
                ) { Text(if (!connected && !testing) "Test Connection" else if (testing) "Connecting..." else "Connected!") }
            }

            if (connected) {
                Spacer(Modifier.height(6.dp))
                Text(text = "Connected! You can select your character.", color = Color.Green)
                if (peerSelected != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(text = "Peer selected: $peerSelected", color = Color.Yellow)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Leo
            Button(
                onClick = {
                    mp?.sendSelectedPlayer(Player.Leo.name)
                    onSelect(Player.Leo, roleHost ?: true, if (roleHost == false) joinIp else null)
                },
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.drawable.leo_front),
                    contentDescription = "Leo",
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Leo")
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Ian
            Button(
                onClick = {
                    mp?.sendSelectedPlayer(Player.Ian.name)
                    onSelect(Player.Ian, roleHost ?: true, if (roleHost == false) joinIp else null)
                },
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.drawable.ian_front),
                    contentDescription = "Ian",
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ian")
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Papa
            Button(
                onClick = {
                    mp?.sendSelectedPlayer(Player.Papa.name)
                    onSelect(Player.Papa, roleHost ?: true, if (roleHost == false) joinIp else null)
                },
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.drawable.papa_front),
                    contentDescription = "Papa",
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Papa")
            }
        }
    }
}

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

        // Multiplayer role selection and networking
        var isHostSelected by remember { mutableStateOf<Boolean?>(initialIsHostSelected) } // null until chosen
        var connectionStatus by remember { mutableStateOf(false) }
        var hostIp by remember { mutableStateOf(initialHostIp) }

        // World unit and sizes
        val UNIT = 15.dp
        val step = 16.dp
        val playerWidth = UNIT * 2
        val playerHeight = UNIT * 3
        val centerX = (maxWidth - playerWidth) / 2
        val screenWidth = maxWidth

        // Convert sizes to px for collision math
        val unitPx = with(density) { UNIT.toPx() }
        val playerWidthPx = with(density) { playerWidth.toPx() }
        val playerHeightPx = with(density) { playerHeight.toPx() }

        // Block-based map on micro-grid. occ stores blockId or -1 at each cell
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
        class TileMap(val width: Int, val height: Int) {
            val maxHealth = 30
            private val occ = IntArray(width * height) { -1 }
            private val blocks = mutableMapOf<Int, Block>()
            private var nextId = 1

            fun forEachBlock(action: (Block) -> Unit) {
                // Iterate over current blocks without exposing internal map
                blocks.values.forEach(action)
            }

            fun idx(x: Int, y: Int): Int = (y * width) + ((x % width) + width) % width
            fun inY(y: Int) = y >= 0 && y < height
            fun get(x: Int, y: Int): Boolean = inY(y) && occ[idx(x, y)] != -1
            fun getHealth(x: Int, y: Int): Int {
                if (!inY(y)) return 0
                val id = occ[idx(x, y)]
                return if (id == -1) 0 else (blocks[id]?.health ?: 0)
            }
            fun setHealth(x: Int, y: Int, health: Int) {
                if (!inY(y)) return
                val id = occ[idx(x, y)]
                val b = blocks[id] ?: return
                val clamped = health.coerceIn(0, b.maxHealth)
                b.health = clamped
                if (b.health <= 0) removeBlock(id)
            }
            fun set(x: Int, y: Int, solid: Boolean) {
                if (!inY(y)) return
                if (solid) {
                    // Place a 3x3 block anchored at (x,y)
                    placeBlock(x, y, 3, 3, Color(0xFF2E8B57))
                } else {
                    val id = occ[idx(x, y)]
                    if (id != -1) removeBlock(id)
                }
            }
            fun placeBlock(col: Int, row: Int, w: Int, h: Int, color: Color, health: Int = maxHealth): Int {
                // Check bounds and emptiness
                if (!inY(row) || !inY(row + h - 1)) return -1
                val anchorColWrapped = ((col % width) + width) % width
                for (dx in 0 until w) {
                    val cx = (anchorColWrapped + dx) % width
                    for (dy in 0 until h) {
                        val ry = row + dy
                        if (!inY(ry) || occ[idx(cx, ry)] != -1) return -1
                    }
                }
                val id = nextId++
                val b = Block(id, anchorColWrapped, row, w, h, health, maxHealth, color)
                blocks[id] = b
                for (dx in 0 until w) {
                    val cx = (anchorColWrapped + dx) % width
                    for (dy in 0 until h) occ[idx(cx, row + dy)] = id
                }
                return id
            }
            fun removeBlock(id: Int) {
                val b = blocks[id] ?: return
                for (dx in 0 until b.w) {
                    val cx = (b.col + dx) % width
                    for (dy in 0 until b.h) occ[idx(cx, b.row + dy)] = -1
                }
                blocks.remove(id)
            }
            fun damage(x: Int, y: Int, amount: Int): Int {
                if (!inY(y)) return 0
                val id = occ[idx(x, y)]
                val b = blocks[id] ?: return 0
                b.health = (b.health - amount).coerceAtLeast(0)
                if (b.health <= 0) removeBlock(id)
                return b.health
            }
            fun snapshotBlocks(): List<Block> = blocks.values.map { it.copy() }
        }

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

        // Player state
        var playerWorldXDp by remember { mutableStateOf(0.dp) } // horizontal position in world space
        var heightPx by remember { mutableStateOf(0f) } // vertical bottom height in px above ground
        var isJumping by remember { mutableStateOf(false) }
        var lastDirection by remember { mutableStateOf(Direction.Right) }

        // Peer (remote) player state for host to render
        // Smoothed render position (dp/px) and target position (px) derived from grid POS updates
        var peerWorldXDp by remember { mutableStateOf(0.dp) }
        var peerWorldXPx by remember { mutableStateOf(0f) }
        var peerHeightPx by remember { mutableStateOf(0f) }
        var peerTargetXPx by remember { mutableStateOf(0f) }
        var peerTargetHeightPx by remember { mutableStateOf(0f) }
        var peerHasPos by remember { mutableStateOf(false) }
        var peerFacingRight by remember { mutableStateOf(true) }
        var peerSelectedPlayer by remember { mutableStateOf<Player?>(null) }

        // Increment to trigger recomposition when map content changes (e.g., tile destroyed)
        var mapVersion by remember { mutableStateOf(0) }

        // One-time spawn: start above the map and let gravity bring the player down onto the next platform or ground
        var didInitialSpawn by remember { mutableStateOf(false) }
        LaunchedEffect(tileMap.width) {
            if (!didInitialSpawn) {
                // Position player high above the highest tiles so they "fall in" from the top
                heightPx = (tileMap.height * unitPx) + playerHeightPx * 2f
                // Keep isJumping false so the existing unsupported check triggers the falling coroutine
                isJumping = false
                didInitialSpawn = true
            }
        }

        // Helpers: convert world x dp to pixel and tile indices
        fun xDpToPx(x: Dp): Float = with(density) { x.toPx() }
        fun pxToDp(px: Float): Dp = with(density) { px.toDp() }

        fun playerOverlappingColumns(xPx: Float): IntRange {
            val left = xPx
            val right = xPx + playerWidthPx - 0.001f
            val c0 = kotlin.math.floor(left / unitPx).toInt()
            val c1 = kotlin.math.floor(right / unitPx).toInt()
            return c0..c1
        }
        fun playerOverlappingRows(bottomPx: Float): IntRange {
            val bottom = bottomPx
            val top = bottomPx + playerHeightPx - 0.001f
            val r0 = kotlin.math.floor(bottom / unitPx).toInt()
            val r1 = kotlin.math.floor(top / unitPx).toInt()
            return r0..r1
        }
        fun anySolidInColumnsRows(cols: IntRange, rows: IntRange): Boolean {
            for (c in cols) for (r in rows) if (tileMap.get(c, r)) return true
            return false
        }

        // Horizontal collision using tile grid with wrap
        fun applyHorizontalCollision(prevXDp: Dp, nextXDp: Dp, bottomPx: Float): Dp {
            var nxPx = xDpToPx(nextXDp)
            val prevPx = xDpToPx(prevXDp)
            val movingRight = nxPx > prevPx
            val rows = playerOverlappingRows(bottomPx)
            if (movingRight) {
                val rightEdge = nxPx + playerWidthPx - 0.001f
                val col = kotlin.math.floor(rightEdge / unitPx).toInt()
                // If the leading column has any solid in our rows, clamp to its left edge
                val cols = col..col
                if (anySolidInColumnsRows(cols, rows)) {
                    val tileLeftPx = col * unitPx
                    nxPx = tileLeftPx - playerWidthPx
                }
            } else if (nxPx < prevPx) {
                val leftEdge = nxPx
                val col = kotlin.math.floor(leftEdge / unitPx).toInt()
                val cols = col..col
                if (anySolidInColumnsRows(cols, rows)) {
                    val tileRightPx = (col + 1) * unitPx
                    nxPx = tileRightPx
                }
            }
            // Wrap horizontally to keep values bounded (visuals still center on player)
            val worldWidthPx = tileMap.width * unitPx
            // Normalize nxPx to a stable range to reduce float drift
            nxPx = ((nxPx % worldWidthPx) + worldWidthPx) % worldWidthPx
            return pxToDp(nxPx)
        }

        fun moveLeft() {
            val tentative = playerWorldXDp - step
            playerWorldXDp = applyHorizontalCollision(playerWorldXDp, tentative, heightPx)
            lastDirection = Direction.Left
            if (isHostSelected == false) {
                mp?.sendInput(left = true, right = false, jump = false, hit = false)
            }
        }
        fun moveRight() {
            val tentative = playerWorldXDp + step
            playerWorldXDp = applyHorizontalCollision(playerWorldXDp, tentative, heightPx)
            lastDirection = Direction.Right
            if (isHostSelected == false) {
                mp?.sendInput(left = false, right = true, jump = false, hit = false)
            }
        }

        // Core action helpers (host-authoritative). They return true if world changed.
        fun performHitAt(xDp: Dp, bottomPx: Float, facing: Direction): Boolean {
            val xPx = xDpToPx(xDp)
            val centerXPx = xPx + playerWidthPx / 2f
            val colCenter = kotlin.math.floor(centerXPx / unitPx).toInt()
            val centerYPx = bottomPx + playerHeightPx / 2f
            val rowCenter = kotlin.math.floor(centerYPx / unitPx).toInt()
            // Determine edge-adjacent target cell based on facing (use player edges, not center)
            val leftCol = kotlin.math.floor((xPx) / unitPx).toInt()
            val rightCol = kotlin.math.floor((xPx + playerWidthPx - 0.001f) / unitPx).toInt()
            val bottomRow = kotlin.math.floor((bottomPx - 0.001f) / unitPx).toInt()
            val topRow = kotlin.math.floor(((bottomPx + playerHeightPx - 0.001f) / unitPx)).toInt()
            val midRow = (bottomRow + topRow) / 2
            var targetCol = colCenter
            var targetRow = rowCenter
            when (facing) {
                Direction.Left -> { targetCol = leftCol - 1; targetRow = midRow }
                Direction.Right -> { targetCol = rightCol + 1; targetRow = midRow }
                Direction.Up -> { targetCol = colCenter; targetRow = topRow + 1 }
                Direction.Down -> { targetCol = colCenter; targetRow = bottomRow - 1 }
            }
            if (!tileMap.inY(targetRow)) return false
            if (!tileMap.get(targetCol, targetRow)) return false
            val remaining = tileMap.damage(targetCol, targetRow, 10)
            mapVersion++
            if (isHostSelected == true) {
                val wrappedX = ((targetCol % tileMap.width) + tileMap.width) % tileMap.width
                if (remaining <= 0) mp?.sendTileDestroyed(wrappedX, targetRow)
                else mp?.sendTileHealth(wrappedX, targetRow, remaining)
            }
            return true
        }

        fun performPlaceAt(xDp: Dp, bottomPx: Float, facing: Direction): Boolean {
            val xPx = xDpToPx(xDp)
            val centerXPx = xPx + playerWidthPx / 2f
            val colCenter = kotlin.math.floor(centerXPx / unitPx).toInt()
            val leftCol = kotlin.math.floor((xPx) / unitPx).toInt()
            val rightCol = kotlin.math.floor((xPx + playerWidthPx - 0.001f) / unitPx).toInt()
            val bottomRow = kotlin.math.floor((bottomPx - 0.001f) / unitPx).toInt()
            val topRow = kotlin.math.floor(((bottomPx + playerHeightPx - 0.001f) / unitPx)).toInt()

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

            // Choose anchor for a 3x3 block depending on facing. Enforce adjacency-only placement (no vertical scan).
            var targetCol = 0
            var targetRow = 0
            var hasTarget = false
            when (facing) {
                Direction.Left -> {
                    // Block must be directly touching the player's left face
                    targetCol = leftCol - 3
                    // Align the 3x3 block base with the player's base row
                    val playerRows = playerOverlappingRows(bottomPx)
                    targetRow = playerRows.first
                    // Require vertical overlap so it's truly adjacent
                    val placeRows = targetRow..(targetRow + 2)
                    val touchesVertically = placeRows.any { it in playerRows }
                    hasTarget = touchesVertically && canPlaceAt(targetCol, targetRow)
                }
                Direction.Right -> {
                    // Block must be directly touching the player's right face
                    targetCol = rightCol + 1
                    val playerRows = playerOverlappingRows(bottomPx)
                    targetRow = playerRows.first
                    val placeRows = targetRow..(targetRow + 2)
                    val touchesVertically = placeRows.any { it in playerRows }
                    hasTarget = touchesVertically && canPlaceAt(targetCol, targetRow)
                }
                Direction.Up -> {
                    // Block must be directly above, touching the player's head
                    targetCol = colCenter - 1
                    targetRow = topRow + 1
                    val playerCols = playerOverlappingColumns(xPx)
                    val placeCols = targetCol..(targetCol + 2)
                    val touchesHorizontally = placeCols.any { it in playerCols }
                    hasTarget = touchesHorizontally && canPlaceAt(targetCol, targetRow)
                }
                Direction.Down -> {
                    // Block must be directly below, touching the player's feet
                    targetCol = colCenter - 1
                    targetRow = bottomRow - 3
                    val playerCols = playerOverlappingColumns(xPx)
                    val placeCols = targetCol..(targetCol + 2)
                    val touchesHorizontally = placeCols.any { it in playerCols }
                    hasTarget = touchesHorizontally && canPlaceAt(targetCol, targetRow)
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

        // Hit logic: damage adjacent tile based on last facing.
        fun hit() {
            // Client just requests; host performs and broadcasts
            if (isHostSelected == false) {
                mp?.sendInput(left = false, right = false, jump = false, hit = true)
                return
            }
            performHitAt(playerWorldXDp, heightPx, lastDirection)
        }

        // Place logic: place a 3x3 block into the adjacent cell (anchor) in last look direction
        fun place() {
            if (isHostSelected == false) {
                mp?.sendInput(left = false, right = false, jump = false, hit = false, place = true)
                return
            }
            performPlaceAt(playerWorldXDp, heightPx, lastDirection)
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
                var v = 1000f // px/s upward
                val g = -3000f // px/s^2
                val frameMs = 16L
                val dt = frameMs / 1000f
                while (true) {
                    val prevBottom = heightPx
                    v += g * dt
                    heightPx += v * dt

                    val xPx = xDpToPx(playerWorldXDp)
                    val cols = playerOverlappingColumns(xPx)

                    // Ceiling collision when ascending: sweep all crossed rows and clamp to the first tile bottom
                    if (v > 0f) {
                        val prevTop = prevBottom + playerHeightPx
                        val nowTop = heightPx + playerHeightPx
                        val startRow = kotlin.math.floor(prevTop / unitPx).toInt()
                        val endRow = kotlin.math.floor(nowTop / unitPx).toInt()
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
                        // Safety: if after integration our top is already inside a solid tile (e.g., block placed above), resolve
                        run {
                            val topRowNow = kotlin.math.floor(((heightPx + playerHeightPx - 0.001f) / unitPx)).toInt()
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
                        val startRow = kotlin.math.floor((prevBottom - 0.001f) / unitPx).toInt()
                        val endRow = kotlin.math.floor((heightPx) / unitPx).toInt()
                        if (endRow < startRow || heightPx <= 0f) {
                            var landingTopPx: Float? = null
                            // Sweep all crossed row tops and find the highest top we intersected
                            for (row in startRow downTo kotlin.math.max(endRow, 0)) {
                                var hit = false
                                for (c in cols) { if (tileMap.get(c, row)) { hit = true; break } }
                                if (hit) {
                                    val topPx = (row + 1) * unitPx
                                    if (prevBottom >= topPx && heightPx <= topPx) {
                                        landingTopPx = kotlin.math.max(landingTopPx ?: Float.NEGATIVE_INFINITY, topPx)
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
                        }
                    }

                    kotlinx.coroutines.delay(frameMs)
                }
            }
        }

        // Attach gameplay listener to existing multiplayer session
        LaunchedEffect(mp, isHostSelected) {
            mp?.updateListener(object : MultiplayerManager.Listener {
                override fun onPeerInput(left: Boolean, right: Boolean, jump: Boolean, hit: Boolean, place: Boolean) {
                    if (isHostSelected == true) {
                        // Do not move peer immediately here; position is authoritative via POS messages.
                        // Only update facing intent for visual orientation.
                        if (left) peerFacingRight = false
                        if (right) peerFacingRight = true
                        // Perform actions host-authoritatively at peer's current (smoothed) position
                        if (hit) {
                            val facing = if (left) Direction.Left else if (right) Direction.Right else if (peerFacingRight) Direction.Right else Direction.Left
                            performHitAt(peerWorldXDp, peerHeightPx, facing)
                        }
                        if (place) {
                            val facing = if (left) Direction.Left else if (right) Direction.Right else if (peerFacingRight) Direction.Right else Direction.Left
                            performPlaceAt(peerWorldXDp, peerHeightPx, facing)
                        }
                        // Jump is handled via physics sync; no action here.
                    }
                }
                override fun onPeerPos(col: Int, row: Int, facingRight: Boolean) {
                    // Update target grid coords for smoothing loop
                    peerTargetXPx = col * unitPx
                    peerTargetHeightPx = row * unitPx
                    if (!peerHasPos) {
                        // First position: snap immediately to avoid long glide from (0,0)
                        peerWorldXPx = peerTargetXPx
                        peerHeightPx = peerTargetHeightPx
                        peerWorldXDp = pxToDp(peerWorldXPx)
                        peerHasPos = true
                    }
                    peerFacingRight = facingRight
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
                override fun onConnectionChanged(connected: Boolean) {
                    connectionStatus = connected
                    if (connected) {
                        mp?.sendSelectedPlayer(player.name)
                    }
                }
                override fun onPeerSelectedPlayer(name: String) {
                    peerSelectedPlayer = when (name) {
                        Player.Leo.name -> Player.Leo
                        Player.Ian.name -> Player.Ian
                        Player.Papa.name -> Player.Papa
                        else -> null
                    }
                }
            })
        }

        // Periodically send our position to the peer (both host and client)
        LaunchedEffect(connectionStatus, isHostSelected, playerWorldXDp, heightPx, lastDirection) {
            if (connectionStatus) {
                // Send our grid-based position (col,row) and facing to avoid dp/px inconsistencies
                val xPx = xDpToPx(playerWorldXDp)
                val col = kotlin.math.floor(xPx / unitPx).toInt()
                val row = kotlin.math.floor((heightPx) / unitPx).toInt()
                val wrappedCol = ((col % tileMap.width) + tileMap.width) % tileMap.width
                val facingRightFlag = (lastDirection == Direction.Right)
                mp?.sendPos(wrappedCol, row, facingRightFlag)
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
                val peerSel = peerSelectedPlayer?.name
                val label = (if (isHostSelected == true) "Host: $status" else "Client: $status") + (if (peerSel != null) " | Peer selected: $peerSel" else "")
                Text(
                    text = label,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                )
            }

            // Play area fills whole screen
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFF87CEEB))
            ) {
                // Camera offset in dp (world x at player minus screen center)
                val cameraOffsetXDp = playerWorldXDp - centerX
                val cameraOffsetXPx = xDpToPx(cameraOffsetXDp)

                // Observe mapVersion so UI updates when tiles are destroyed
                val _mv = mapVersion
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
                                    .size(width = UNIT * b.w, height = UNIT * b.h)
                                    .background(blockColor)
                            )
                        }
                    }
                }

                // Player sprite (always centered)
                val heightDp = pxToDp(heightPx)
                val faceRight = lastDirection == Direction.Right
                androidx.compose.foundation.Image(
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
                        .graphicsLayer { scaleX = if (faceRight) -1f else 1f }
                )

                // Render remote player (peer) on both host and client when connected
                if (connectionStatus) {
                    val peerX = centerX + (peerWorldXDp - playerWorldXDp)
                    val peerHeightDp = pxToDp(peerHeightPx)
                    val peerRes = when (peerSelectedPlayer) {
                        Player.Leo -> R.drawable.leo_front
                        Player.Ian -> R.drawable.ian_front
                        Player.Papa -> R.drawable.papa_front
                        else -> R.drawable.ian_front
                    }
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = peerRes),
                        contentDescription = "Peer",
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = peerX, y = -peerHeightDp)
                            .height(playerHeight)
                            .graphicsLayer { scaleX = if (peerFacingRight) -1f else 1f }
                    )
                }
            }

            // Smoothly interpolate remote player's movement to avoid jumpy motion
            LaunchedEffect(connectionStatus, unitPx) {
                while (connectionStatus) {
                    val worldWidthPxF = tileMap.width * unitPx
                    // Adjust target across wrap so we move the short way
                    fun adjustTarget(current: Float, target: Float): Float {
                        var t = target
                        var d = t - current
                        if (kotlin.math.abs(d) > worldWidthPxF / 2f) {
                            t += if (d > 0f) -worldWidthPxF else worldWidthPxF
                        }
                        return t
                    }
                    val adjTargetX = adjustTarget(peerWorldXPx, peerTargetXPx)
                    val adjTargetY = peerTargetHeightPx
                    val alpha = 0.25f // smoothing factor (0..1), higher = snappier
                    peerWorldXPx += (adjTargetX - peerWorldXPx) * alpha
                    peerHeightPx += (adjTargetY - peerHeightPx) * alpha
                    // Normalize wrap and expose dp for rendering
                    val worldWidthPx = tileMap.width * unitPx
                    peerWorldXPx = ((peerWorldXPx % worldWidthPx) + worldWidthPx) % worldWidthPx
                    peerWorldXDp = pxToDp(peerWorldXPx)
                    kotlinx.coroutines.delay(16L)
                }
            }

            // Start falling if leaving support (walk off edges)
            LaunchedEffect(playerWorldXDp, isJumping) {
                if (!isJumping) {
                    val xPx = xDpToPx(playerWorldXDp)
                    val cols = playerOverlappingColumns(xPx)
                    val supportedTopPxCandidates = mutableListOf<Float>()
                    for (c in cols) {
                        // Support exists if there is a solid tile directly under the player's bottom
                        val rowUnder = kotlin.math.floor((heightPx - 0.001f) / unitPx).toInt()
                        if (rowUnder >= 0 && tileMap.get(c, rowUnder)) {
                            supportedTopPxCandidates += (rowUnder + 1) * unitPx
                        } else if (heightPx == 0f) {
                            supportedTopPxCandidates += 0f
                        }
                    }
                    val supported = supportedTopPxCandidates.any { it == heightPx }
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
                                val startRow = kotlin.math.floor((prevBottom - 0.001f) / unitPx).toInt()
                                val endRow = kotlin.math.floor((heightPx) / unitPx).toInt()
                                for (row in startRow downTo kotlin.math.max(endRow, 0)) {
                                    var hit = false
                                    for (c in landingCols) { if (tileMap.get(c, row)) { hit = true; break } }
                                    if (hit) {
                                        val topPx = (row + 1) * unitPx
                                        if (prevBottom >= topPx && heightPx <= topPx) {
                                            landingTopPx = kotlin.math.max(landingTopPx ?: Float.NEGATIVE_INFINITY, topPx)
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
                                kotlinx.coroutines.delay(frameMs)
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
            var joystickY by remember { mutableStateOf(0f) } // -1..1 (vertical component; +down)

            // Continuous movement loop driven by joystick horizontal value
            LaunchedEffect(Unit) {
                val frameMs = 16L
                val dt = frameMs / 1000f
                val maxSpeedPxPerSec = with(density) { 420.dp.toPx() } // tune max speed
                while (true) {
                    val v = joystickX
                    if (kotlin.math.abs(v) > 0.01f) {
                        val deltaPx = v * maxSpeedPxPerSec * dt
                        val deltaDp = pxToDp(deltaPx)
                        val tentative = playerWorldXDp + deltaDp
                        playerWorldXDp = applyHorizontalCollision(playerWorldXDp, tentative, heightPx)
                        // Update facing based on horizontal movement too
                        lastDirection = if (v > 0f) Direction.Right else if (v < 0f) Direction.Left else lastDirection
                    }
                    kotlinx.coroutines.delay(frameMs)
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
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = with(density) { 3.dp.toPx() })
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
                                val mag = kotlin.math.sqrt(dx*dx + dy*dy)
                                val clamped = if (mag > movementRadiusPx && mag > 0f) {
                                    val scale = movementRadiusPx / mag
                                    Offset(dx * scale, dy * scale)
                                } else proposed
                                knobOffsetPx = clamped

                                // Normalized components -1..1
                                joystickX = if (movementRadiusPx > 0f) (clamped.x / movementRadiusPx) else 0f
                                joystickY = if (movementRadiusPx > 0f) (clamped.y / movementRadiusPx) else 0f

                                // Determine last cardinal direction (4 sectors = 25% each)
                                if (mag > movementRadiusPx * 0.1f) {
                                    val angle = kotlin.math.atan2(clamped.y, clamped.x) // radians, +Y is down
                                    val deg = Math.toDegrees(angle.toDouble()).toFloat()
                                    lastDirection = when {
                                        deg > -45f && deg <= 45f -> Direction.Right
                                        deg > 45f && deg <= 135f -> Direction.Down
                                        deg <= -135f || deg > 135f -> Direction.Left
                                        else -> Direction.Up
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
                        .background(Color.White.copy(alpha = 0.3f), shape = androidx.compose.foundation.shape.CircleShape)
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
                        kotlinx.coroutines.delay(700)
                    }
                }
                Button(
                    onClick = { hit() },
                    interactionSource = hitInteraction,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
                ) { Text("Hit") }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { place() },
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