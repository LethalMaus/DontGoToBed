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

        // Sizes
        val tileSize = 40.dp
        val blockSize = 40.dp // player size equals tile size
        val step = 16.dp
        val centerX = (maxWidth - blockSize) / 2
        val screenWidth = maxWidth

        // Convert sizes to px for collision math
        val tileSizePx = with(density) { tileSize.toPx() }
        val blockSizePx = with(density) { blockSize.toPx() }

        // Tile map model (x wraps, y does not). true = Solid, false = Empty
        data class TileMap(val width: Int, val height: Int, val tiles: MutableList<Boolean>, val hitCounts: MutableList<Int>) {
            fun idx(x: Int, y: Int): Int = (y * width) + ((x % width) + width) % width
            fun inY(y: Int) = y >= 0 && y < height
            fun get(x: Int, y: Int): Boolean = inY(y) && tiles[idx(x, y)]
            fun set(x: Int, y: Int, solid: Boolean) { if (inY(y)) tiles[idx(x, y)] = solid }
            fun addHit(x: Int, y: Int): Int {
                if (!inY(y)) return 0
                val i = idx(x, y)
                hitCounts[i] = (hitCounts[i] + 1).coerceAtLeast(0)
                return hitCounts[i]
            }
            fun resetHits(x: Int, y: Int) { if (inY(y)) hitCounts[idx(x, y)] = 0 }
        }

        // Build a sample map with ground and some platforms
        val tileMap by remember(maxWidth) {
            mutableStateOf(
                run {
                    val w = 64 // horizontal wrap length in tiles
                    val h = 18 // vertical tiles (0 is ground row)
                    val tiles = MutableList(w * h) { false }
                    val hitCounts = MutableList(w * h) { 0 }
                    val map = TileMap(w, h, tiles, hitCounts)
                    // Ground: fill row 0 with solid
                    for (x in 0 until w) map.set(x, 0, true)
                    // Some stacks and floating islands
                    // Stack at x=10, rows 1..3
                    map.set(10, 1, true); map.set(10, 2, true); map.set(10, 3, true)
                    // Floating 3-long at x=18..20, row 4
                    map.set(18, 4, true); map.set(19, 4, true); map.set(20, 4, true)
                    // Small steps
                    map.set(26, 1, true); map.set(27, 2, true); map.set(28, 3, true)
                    // A gap in ground and a bridge above
                    map.set(36, 0, false); map.set(37, 0, false); map.set(38, 0, false)
                    map.set(36, 2, true); map.set(37, 2, true); map.set(38, 2, true)
                    // Another tower
                    map.set(46, 1, true); map.set(46, 2, true)
                    // Return map
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
        var peerWorldXDp by remember { mutableStateOf(0.dp) }
        var peerHeightPx by remember { mutableStateOf(0f) }
        var peerFacingRight by remember { mutableStateOf(true) }
        var peerSelectedPlayer by remember { mutableStateOf<Player?>(null) }

        // Increment to trigger recomposition when map content changes (e.g., tile destroyed)
        var mapVersion by remember { mutableStateOf(0) }

        // One-time spawn: start above the map and let gravity bring the player down onto the next platform or ground
        var didInitialSpawn by remember { mutableStateOf(false) }
        LaunchedEffect(tileMap.width) {
            if (!didInitialSpawn) {
                // Position player high above the highest tiles so they "fall in" from the top
                heightPx = (tileMap.height * tileSizePx) + blockSizePx * 2f
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
            val right = xPx + blockSizePx - 0.001f
            val c0 = kotlin.math.floor(left / tileSizePx).toInt()
            val c1 = kotlin.math.floor(right / tileSizePx).toInt()
            return c0..c1
        }
        fun playerOverlappingRows(bottomPx: Float): IntRange {
            val bottom = bottomPx
            val top = bottomPx + blockSizePx - 0.001f
            val r0 = kotlin.math.floor(bottom / tileSizePx).toInt()
            val r1 = kotlin.math.floor(top / tileSizePx).toInt()
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
                val rightEdge = nxPx + blockSizePx - 0.001f
                val col = kotlin.math.floor(rightEdge / tileSizePx).toInt()
                // If the leading column has any solid in our rows, clamp to its left edge
                val cols = col..col
                if (anySolidInColumnsRows(cols, rows)) {
                    val tileLeftPx = col * tileSizePx
                    nxPx = tileLeftPx - blockSizePx
                }
            } else if (nxPx < prevPx) {
                val leftEdge = nxPx
                val col = kotlin.math.floor(leftEdge / tileSizePx).toInt()
                val cols = col..col
                if (anySolidInColumnsRows(cols, rows)) {
                    val tileRightPx = (col + 1) * tileSizePx
                    nxPx = tileRightPx
                }
            }
            // Wrap horizontally to keep values bounded (visuals still center on player)
            val worldWidthPx = tileMap.width * tileSizePx
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

        // Hit logic: damage adjacent tile in last facing direction; destroy after 3 hits
        fun hit() {
            if (isHostSelected == false) {
                mp?.sendInput(left = false, right = false, jump = false, hit = true)
            }
            val xPx = xDpToPx(playerWorldXDp)
            val centerXPx = xPx + blockSizePx / 2f
            val colCenter = kotlin.math.floor(centerXPx / tileSizePx).toInt()

            val centerYPx = heightPx + blockSizePx / 2f
            val rowCenter = kotlin.math.floor(centerYPx / tileSizePx).toInt()

            var targetCol = colCenter
            var targetRow = rowCenter
            when (lastDirection) {
                Direction.Left -> targetCol = colCenter - 1
                Direction.Right -> targetCol = colCenter + 1
                Direction.Up -> targetRow = rowCenter + 1
                Direction.Down -> targetRow = rowCenter - 1
            }
            // Y bounds check; X wraps in TileMap.get
            if (!tileMap.inY(targetRow)) return
            if (!tileMap.get(targetCol, targetRow)) return // nothing

            val hits = tileMap.addHit(targetCol, targetRow)
            if (hits >= 3) {
                tileMap.set(targetCol, targetRow, false)
                tileMap.resetHits(targetCol, targetRow)
                mapVersion++ // trigger recomposition so tile disappears
                // Sync to client if hosting
                if (isHostSelected == true) {
                    mp?.sendTileDestroyed(((targetCol % tileMap.width) + tileMap.width) % tileMap.width, targetRow)
                }
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

                    // Ceiling collision when ascending: player's top hits tile bottom
                    if (v > 0f) {
                        val prevTop = prevBottom + blockSizePx
                        val nowTop = heightPx + blockSizePx
                        val prevTopRowBoundary = kotlin.math.floor(prevTop / tileSizePx).toInt()
                        val nowTopRowBoundary = kotlin.math.floor(nowTop / tileSizePx).toInt()
                        if (nowTopRowBoundary > prevTopRowBoundary) {
                            val row = nowTopRowBoundary // row containing new top
                            var hit = false
                            for (c in cols) if (tileMap.get(c, row)) { hit = true; break }
                            if (hit) {
                                val tileBottomPx = row * tileSizePx
                                heightPx = tileBottomPx - blockSizePx
                                v = 0f
                            }
                        }
                    }

                    // Landing when descending: player's bottom crosses tile top
                    if (v <= 0f) {
                        val prevRowBoundary = kotlin.math.floor(prevBottom / tileSizePx).toInt()
                        val nowRowBoundary = kotlin.math.floor(heightPx / tileSizePx).toInt()
                        if (nowRowBoundary < prevRowBoundary || heightPx <= 0f) {
                            // Check all potential tile tops between prev and now (typically one row)
                            var landingTopPx: Float? = null
                            // Include ground row 0 as valid landing top at 1*tileSizePx top
                            // For each column, find highest solid tile top crossed
                            for (c in cols) {
                                val r = kotlin.math.floor((prevBottom - 0.001f) / tileSizePx).toInt()
                                // Check current row under player and below
                                val candidateRows = listOf(r, r - 1, 0).distinct()
                                for (row in candidateRows) {
                                    if (row < 0) continue
                                    if (tileMap.get(c, row)) {
                                        val topPx = (row + 1) * tileSizePx
                                        if (prevBottom >= topPx && heightPx <= topPx) {
                                            landingTopPx = kotlin.math.max(landingTopPx ?: Float.NEGATIVE_INFINITY, topPx)
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
                        }
                    }

                    kotlinx.coroutines.delay(frameMs)
                }
            }
        }

        // Attach gameplay listener to existing multiplayer session
        LaunchedEffect(mp, isHostSelected) {
            mp?.updateListener(object : MultiplayerManager.Listener {
                override fun onPeerInput(left: Boolean, right: Boolean, jump: Boolean, hit: Boolean) {
                    if (isHostSelected == true) {
                        // Apply rudimentary movement to peer on host
                        if (left) {
                            val tentative = peerWorldXDp - step
                            peerWorldXDp = applyHorizontalCollision(peerWorldXDp, tentative, peerHeightPx)
                            peerFacingRight = false
                        }
                        if (right) {
                            val tentative = peerWorldXDp + step
                            peerWorldXDp = applyHorizontalCollision(peerWorldXDp, tentative, peerHeightPx)
                            peerFacingRight = true
                        }
                        // ignore jump/hit here; host is authoritative for world changes
                    }
                }
                override fun onPeerPos(xDp: Float, heightPx: Float) {
                    // Update remote player's position regardless of our role
                    peerWorldXDp = with(density) { xDp.dp }
                    peerHeightPx = heightPx
                }
                override fun onTileDestroyed(x: Int, y: Int) {
                    if (isHostSelected == false) {
                        tileMap.set(x, y, false)
                        tileMap.resetHits(x, y)
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
        LaunchedEffect(connectionStatus, isHostSelected, playerWorldXDp, heightPx) {
            if (connectionStatus) {
                // fire-and-forget current position
                mp?.sendPos(playerWorldXDp.value, heightPx)
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
                    .pointerInput(tileMap.width, density) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            val deltaDp = with(density) { dragAmount.toDp() }
                            playerWorldXDp = applyHorizontalCollision(playerWorldXDp, playerWorldXDp + deltaDp, heightPx)
                        }
                    }
            ) {
                // Camera offset in dp (world x at player minus screen center)
                val cameraOffsetXDp = playerWorldXDp - centerX
                val cameraOffsetXPx = xDpToPx(cameraOffsetXDp)

                // Observe mapVersion so UI updates when tiles are destroyed
                val _mv = mapVersion
                // Render visible tiles with horizontal wrapping
                val viewportWidthPx = xDpToPx(screenWidth)
                val firstCol = kotlin.math.floor(cameraOffsetXPx / tileSizePx).toInt()
                val colsOnScreen = kotlin.math.ceil(viewportWidthPx / tileSizePx).toInt() + 2
                for (k in firstCol until firstCol + colsOnScreen) {
                    val mapCol = ((k % tileMap.width) + tileMap.width) % tileMap.width
                    val xDp = pxToDp(k * tileSizePx - cameraOffsetXPx)
                    for (row in 0 until tileMap.height) {
                        if (tileMap.get(mapCol, row)) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .offset(x = xDp, y = -pxToDp(row * tileSizePx))
                                    .size(tileSize)
                                    .background(Color.DarkGray)
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
                        .size(blockSize)
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
                            .size(blockSize)
                            .graphicsLayer { scaleX = if (peerFacingRight) -1f else 1f }
                    )
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
                        val rowUnder = kotlin.math.floor((heightPx - 0.001f) / tileSizePx).toInt()
                        if (rowUnder >= 0 && tileMap.get(c, rowUnder)) {
                            supportedTopPxCandidates += (rowUnder + 1) * tileSizePx
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
                                for (c in landingCols) {
                                    val r = kotlin.math.floor((prevBottom - 0.001f) / tileSizePx).toInt()
                                    val candidateRows = listOf(r, r - 1, 0).distinct()
                                    for (row in candidateRows) {
                                        if (row < 0) continue
                                        if (tileMap.get(c, row)) {
                                            val topPx = (row + 1) * tileSizePx
                                            if (prevBottom >= topPx && heightPx <= topPx) {
                                                landingTopPx = kotlin.math.max(landingTopPx ?: Float.NEGATIVE_INFINITY, topPx)
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