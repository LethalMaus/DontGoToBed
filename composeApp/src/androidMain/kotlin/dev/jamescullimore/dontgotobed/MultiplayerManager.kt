package dev.jamescullimore.dontgotobed

import java.util.concurrent.ConcurrentHashMap

import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.java_websocket.WebSocket
import org.java_websocket.framing.CloseFrame
import java.lang.Exception
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal peer-to-peer style multiplayer over WebSockets.
 * One device acts as Host (server) and the other as Client (joins).
 *
 * Hardcoded defaults:
 * - Host binds 0.0.0.0 on PORT
 * - Client connects to ws://HOST_IP:PORT
 *
 * Messages are plain strings to keep it lightweight (no JSON dependency):
 * - HELLO|role
 * - INPUT|left|right|jump|hit|place  (booleans as 0/1; 'place' optional, defaults to 0 if missing)
 * - POS|col|row|f             (ints, optional f=1 right, 0 left; if missing defaults to 1)
 * - TILE|destroy|x|y          (ints)
 * - TILE|place|x|y            (ints)
 * - TILE|hp|x|y|health        (ints)
 */
actual class MultiplayerManager actual constructor(
    private val isHost: Boolean,
    private val hostIp: String,
    private val port: Int,
    initialListener: Listener
) {
    // Listener can be updated by UI layers without restarting the connection
    @Volatile
    private var listener: Listener = initialListener

    // Multi-peer support
    data class PeerState(
        var name: String? = null,
        var col: Int? = null,
        var latitude: Int? = null,
        var longitude: Int? = null,
        var row: Int? = null,
        var facingRight: Boolean? = null
    )

    // Server side: track many clients
    private val socketToId = ConcurrentHashMap<WebSocket, Int>()
    private val idToSocket = ConcurrentHashMap<Int, WebSocket>()
    private val peers = ConcurrentHashMap<Int, PeerState>()
    private var nextPeerId = 1 // Host uses id 0

    // Client side: cache the last known state of all peers as told by host (for late UI listeners)
    private val clientPeerCache = ConcurrentHashMap<Int, PeerState>()

    // Cache host (self) state for late-join synchronization when hosting
    @Volatile private var selfId: Int = if (isHost) 0 else -1
    @Volatile private var lastSelfName: String? = null
    @Volatile private var lastSelfCol: Int? = null
    @Volatile private var lastSelfRow: Int? = null
    @Volatile private var lastSelfFacingRight: Boolean? = null

    actual fun updateListener(newListener: Listener) {
        this.listener = newListener
        // Immediately inform the new listener about current connection state so UI can reflect it
        try {
            newListener.onConnectionChanged(connected.get())
        } catch (_: Exception) {}
        // Replay cached known peers (for UI that attaches late)
        try {
            // Server-side cache (host tracking many clients)
            peers.forEach { (id, ps) ->
                ps.name?.let { newListener.onPeerSelectedPlayer(id, it) }
                val c = ps.col; val r = ps.row; val fr = ps.facingRight
                if (c != null && r != null && fr != null) newListener.onPeerPos(id, c, r, fr, ps.latitude, ps.longitude)
            }
            // Host self state (id 0) to newly attached listeners
            if (isHost) {
                val c = lastSelfCol; val r = lastSelfRow; val fr = lastSelfFacingRight
                lastSelfName?.let { newListener.onPeerSelectedPlayer(0, it) }
                if (c != null && r != null && fr != null) newListener.onPeerPos(0, c, r, fr, lastLatitude, lastLongitude)
            }
            // Client-side cache (messages received from host about any peer, including host id 0)
            clientPeerCache.forEach { (id, ps) ->
                ps.name?.let { newListener.onPeerSelectedPlayer(id, it) }
                val c = ps.col; val r = ps.row; val fr = ps.facingRight
                if (c != null && r != null && fr != null) newListener.onPeerPos(id, c, r, fr, ps.latitude, ps.longitude)
            }
        } catch (_: Exception) {}
    }
    actual interface Listener {
        actual fun onRoundEvent(senderId: Int, payload: String)
        actual fun onPeerInput(id: Int, left: Boolean, right: Boolean, jump: Boolean, hit: Boolean, place: Boolean)
        actual fun onPeerPos(id: Int, col: Int, row: Int, facingRight: Boolean, latitude: Int?, longitude: Int?)
        actual fun onTileDestroyed(x: Int, y: Int)
        actual fun onTilePlaced(x: Int, y: Int)
        actual fun onTileHealth(x: Int, y: Int, health: Int)
        actual fun onPeerSelectedPlayer(id: Int, name: String)
        actual fun onPeerConnected(id: Int)
        actual fun onPeerDisconnected(id: Int)
        actual fun onConnectionChanged(connected: Boolean)
        actual fun onNpcState(kind: String, id: Long, xMilliTiles: Int, yMilliTiles: Int, facingRight: Boolean, hp: Int, stateOrdinal: Int, pulseMilli: Int)
        actual fun onNpcGone(kind: String, id: Long)
        actual fun onArrowState(id: Long, xMilliTiles: Int, yMilliTiles: Int)
        actual fun onArrowGone(id: Long)
        actual fun onPotionState(id: Long, col: Int, row: Int, type: ItemType)
        actual fun onPotionGone(id: Long)
    }

    private var server: WebSocketServer? = null
    private val serverClientSockets = ConcurrentHashMap.newKeySet<WebSocket>()
    private var client: WebSocketClient? = null

    private val connected = AtomicBoolean(false)
    private val reconnecting = AtomicBoolean(false)
    @Volatile private var shouldKeepTrying = true

    actual fun start() {
        shouldKeepTrying = true
        if (isHost) startServer() else startClient()
    }

    actual fun stop() {
        shouldKeepTrying = false
        reconnecting.set(false)
        try {
            serverClientSockets.forEach { it.close(CloseFrame.NORMAL) }
            serverClientSockets.clear()
            idToSocket.clear()
            socketToId.clear()
            peers.clear()
        } catch (_: Exception) {}
        try {
            client?.close()
        } catch (_: Exception) {}
        try {
            server?.stop()
        } catch (_: Exception) {}
        connected.set(false)
        listener.onConnectionChanged(false)
    }

    // Client -> Host messages (host also uses these to inform clients of host state)
    private var lastRoundSnapshot: String? = null
    actual fun sendRoundEvent(payload: String) {
        if (isHost && payload.startsWith("STATE;")) lastRoundSnapshot = payload
        send("ROUND|$payload")
    }

    actual fun sendInput(left: Boolean, right: Boolean, jump: Boolean, hit: Boolean, place: Boolean) {
        val msg = "INPUT|${b(left)}|${b(right)}|${b(jump)}|${b(hit)}|${b(place)}"
        send(msg)
    }

    // Internal de-duplication for POS messages to avoid unnecessary network traffic
    @Volatile private var lastSentPosCol: Int? = null
    @Volatile private var lastSentPosRow: Int? = null
    @Volatile private var lastSentPosFacingRight: Boolean? = null

    private var lastLatitude: Int? = null
    private var lastLongitude: Int? = null

    actual fun sendPos(col: Int, row: Int, facingRight: Boolean, latitude: Int, longitude: Int) {
        val changed = (lastLatitude != latitude) || (lastLongitude != longitude) || (lastSentPosCol != col) || (lastSentPosRow != row) || (lastSentPosFacingRight != facingRight)
        // Always update caches so late-join replay stays accurate
        if (isHost) {
            lastSelfCol = col; lastSelfRow = row; lastSelfFacingRight = facingRight
        }
        lastLatitude = latitude
        lastLongitude = longitude
        lastSentPosCol = col
        lastSentPosRow = row
        lastSentPosFacingRight = facingRight
        if (!changed) {
            // Skip sending duplicate POS with no effective change (same grid and facing)
            return
        }
        val msg = if (isHost) "POS|0|$col|$row|${b(facingRight)}|$latitude|$longitude" else "POS|$col|$row|${b(facingRight)}|$latitude|$longitude"
        send(msg)
    }

    // Host -> Client messages
    actual fun sendTileDestroyed(x: Int, y: Int) {
        val msg = "TILE|destroy|$x|$y"
        send(msg)
    }

    actual fun sendTilePlaced(x: Int, y: Int) {
        val msg = "TILE|place|$x|$y"
        send(msg)
    }

    actual fun sendTileHealth(x: Int, y: Int, health: Int) {
        val msg = "TILE|hp|$x|$y|$health"
        send(msg)
    }

    // Both directions: notify selected character
    actual fun sendSelectedPlayer(name: String) {
        if (isHost) {
            lastSelfName = name
            send("CHAR|0|$name")
        } else {
            send("CHAR|$name")
        }
    }

    actual fun sendNpcState(kind: String, id: Long, xMilliTiles: Int, yMilliTiles: Int, facingRight: Boolean, hp: Int, stateOrdinal: Int, pulseMilli: Int) {
        send("NPC|$kind|$id|$xMilliTiles|$yMilliTiles|${b(facingRight)}|$hp|$stateOrdinal|$pulseMilli")
    }

    actual fun sendNpcGone(kind: String, id: Long) {
        send("NPC|$kind|$id|gone")
    }

    actual fun sendArrowState(id: Long, xMilliTiles: Int, yMilliTiles: Int) {
        send("ARROW|$id|$xMilliTiles|$yMilliTiles")
    }

    actual fun sendArrowGone(id: Long) {
        send("ARROW|$id|gone")
    }

    actual fun sendPotionState(id: Long, col: Int, row: Int, type: ItemType) {
        send("POTION|$id|$col|$row|${type.name}")
    }

    actual fun sendPotionGone(id: Long) {
        send("POTION|$id|gone")
    }

    private fun send(msg: String) {
        try {
            if (isHost) {
                if (serverClientSockets.isEmpty()) {
                    Log.w(TAG, "send skipped (host): no clients")
                } else {
                    serverClientSockets.forEach { s -> runCatching { s.send(msg) } }
                }
            } else {
                if (client == null) {
                    Log.w(TAG, "send skipped (client): not connected")
                } else {
                    client?.send(msg)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "send failed: ${e.message}")
        }
    }

    private fun startServer() {
        val addr = InetSocketAddress("0.0.0.0", port)
        server = object : WebSocketServer(addr) {
            override fun onStart() {
                Log.i(TAG, "Server started on ${address}")
            }
            override fun onOpen(conn: WebSocket, handshake: ClientHandshake?) {
                Log.i(TAG, "Client connected: ${conn.remoteSocketAddress}")
                serverClientSockets.add(conn)
                // Assign a new peer ID
                val id = nextPeerId++
                socketToId[conn] = id
                idToSocket[id] = conn
                peers[id] = PeerState()
                // Notify listener
                listener.onPeerConnected(id)
                // Ensure server marked as connected
                connected.set(true)
                listener.onConnectionChanged(true)
                // Welcome this client with its assigned ID
                conn.send("WELCOME|$id")
                lastRoundSnapshot?.let { conn.send("ROUND|$it") }
                // Replay known states (including host as id 0) to the newcomer
                if (lastSelfName != null) conn.send("CHAR|0|${lastSelfName}")
                if (lastSelfCol != null && lastSelfRow != null && lastSelfFacingRight != null) {
                    conn.send("POS|0|${lastSelfCol}|${lastSelfRow}|${b(lastSelfFacingRight!!)}|$lastLatitude|$lastLongitude")
                }
                peers.forEach { (pid, ps) ->
                    if (pid != id) {
                        ps.name?.let { conn.send("CHAR|$pid|$it") }
                        val c = ps.col; val r = ps.row; val fr = ps.facingRight
                        if (c != null && r != null && fr != null) conn.send("POS|$pid|$c|$r|${b(fr)}|${ps.latitude}|${ps.longitude}")
                    }
                }
            }
            override fun onClose(conn: WebSocket, code: Int, reason: String?, remote: Boolean) {
                Log.i(TAG, "Client disconnected: code=$code reason=$reason remote=$remote")
                serverClientSockets.remove(conn)
                val id = socketToId.remove(conn)
                if (id != null) {
                    idToSocket.remove(id)
                    peers.remove(id)
                    listener.onPeerDisconnected(id)
                    // Inform remaining clients
                    send("BYE|$id")
                }
                if (serverClientSockets.isEmpty()) {
                    connected.set(false)
                    listener.onConnectionChanged(false)
                }
            }
            override fun onMessage(conn: WebSocket, message: String) {
                handleMessageServer(message, conn)
            }
            override fun onError(conn: WebSocket?, ex: Exception) {
                Log.w(TAG, "Server error: ${ex.message}")
            }
        }.apply {
            // A recent session can leave accepted sockets in TIME_WAIT. Allow
            // the same local listening port to be rebound when hosting again.
            setReuseAddr(true)
            // Enable keep-alive pings and connection watchdog to prevent idle closes through NATs
            // Sends a ping every N seconds and detects unresponsive peers
            setConnectionLostTimeout(30)
        }
        server?.start()
    }

    private fun startClient() {
        val uri = URI.create("ws://$hostIp:$port")
        client = object : WebSocketClient(uri) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                Log.i(TAG, "Client connected to $uri")
                connected.set(true)
                reconnecting.set(false)
                listener.onConnectionChanged(true)
                send("HELLO|client")
            }
            override fun onMessage(message: String) {
                handleMessage(message)
            }
            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                Log.i(TAG, "Client closed: code=$code reason=$reason remote=$remote")
                connected.set(false)
                listener.onConnectionChanged(false)
                // Auto-reconnect with backoff
                if (shouldKeepTrying) scheduleReconnect()
            }
            override fun onError(ex: Exception) {
                Log.w(TAG, "Client error: ${ex.message}")
                if (!connected.get() && shouldKeepTrying) scheduleReconnect()
            }
        }.apply {
            // Enable keep-alive pings and connection watchdog to prevent idle closes through NATs
            // Sends a ping every N seconds and detects unresponsive peers
            connectionLostTimeout = 30
        }
        client?.connect()
    }

    @Synchronized
    private fun scheduleReconnect() {
        if (isHost) return
        if (reconnecting.get()) return
        reconnecting.set(true)
        Thread({
            var attempt = 0
            while (!connected.get() && shouldKeepTrying && !isHost) {
                val base = 1000L
                val max = 15000L
                val delay = kotlin.math.min(max, base * (1 shl kotlin.math.min(attempt, 4)))
                try {
                    Thread.sleep(delay)
                } catch (_: InterruptedException) {}
                if (connected.get() || !shouldKeepTrying) break
                try {
                    // Try to reconnect by creating a new client
                    startClient()
                } catch (e: Exception) {
                    Log.w(TAG, "Reconnect attempt failed: ${e.message}")
                }
                attempt++
            }
            reconnecting.set(false)
        }, "WS-Reconnect").start()
    }

    // Host-side handler (messages from a specific client connection)
    private fun handleMessageServer(message: String, conn: WebSocket) {
        val parts = message.split('|')
        if (parts.isEmpty()) return
        val id = socketToId[conn] ?: return
        when (parts[0]) {
            "ROUND" -> listener.onRoundEvent(id, message.substringAfter('|'))
            "INPUT" -> if (parts.size >= 5) {
                val l = parts[1] == "1"
                val r = parts[2] == "1"
                val j = parts[3] == "1"
                val h = parts[4] == "1"
                val p = if (parts.size >= 6) parts[5] == "1" else false
                listener.onPeerInput(id, l, r, j, h, p)
            }
            "POS" -> {
                // client -> host: POS|col|row|f
                if (parts.size >= 3) {
                    val col = parts[1].toIntOrNull() ?: return
                    val row = parts[2].toIntOrNull() ?: return
                    val fr = if (parts.size >= 4) parts[3] == "1" else true
                    val ps = peers[id] ?: PeerState().also { peers[id] = it }
                    ps.col = col; ps.row = row; ps.facingRight = fr
                    ps.latitude = parts.getOrNull(4)?.toIntOrNull()
                    ps.longitude = parts.getOrNull(5)?.toIntOrNull()
                    // Broadcast to all clients with the sender id
                    send("POS|$id|$col|$row|${b(fr)}|${ps.latitude}|${ps.longitude}")
                    listener.onPeerPos(id, col, row, fr, ps.latitude, ps.longitude)
                }
            }
            "CHAR" -> if (parts.size >= 2) {
                val name = parts[1]
                val ps = peers[id] ?: PeerState().also { peers[id] = it }
                ps.name = name
                send("CHAR|$id|$name")
                listener.onPeerSelectedPlayer(id, name)
            }
            // World state is host-owned. Clients send validated ACTION commands instead.
            "TILE", "NPC", "ARROW", "POTION" -> Unit
        }
    }

    // Client-side handler (messages from host)
    private fun handleMessage(message: String) {
        val parts = message.split('|')
        if (parts.isEmpty()) return
        when (parts[0]) {
            "ROUND" -> {
                val payload = message.substringAfter('|')
                if (payload.startsWith("STATE;")) lastRoundSnapshot = payload
                listener.onRoundEvent(0, payload)
            }
            "WELCOME" -> if (parts.size >= 2) {
                selfId = parts[1].toIntOrNull() ?: selfId
            }
            "BYE" -> if (parts.size >= 2) {
                val id = parts[1].toIntOrNull() ?: return
                clientPeerCache.remove(id)
                listener.onPeerDisconnected(id)
            }
            "POS" -> {
                // host->clients: POS|id|col|row|f
                if (parts.size >= 5) {
                    val id = parts[1].toIntOrNull() ?: return
                    val col = parts[2].toIntOrNull() ?: return
                    val row = parts[3].toIntOrNull() ?: return
                    val fr = parts[4] == "1"
                    // Cache for late listeners on client side
                    val ps = clientPeerCache[id] ?: PeerState().also { clientPeerCache[id] = it }
                    ps.col = col; ps.row = row; ps.facingRight = fr
                    ps.latitude = parts.getOrNull(5)?.toIntOrNull()
                    ps.longitude = parts.getOrNull(6)?.toIntOrNull()
                    listener.onPeerPos(id, col, row, fr, ps.latitude, ps.longitude)
                } else if (parts.size >= 3) {
                    // backward compatibility (no id)
                    val col = parts[1].toIntOrNull() ?: return
                    val row = parts[2].toIntOrNull() ?: return
                    val fr = if (parts.size >= 4) parts[3] == "1" else true
                    val ps = clientPeerCache[0] ?: PeerState().also { clientPeerCache[0] = it }
                    ps.col = col; ps.row = row; ps.facingRight = fr
                    ps.latitude = parts.getOrNull(4)?.toIntOrNull()
                    ps.longitude = parts.getOrNull(5)?.toIntOrNull()
                    listener.onPeerPos(0, col, row, fr, ps.latitude, ps.longitude)
                }
            }
            "TILE" -> if (parts.size >= 4) {
                val action = parts[1]
                when (action) {
                    "destroy", "place" -> {
                        val x = parts.getOrNull(2)?.toIntOrNull() ?: return
                        val y = parts.getOrNull(3)?.toIntOrNull() ?: return
                        if (action == "destroy") listener.onTileDestroyed(x, y) else listener.onTilePlaced(x, y)
                    }
                    "hp" -> {
                        val x = parts.getOrNull(2)?.toIntOrNull() ?: return
                        val y = parts.getOrNull(3)?.toIntOrNull() ?: return
                        val hp = parts.getOrNull(4)?.toIntOrNull() ?: return
                        listener.onTileHealth(x, y, hp)
                    }
                }
            }
            "CHAR" -> {
                // host->clients: CHAR|id|name (or legacy CHAR|name)
                if (parts.size >= 3) {
                    val id = parts[1].toIntOrNull() ?: return
                    val name = parts[2]
                    // Cache for late listeners on client side
                    val ps = clientPeerCache[id] ?: PeerState().also { clientPeerCache[id] = it }
                    ps.name = name
                    listener.onPeerSelectedPlayer(id, name)
                } else if (parts.size >= 2) {
                    val name = parts[1]
                    val ps = clientPeerCache[0] ?: PeerState().also { clientPeerCache[0] = it }
                    ps.name = name
                    listener.onPeerSelectedPlayer(0, name)
                }
            }
            "NPC" -> handleNpcMessage(parts, rebroadcast = false)
            "ARROW" -> handleArrowMessage(parts, rebroadcast = false)
            "POTION" -> handlePotionMessage(parts, rebroadcast = false)
        }
    }

    private fun handleNpcMessage(parts: List<String>, rebroadcast: Boolean) {
        if (parts.size < 4) return
        val kind = parts[1]
        val id = parts[2].toLongOrNull() ?: return
        if (parts[3] == "gone") {
            if (rebroadcast) send("NPC|$kind|$id|gone")
            listener.onNpcGone(kind, id)
            return
        }
        if (parts.size < 7) return
        val xMilliTiles = parts[3].toIntOrNull() ?: return
        val yMilliTiles = parts[4].toIntOrNull() ?: return
        val facingRight = parts[5] == "1"
        val hp = parts[6].toIntOrNull() ?: return
        val stateOrdinal = parts.getOrNull(7)?.toIntOrNull() ?: 0
        val pulseMilli = parts.getOrNull(8)?.toIntOrNull() ?: 0
        if (rebroadcast) send("NPC|$kind|$id|$xMilliTiles|$yMilliTiles|${b(facingRight)}|$hp|$stateOrdinal|$pulseMilli")
        listener.onNpcState(kind, id, xMilliTiles, yMilliTiles, facingRight, hp, stateOrdinal, pulseMilli)
    }

    private fun handleArrowMessage(parts: List<String>, rebroadcast: Boolean) {
        if (parts.size < 3) return
        val id = parts[1].toLongOrNull() ?: return
        if (parts[2] == "gone") {
            if (rebroadcast) send("ARROW|$id|gone")
            listener.onArrowGone(id)
            return
        }
        if (parts.size < 4) return
        val xMilliTiles = parts[2].toIntOrNull() ?: return
        val yMilliTiles = parts[3].toIntOrNull() ?: return
        if (rebroadcast) send("ARROW|$id|$xMilliTiles|$yMilliTiles")
        listener.onArrowState(id, xMilliTiles, yMilliTiles)
    }

    private fun handlePotionMessage(parts: List<String>, rebroadcast: Boolean) {
        if (parts.size < 3) return
        val id = parts[1].toLongOrNull() ?: return
        if (parts[2] == "gone") {
            if (rebroadcast) send("POTION|$id|gone")
            listener.onPotionGone(id)
            return
        }
        if (parts.size < 4) return
        val col = parts[2].toIntOrNull() ?: return
        val row = parts[3].toIntOrNull() ?: return
        val type = ItemType.entries.firstOrNull { it.name == parts.getOrNull(4) } ?: ItemType.Potion
        if (rebroadcast) send("POTION|$id|$col|$row|${type.name}")
        listener.onPotionState(id, col, row, type)
    }

    private fun b(v: Boolean) = if (v) "1" else "0"
    actual fun getSelfId(): Int = selfId

    actual companion object {
        actual val TAG: String = "Multiplayer"
        actual val DEFAULT_PORT: Int = 8082
        // Default host IP used by client side when none is provided
        actual val DEFAULT_HOST_IP: String = "127.0.0.1"
    }
}
