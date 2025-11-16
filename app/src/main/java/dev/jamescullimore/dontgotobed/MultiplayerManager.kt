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

    // Cache last-known peer state so late listeners (e.g., when switching screens) can immediately reflect it
    @Volatile private var lastPeerSelectedName: String? = null
    @Volatile private var lastPeerCol: Int? = null
    @Volatile private var lastPeerRow: Int? = null
    @Volatile private var lastPeerFacingRight: Boolean? = null

    fun updateListener(newListener: Listener) {
        this.listener = newListener
        // Immediately inform the new listener about current connection state so UI can reflect it
        try {
            newListener.onConnectionChanged(connected.get())
        } catch (_: Exception) {}
        // Replay cached peer identity and position if available
        try {
            lastPeerSelectedName?.let { newListener.onPeerSelectedPlayer(it) }
        } catch (_: Exception) {}
        try {
            val col = lastPeerCol
            val row = lastPeerRow
            val fr = lastPeerFacingRight
            if (col != null && row != null && fr != null) {
                newListener.onPeerPos(col, row, fr)
            }
        } catch (_: Exception) {}
    }
    interface Listener {
        fun onPeerInput(left: Boolean, right: Boolean, jump: Boolean, hit: Boolean, place: Boolean) {}
        fun onPeerPos(col: Int, row: Int, facingRight: Boolean) {}
        fun onTileDestroyed(x: Int, y: Int) {}
        fun onTilePlaced(x: Int, y: Int) {}
        fun onTileHealth(x: Int, y: Int, health: Int) {}
        fun onPeerSelectedPlayer(name: String) {}
        fun onConnectionChanged(connected: Boolean) {}
    }

    private var server: WebSocketServer? = null
    private var serverClientSocket: WebSocket? = null
    private var client: WebSocketClient? = null

    private val connected = AtomicBoolean(false)

    fun start() {
        if (isHost) startServer() else startClient()
    }

    fun stop() {
        try {
            serverClientSocket?.close(CloseFrame.NORMAL)
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

    // Client -> Host messages
    fun sendInput(left: Boolean, right: Boolean, jump: Boolean, hit: Boolean, place: Boolean = false) {
        val msg = "INPUT|${b(left)}|${b(right)}|${b(jump)}|${b(hit)}|${b(place)}"
        send(msg)
    }

    fun sendPos(col: Int, row: Int, facingRight: Boolean) {
        val msg = "POS|$col|$row|${b(facingRight)}"
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
        val msg = "CHAR|$name"
        send(msg)
    }

    private fun send(msg: String) {
        try {
            if (isHost) {
                if (serverClientSocket == null) {
                    Log.w(TAG, "send skipped (host): no client socket. msg=\"$msg\"")
                } else {
                    Log.d(TAG, "send (host->client): \"$msg\"")
                    serverClientSocket?.send(msg)
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
                serverClientSocket = conn
                connected.set(true)
                listener.onConnectionChanged(true)
                Log.d(TAG, "send (server greeting): \"HELLO|host\"")
                conn.send("HELLO|host")
            }
            override fun onClose(conn: WebSocket, code: Int, reason: String?, remote: Boolean) {
                Log.i(TAG, "Client disconnected: code=$code reason=$reason remote=$remote")
                if (serverClientSocket == conn) serverClientSocket = null
                connected.set(false)
                listener.onConnectionChanged(false)
            }
            override fun onMessage(conn: WebSocket, message: String) {
                Log.d(TAG, "recv (server) from ${conn.remoteSocketAddress}: \"$message\"")
                handleMessage(message)
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
            }
            override fun onError(ex: Exception) {
                Log.w(TAG, "Client error: ${ex.message}")
            }
        }.apply {
            // Enable keep-alive pings and connection watchdog to prevent idle closes through NATs
            // Sends a ping every N seconds and detects unresponsive peers
            connectionLostTimeout = 30
        }
        client?.connect()
    }

    private fun handleMessage(message: String) {
        // Very small parser for our pipe-delimited protocol
        Log.d(TAG, "handleMessage: raw=\"$message\"")
        val parts = message.split('|')
        if (parts.isEmpty()) {
            Log.w(TAG, "handleMessage: empty parts for message")
            return
        }
        when (parts[0]) {
            "INPUT" -> if (parts.size >= 5) {
                val l = parts[1] == "1"
                val r = parts[2] == "1"
                val j = parts[3] == "1"
                val h = parts[4] == "1"
                val p = if (parts.size >= 6) parts[5] == "1" else false
                Log.d(TAG, "parsed INPUT l=$l r=$r j=$j h=$h p=$p")
                listener.onPeerInput(l, r, j, h, p)
            } else {
                Log.w(TAG, "malformed INPUT: ${parts.joinToString("|")}")
            }
            "POS" -> if (parts.size >= 3) {
                val col = parts[1].toIntOrNull()
                val row = parts[2].toIntOrNull()
                val fr = if (parts.size >= 4) parts[3] == "1" else true
                if (col == null || row == null) {
                    Log.w(TAG, "malformed POS: ${parts.joinToString("|")}")
                    return
                }
                Log.d(TAG, "parsed POS col=$col row=$row fr=$fr")
                // Cache last-known peer position/facing for late listeners
                lastPeerCol = col
                lastPeerRow = row
                lastPeerFacingRight = fr
                listener.onPeerPos(col, row, fr)
            } else {
                Log.w(TAG, "malformed POS: ${parts.joinToString("|")}")
            }
            "TILE" -> if (parts.size >= 4) {
                val action = parts[1]
                when (action) {
                    "destroy", "place" -> {
                        val x = parts.getOrNull(2)?.toIntOrNull()
                        val y = parts.getOrNull(3)?.toIntOrNull()
                        if (x == null || y == null) {
                            Log.w(TAG, "malformed TILE: ${parts.joinToString("|")}")
                            return
                        }
                        when (action) {
                            "destroy" -> {
                                Log.d(TAG, "parsed TILE destroy x=$x y=$y")
                                listener.onTileDestroyed(x, y)
                            }
                            "place" -> {
                                Log.d(TAG, "parsed TILE place x=$x y=$y")
                                listener.onTilePlaced(x, y)
                            }
                        }
                    }
                    "hp" -> {
                        val x = parts.getOrNull(2)?.toIntOrNull()
                        val y = parts.getOrNull(3)?.toIntOrNull()
                        val hp = parts.getOrNull(4)?.toIntOrNull()
                        if (x == null || y == null || hp == null) {
                            Log.w(TAG, "malformed TILE hp: ${parts.joinToString("|")}")
                            return
                        }
                        Log.d(TAG, "parsed TILE hp x=$x y=$y hp=$hp")
                        listener.onTileHealth(x, y, hp)
                    }
                    else -> Log.w(TAG, "unknown TILE action '$action': ${parts.joinToString("|")}")
                }
            } else {
                Log.w(TAG, "unknown or malformed TILE: ${parts.joinToString("|")}")
            }
            "CHAR" -> if (parts.size >= 2) {
                val name = parts[1]
                Log.d(TAG, "parsed CHAR name=$name")
                // Cache last-known peer character for late listeners
                lastPeerSelectedName = name
                listener.onPeerSelectedPlayer(name)
            } else {
                Log.w(TAG, "malformed CHAR: ${parts.joinToString("|")}")
            }
            else -> {
                Log.w(TAG, "unknown message type: ${parts[0]} raw=\"${message}\"")
            }
        }
    }

    private fun b(v: Boolean) = if (v) "1" else "0"

    companion object {
        const val TAG = "Multiplayer"
        const val DEFAULT_PORT = 8082
        // Default host IP used by client side when none is provided
        const val DEFAULT_HOST_IP = "127.0.0.1"
    }
}
