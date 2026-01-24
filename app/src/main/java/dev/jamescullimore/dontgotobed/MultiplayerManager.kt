package dev.jamescullimore.dontgotobed

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
class MultiplayerManager(
    private val isHost: Boolean,
    private val hostIp: String,
    private val port: Int = DEFAULT_PORT,
    initialListener: Listener
) {
    // Listener can be updated by UI layers without restarting the connection
    @Volatile
    private var listener: Listener = initialListener

    // Multi-peer support
    data class PeerState(
        var name: String? = null,
        var col: Int? = null,
        var row: Int? = null,
        var facingRight: Boolean? = null
    )

    // Server side: track many clients
    private val socketToId = mutableMapOf<WebSocket, Int>()
    private val idToSocket = mutableMapOf<Int, WebSocket>()
    private val peers = mutableMapOf<Int, PeerState>()
    private var nextPeerId = 1 // Host uses id 0

    // Client side: cache the last known state of all peers as told by host (for late UI listeners)
    private val clientPeerCache = mutableMapOf<Int, PeerState>()

    // Cache host (self) state for late-join synchronization when hosting
    @Volatile private var selfId: Int = if (isHost) 0 else -1
    @Volatile private var lastSelfName: String? = null
    @Volatile private var lastSelfCol: Int? = null
    @Volatile private var lastSelfRow: Int? = null
    @Volatile private var lastSelfFacingRight: Boolean? = null

    fun updateListener(newListener: Listener) {
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
                if (c != null && r != null && fr != null) newListener.onPeerPos(id, c, r, fr)
            }
            // Host self state (id 0) to newly attached listeners
            if (isHost) {
                val c = lastSelfCol; val r = lastSelfRow; val fr = lastSelfFacingRight
                lastSelfName?.let { newListener.onPeerSelectedPlayer(0, it) }
                if (c != null && r != null && fr != null) newListener.onPeerPos(0, c, r, fr)
            }
            // Client-side cache (messages received from host about any peer, including host id 0)
            clientPeerCache.forEach { (id, ps) ->
                ps.name?.let { newListener.onPeerSelectedPlayer(id, it) }
                val c = ps.col; val r = ps.row; val fr = ps.facingRight
                if (c != null && r != null && fr != null) newListener.onPeerPos(id, c, r, fr)
            }
        } catch (_: Exception) {}
    }
    interface Listener {
        fun onPeerInput(id: Int, left: Boolean, right: Boolean, jump: Boolean, hit: Boolean, place: Boolean) {}
        fun onPeerPos(id: Int, col: Int, row: Int, facingRight: Boolean) {}
        fun onTileDestroyed(x: Int, y: Int) {}
        fun onTilePlaced(x: Int, y: Int) {}
        fun onTileHealth(x: Int, y: Int, health: Int) {}
        fun onPeerSelectedPlayer(id: Int, name: String) {}
        fun onPeerConnected(id: Int) {}
        fun onPeerDisconnected(id: Int) {}
        fun onConnectionChanged(connected: Boolean) {}
    }

    private var server: WebSocketServer? = null
    private val serverClientSockets = mutableSetOf<WebSocket>()
    private var client: WebSocketClient? = null

    private val connected = AtomicBoolean(false)
    private val reconnecting = AtomicBoolean(false)
    @Volatile private var shouldKeepTrying = true

    fun start() {
        if (isHost) startServer() else startClient()
    }

    fun stop() {
        shouldKeepTrying = false
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
    fun sendInput(left: Boolean, right: Boolean, jump: Boolean, hit: Boolean, place: Boolean = false) {
        val msg = "INPUT|${b(left)}|${b(right)}|${b(jump)}|${b(hit)}|${b(place)}"
        send(msg)
    }

    // Internal de-duplication for POS messages to avoid unnecessary network traffic
    @Volatile private var lastSentPosCol: Int? = null
    @Volatile private var lastSentPosRow: Int? = null
    @Volatile private var lastSentPosFacingRight: Boolean? = null

    fun sendPos(col: Int, row: Int, facingRight: Boolean) {
        val changed = (lastSentPosCol != col) || (lastSentPosRow != row) || (lastSentPosFacingRight != facingRight)
        // Always update caches so late-join replay stays accurate
        if (isHost) {
            lastSelfCol = col; lastSelfRow = row; lastSelfFacingRight = facingRight
        }
        lastSentPosCol = col
        lastSentPosRow = row
        lastSentPosFacingRight = facingRight
        if (!changed) {
            // Skip sending duplicate POS with no effective change (same grid and facing)
            return
        }
        val msg = if (isHost) "POS|0|$col|$row|${b(facingRight)}" else "POS|$col|$row|${b(facingRight)}"
        send(msg)
    }

    // Host -> Client messages
    fun sendTileDestroyed(x: Int, y: Int) {
        if (!isHost) return
        val msg = "TILE|destroy|$x|$y"
        send(msg)
    }

    fun sendTilePlaced(x: Int, y: Int) {
        if (!isHost) return
        val msg = "TILE|place|$x|$y"
        send(msg)
    }

    fun sendTileHealth(x: Int, y: Int, health: Int) {
        if (!isHost) return
        val msg = "TILE|hp|$x|$y|$health"
        send(msg)
    }

    // Both directions: notify selected character
    fun sendSelectedPlayer(name: String) {
        if (isHost) {
            lastSelfName = name
            send("CHAR|0|$name")
        } else {
            send("CHAR|$name")
        }
    }

    private fun send(msg: String) {
        try {
            if (isHost) {
                if (serverClientSockets.isEmpty()) {
                    Log.w(TAG, "send skipped (host): no clients. msg=\"$msg\"")
                } else {
                    Log.d(TAG, "broadcast (host->clients): \"$msg\"")
                    serverClientSockets.forEach { s -> runCatching { s.send(msg) } }
                }
            } else {
                if (client == null) {
                    Log.w(TAG, "send skipped (client): not connected. msg=\"$msg\"")
                } else {
                    Log.d(TAG, "send (client->host): \"$msg\"")
                    client?.send(msg)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "send failed: ${e.message}. msg=\"$msg\"")
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
                // Replay known states (including host as id 0) to the newcomer
                if (lastSelfName != null) conn.send("CHAR|0|${lastSelfName}")
                if (lastSelfCol != null && lastSelfRow != null && lastSelfFacingRight != null) {
                    conn.send("POS|0|${lastSelfCol}|${lastSelfRow}|${b(lastSelfFacingRight!!)}")
                }
                peers.forEach { (pid, ps) ->
                    if (pid != id) {
                        ps.name?.let { conn.send("CHAR|$pid|$it") }
                        val c = ps.col; val r = ps.row; val fr = ps.facingRight
                        if (c != null && r != null && fr != null) conn.send("POS|$pid|$c|$r|${b(fr)}")
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
                Log.d(TAG, "recv (server) from ${conn.remoteSocketAddress}: \"$message\"")
                handleMessageServer(message, conn)
            }
            override fun onError(conn: WebSocket?, ex: Exception) {
                Log.w(TAG, "Server error: ${ex.message}")
            }
        }.apply {
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
                Log.d(TAG, "recv (client): \"$message\"")
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
                    // Broadcast to all clients with the sender id
                    send("POS|$id|$col|$row|${b(fr)}")
                    listener.onPeerPos(id, col, row, fr)
                }
            }
            "CHAR" -> if (parts.size >= 2) {
                val name = parts[1]
                val ps = peers[id] ?: PeerState().also { peers[id] = it }
                ps.name = name
                send("CHAR|$id|$name")
                listener.onPeerSelectedPlayer(id, name)
            }
        }
    }

    // Client-side handler (messages from host)
    private fun handleMessage(message: String) {
        Log.d(TAG, "handleMessage: raw=\"$message\"")
        val parts = message.split('|')
        if (parts.isEmpty()) return
        when (parts[0]) {
            "WELCOME" -> if (parts.size >= 2) {
                selfId = parts[1].toIntOrNull() ?: selfId
            }
            "BYE" -> if (parts.size >= 2) {
                val id = parts[1].toIntOrNull() ?: return
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
                    listener.onPeerPos(id, col, row, fr)
                } else if (parts.size >= 3) {
                    // backward compatibility (no id)
                    val col = parts[1].toIntOrNull() ?: return
                    val row = parts[2].toIntOrNull() ?: return
                    val fr = if (parts.size >= 4) parts[3] == "1" else true
                    val ps = clientPeerCache[0] ?: PeerState().also { clientPeerCache[0] = it }
                    ps.col = col; ps.row = row; ps.facingRight = fr
                    listener.onPeerPos(0, col, row, fr)
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
        }
    }

    private fun b(v: Boolean) = if (v) "1" else "0"

    fun getSelfId(): Int = selfId

    companion object {
        const val TAG = "Multiplayer"
        const val DEFAULT_PORT = 8082
        // Default host IP used by client side when none is provided
        const val DEFAULT_HOST_IP = "127.0.0.1"
    }
}
