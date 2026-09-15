package dev.jamescullimore.dontgotobed

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.websocket.ClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.URLProtocol
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import platform.Foundation.NSRecursiveLock
import platform.posix.AF_INET
import platform.posix.INADDR_ANY
import platform.posix.SOCK_STREAM
import platform.posix.accept
import platform.posix.bind
import platform.posix.close
import platform.posix.listen
import platform.posix.read
import platform.posix.recv
import platform.posix.sockaddr
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.write
import kotlin.math.min
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.value
import platform.posix.setsockopt
import platform.posix.SOL_SOCKET
import platform.posix.SO_NOSIGPIPE
import platform.posix.SO_REUSEADDR
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned

actual class MultiplayerManager actual constructor(
    private val isHost: Boolean,
    private val hostIp: String,
    private val port: Int,
    initialListener: Listener
) {
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

    data class PeerState(
        var name: String? = null,
        var col: Int? = null,
        var latitude: Int? = null,
        var longitude: Int? = null,
        var row: Int? = null,
        var facingRight: Boolean? = null
    )

    private data class HostClient(
        val id: Int,
        val fd: Int,
        var alive: Boolean = true
    )

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val wsClient = HttpClient(Darwin) {
        install(WebSockets)
    }

    private var connectJob: Job? = null
    private var clientSession: ClientWebSocketSession? = null

    private var serverJob: Job? = null
    private var serverFd: Int = -1
    private var nextPeerId: Int = 1
    private val hostClients = mutableMapOf<Int, HostClient>()
    private val socketWriteLock = NSRecursiveLock()
    private val clientsLock = NSRecursiveLock()
    private inline fun <T> withClients(block: () -> T): T {
        clientsLock.lock()
        return try { block() } finally { clientsLock.unlock() }
    }
    private val outgoing = Channel<Pair<List<HostClient>?, String>>(Channel.UNLIMITED)
    private val writer = scope.launch {
        for ((clients, message) in outgoing) {
            if (clients != null) clients.forEach { client ->
                if (client.alive && !sendWsText(client.fd, message)) client.alive = false
            } else runCatching { clientSession?.send(Frame.Text(message)) }
        }
    }
    private val peers = mutableMapOf<Int, PeerState>()

    private var listener: Listener = initialListener
    private var connected: Boolean = false
    private var shouldKeepTrying: Boolean = true
    private var selfId: Int = if (isHost) 0 else -1

    private var lastSentPosCol: Int? = null
    private var lastSentPosRow: Int? = null
    private var lastSentPosFacingRight: Boolean? = null

    private val clientPeerCache = mutableMapOf<Int, PeerState>()

    private var lastSelfName: String? = null
    private var lastSelfCol: Int? = null
    private var lastSelfRow: Int? = null
    private var lastSelfFacingRight: Boolean? = null

    actual fun updateListener(newListener: Listener) {
        listener = newListener
        if (!isHost) lastRoundSnapshot?.let { newListener.onRoundEvent(0, it) }
        newListener.onConnectionChanged(connected)

        if (isHost) {
            withClients { peers.toMap() }.forEach { (id, ps) ->
                ps.name?.let { newListener.onPeerSelectedPlayer(id, it) }
                val c = ps.col
                val r = ps.row
                val fr = ps.facingRight
                if (c != null && r != null && fr != null) newListener.onPeerPos(id, c, r, fr, ps.latitude, ps.longitude)
            }
            val c = lastSelfCol
            val r = lastSelfRow
            val fr = lastSelfFacingRight
            lastSelfName?.let { newListener.onPeerSelectedPlayer(0, it) }
            if (c != null && r != null && fr != null) newListener.onPeerPos(0, c, r, fr, lastLatitude, lastLongitude)
        } else {
            withClients { clientPeerCache.toMap() }.forEach { (id, ps) ->
                ps.name?.let { newListener.onPeerSelectedPlayer(id, it) }
                val c = ps.col
                val r = ps.row
                val fr = ps.facingRight
                if (c != null && r != null && fr != null) newListener.onPeerPos(id, c, r, fr, ps.latitude, ps.longitude)
            }
        }
    }

    actual fun start() {
        shouldKeepTrying = true
        if (isHost) startHostServer() else startClientLoop()
    }

    actual fun stop() {
        shouldKeepTrying = false

        connectJob?.cancel()
        connectJob = null

        serverJob?.cancel()
        serverJob = null

        scope.launch {
            clientSession = null
            withClients { hostClients.values.toList() }.forEach { c -> c.alive = false; runCatching { close(c.fd) } }
            withClients { hostClients.clear() }
            withClients { peers.clear() }
            if (serverFd >= 0) {
                runCatching { close(serverFd) }
                serverFd = -1
            }
            if (connected) {
                connected = false
                listener.onConnectionChanged(false)
            }
        }
    }

    private var lastRoundSnapshot: String? = null
    actual fun sendRoundEvent(payload: String) {
        if (isHost && payload.startsWith("STATE;")) lastRoundSnapshot = payload
        send("ROUND|$payload")
    }

    actual fun sendInput(left: Boolean, right: Boolean, jump: Boolean, hit: Boolean, place: Boolean) {
        send("INPUT|${b(left)}|${b(right)}|${b(jump)}|${b(hit)}|${b(place)}")
    }

    private var lastLatitude: Int? = null
    private var lastLongitude: Int? = null

    actual fun sendPos(col: Int, row: Int, facingRight: Boolean, latitude: Int, longitude: Int) {
        val changed = (lastLatitude != latitude) || (lastLongitude != longitude) || (lastSentPosCol != col) || (lastSentPosRow != row) || (lastSentPosFacingRight != facingRight)

        if (isHost) {
            lastSelfCol = col
            lastSelfRow = row
            lastSelfFacingRight = facingRight
        }

        lastLatitude = latitude
        lastLongitude = longitude
        lastSentPosCol = col
        lastSentPosRow = row
        lastSentPosFacingRight = facingRight
        if (!changed) return

        val msg = if (isHost) "POS|0|$col|$row|${b(facingRight)}|$latitude|$longitude" else "POS|$col|$row|${b(facingRight)}|$latitude|$longitude"
        send(msg)
    }

    actual fun sendTileDestroyed(x: Int, y: Int) {
        send("TILE|destroy|$x|$y")
    }

    actual fun sendTilePlaced(x: Int, y: Int) {
        send("TILE|place|$x|$y")
    }

    actual fun sendTileHealth(x: Int, y: Int, health: Int) {
        send("TILE|hp|$x|$y|$health")
    }

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

    actual fun getSelfId(): Int = selfId

    private fun startClientLoop() {
        if (connectJob?.isActive == true) return
        connectJob = scope.launch {
            var attempt = 0
            while (isActive && shouldKeepTrying) {
                try {
                    runClientSession()
                    attempt = 0
                } catch (_: Exception) {
                    connected = false
                    listener.onConnectionChanged(false)
                }

                if (!shouldKeepTrying) break
                val delayMs = minOf(15000L, 1000L shl minOf(attempt, 4))
                delay(delayMs)
                attempt++
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun startHostServer() {
        if (serverJob?.isActive == true) return
        serverJob = scope.launch {
            val fd = socket(AF_INET, SOCK_STREAM, 0)
            if (fd < 0) {
                connected = false
                listener.onConnectionChanged(false)
                return@launch
            }
            serverFd = fd
            memScoped {
                val enabled = alloc<IntVar>().apply { value = 1 }
                setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, enabled.ptr, sizeOf<IntVar>().convert())
            }

            val bindOk = memScoped {
                val addr = alloc<sockaddr_in>()
                addr.sin_family = AF_INET.convert()
                addr.sin_port = toNetworkPort(port)
                addr.sin_addr.s_addr = INADDR_ANY.convert()
                bind(fd, addr.ptr.reinterpret<sockaddr>(), sizeOf<sockaddr_in>().convert()) == 0
            }

            if (!bindOk || listen(fd, 8) != 0) {
                runCatching { close(fd) }
                serverFd = -1
                connected = false
                listener.onConnectionChanged(false)
                return@launch
            }

            connected = true
            listener.onConnectionChanged(true)

            while (isActive && shouldKeepTrying) {
                val clientFd = accept(fd, null, null)
                if (clientFd < 0) continue

                scope.launch {
                    handleAcceptedClient(clientFd)
                }
            }
        }
    }

    private suspend fun runClientSession() {
        val ws = wsClient.webSocketSession {
            url {
                protocol = URLProtocol.WS
                host = hostIp
                port = this@MultiplayerManager.port
            }
        }

        clientSession = ws
        connected = true
        listener.onConnectionChanged(true)
        ws.send(Frame.Text("HELLO|client"))

        try {
            for (frame in ws.incoming) {
                if (frame is Frame.Text) {
                    handleMessageClient(message = frame.readText())
                }
            }
        } finally {
            clientSession = null
            if (connected) {
                connected = false
                listener.onConnectionChanged(false)
            }
        }
    }

    private fun send(msg: String) {
        if (!connected) return
        // One ordered writer; socket I/O must never block the game's main thread.
        outgoing.trySend((if (isHost) withClients { hostClients.values.toList() } else null) to msg)
    }

    private fun handleMessageClient(message: String) {
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
                withClients { clientPeerCache.remove(id) }
                listener.onPeerDisconnected(id)
            }

            "POS" -> {
                if (parts.size >= 5) {
                    val id = parts[1].toIntOrNull() ?: return
                    val col = parts[2].toIntOrNull() ?: return
                    val row = parts[3].toIntOrNull() ?: return
                    val fr = parts[4] == "1"
                    val ps = withClients { clientPeerCache.getOrPut(id) { PeerState() } }
                    ps.col = col
                    ps.row = row
                    ps.facingRight = fr
                    ps.latitude = parts.getOrNull(5)?.toIntOrNull()
                    ps.longitude = parts.getOrNull(6)?.toIntOrNull()
                    listener.onPeerPos(id, col, row, fr, ps.latitude, ps.longitude)
                } else if (parts.size >= 3) {
                    val col = parts[1].toIntOrNull() ?: return
                    val row = parts[2].toIntOrNull() ?: return
                    val fr = if (parts.size >= 4) parts[3] == "1" else true
                    val ps = withClients { clientPeerCache.getOrPut(0) { PeerState() } }
                    ps.col = col
                    ps.row = row
                    ps.facingRight = fr
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
                if (parts.size >= 3) {
                    val id = parts[1].toIntOrNull() ?: return
                    val name = parts[2]
                    val ps = withClients { clientPeerCache.getOrPut(id) { PeerState() } }
                    ps.name = name
                    listener.onPeerSelectedPlayer(id, name)
                } else if (parts.size >= 2) {
                    val name = parts[1]
                    val ps = withClients { clientPeerCache.getOrPut(0) { PeerState() } }
                    ps.name = name
                    listener.onPeerSelectedPlayer(0, name)
                }
            }

            "NPC" -> handleNpcMessage(parts, rebroadcast = false)
            "ARROW" -> handleArrowMessage(parts, rebroadcast = false)
            "POTION" -> handlePotionMessage(parts, rebroadcast = false)
        }
    }

    private fun handleMessageHost(message: String, peerId: Int) {
        val parts = message.split('|')
        if (parts.isEmpty()) return
        when (parts[0]) {
            "ROUND" -> listener.onRoundEvent(peerId, message.substringAfter('|'))
            "INPUT" -> if (parts.size >= 5) {
                val l = parts[1] == "1"
                val r = parts[2] == "1"
                val j = parts[3] == "1"
                val h = parts[4] == "1"
                val p = if (parts.size >= 6) parts[5] == "1" else false
                listener.onPeerInput(peerId, l, r, j, h, p)
            }

            "POS" -> {
                if (parts.size >= 3) {
                    val col = parts[1].toIntOrNull() ?: return
                    val row = parts[2].toIntOrNull() ?: return
                    val fr = if (parts.size >= 4) parts[3] == "1" else true
                    val ps = withClients { peers.getOrPut(peerId) { PeerState() } }
                    ps.col = col
                    ps.row = row
                    ps.facingRight = fr
                    ps.latitude = parts.getOrNull(4)?.toIntOrNull()
                    ps.longitude = parts.getOrNull(5)?.toIntOrNull()
                    send("POS|$peerId|$col|$row|${b(fr)}|${ps.latitude}|${ps.longitude}")
                    listener.onPeerPos(peerId, col, row, fr, ps.latitude, ps.longitude)
                }
            }

            "CHAR" -> if (parts.size >= 2) {
                val name = parts[1]
                val ps = withClients { peers.getOrPut(peerId) { PeerState() } }
                ps.name = name
                send("CHAR|$peerId|$name")
                listener.onPeerSelectedPlayer(peerId, name)
            }

            // World state is host-owned. Clients send validated ACTION commands instead.
            "TILE", "NPC", "ARROW", "POTION" -> Unit
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

    private fun handleAcceptedClient(fd: Int) {
        memScoped {
            val enabled = alloc<IntVar>().apply { value = 1 }
            setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, enabled.ptr, sizeOf<IntVar>().convert())
        }
        if (!performHandshake(fd)) {
            runCatching { close(fd) }
            return
        }

        val id = withClients { nextPeerId++ }
        val client = HostClient(id = id, fd = fd)
        withClients { peers[id] = PeerState() }
        sendWsText(fd, "WELCOME|$id")
        withClients { hostClients[id] = client }
        listener.onPeerConnected(id)
        lastRoundSnapshot?.let { sendWsText(fd, "ROUND|$it") }

        lastSelfName?.let { sendWsText(fd, "CHAR|0|$it") }
        val c = lastSelfCol
        val r = lastSelfRow
        val fr = lastSelfFacingRight
        if (c != null && r != null && fr != null) sendWsText(fd, "POS|0|$c|$r|${b(fr)}|$lastLatitude|$lastLongitude")

        withClients { peers.toMap() }.forEach { (pid, ps) ->
            if (pid == id) return@forEach
            ps.name?.let { sendWsText(fd, "CHAR|$pid|$it") }
            val pc = ps.col
            val pr = ps.row
            val pfr = ps.facingRight
            if (pc != null && pr != null && pfr != null) sendWsText(fd, "POS|$pid|$pc|$pr|${b(pfr)}|${ps.latitude}|${ps.longitude}")
        }

        while (client.alive && shouldKeepTrying) {
            val msg = readWsText(fd) ?: break
            handleMessageHost(msg, id)
        }

        client.alive = false
        withClients { hostClients.remove(id) }
        withClients { peers.remove(id) }
        runCatching { close(fd) }

        listener.onPeerDisconnected(id)
        send("BYE|$id")

        if (withClients { hostClients.isEmpty() } && connected) {
            listener.onConnectionChanged(true)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun performHandshake(fd: Int): Boolean {
        val reqBytes = readHttpRequest(fd) ?: return false
        val req = reqBytes.decodeToString()
        val keyLine = req.lineSequence().firstOrNull { it.startsWith("Sec-WebSocket-Key:", ignoreCase = true) }
            ?: return false
        val key = keyLine.substringAfter(':').trim()
        if (key.isEmpty()) return false

        val accept = websocketAccept(key)
        val response = WebSocketServerWire.handshake(accept)

        return writeAll(fd, response.encodeToByteArray())
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun readHttpRequest(fd: Int): ByteArray? {
        val out = ArrayList<Byte>(1024)
        val buf = ByteArray(512)

        repeat(32) {
            val n = buf.usePinned { pinned ->
                read(fd, pinned.addressOf(0), buf.size.convert()).toInt()
            }
            if (n <= 0) return null
            for (i in 0 until n) out.add(buf[i])
            if (out.size >= 4) {
                val s = out.size
                if (
                    out[s - 4] == '\r'.code.toByte() &&
                    out[s - 3] == '\n'.code.toByte() &&
                    out[s - 2] == '\r'.code.toByte() &&
                    out[s - 1] == '\n'.code.toByte()
                ) {
                    return out.toByteArray()
                }
            }
        }
        return null
    }

    private fun readWsText(fd: Int): String? = WebSocketServerWire.readText(
        read = { length -> ByteArray(length).takeIf { readExactly(fd, it, length) } },
        write = { bytes -> writeFrame(fd, bytes) }
    )

    private fun sendWsText(fd: Int, message: String): Boolean =
        writeFrame(fd, WebSocketServerWire.frame(message.encodeToByteArray()))

    private fun writeFrame(fd: Int, bytes: ByteArray): Boolean {
        socketWriteLock.lock()
        return try { writeAll(fd, bytes) } finally { socketWriteLock.unlock() }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun readExactly(fd: Int, dst: ByteArray, len: Int): Boolean {
        var off = 0
        while (off < len) {
            val n = dst.usePinned { pinned ->
                recv(fd, pinned.addressOf(off), (len - off).convert(), 0).toInt()
            }
            if (n <= 0) return false
            off += n
        }
        return true
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun writeAll(fd: Int, bytes: ByteArray): Boolean {
        var off = 0
        while (off < bytes.size) {
            val n = bytes.usePinned { pinned ->
                write(fd, pinned.addressOf(off), (bytes.size - off).convert()).toInt()
            }
            if (n <= 0) return false
            off += n
        }
        return true
    }

    private fun websocketAccept(clientKey: String): String {
        val digest = sha1((clientKey + WS_GUID).encodeToByteArray())
        return base64(digest)
    }

    private fun b(v: Boolean) = if (v) "1" else "0"
    private fun toNetworkPort(value: Int): UShort = (((value and 0xFF) shl 8) or ((value ushr 8) and 0xFF)).toUShort()

    private fun sha1(input: ByteArray): ByteArray {
        fun leftRotate(x: Int, bits: Int): Int = (x shl bits) or (x ushr (32 - bits))

        val ml = input.size.toLong() * 8L
        val withOne = input + byteArrayOf(0x80.toByte())
        val padLen = ((56 - (withOne.size % 64)) + 64) % 64
        val padded = ByteArray(withOne.size + padLen + 8)
        withOne.copyInto(padded, 0, 0, withOne.size)
        for (i in 0 until 8) {
            padded[padded.size - 1 - i] = ((ml ushr (8 * i)) and 0xFF).toByte()
        }

        var h0 = 0x67452301
        var h1 = 0xEFCDAB89.toInt()
        var h2 = 0x98BADCFE.toInt()
        var h3 = 0x10325476
        var h4 = 0xC3D2E1F0.toInt()

        val w = IntArray(80)
        var chunk = 0
        while (chunk < padded.size) {
            for (i in 0 until 16) {
                val j = chunk + i * 4
                w[i] =
                    ((padded[j].toInt() and 0xFF) shl 24) or
                    ((padded[j + 1].toInt() and 0xFF) shl 16) or
                    ((padded[j + 2].toInt() and 0xFF) shl 8) or
                    (padded[j + 3].toInt() and 0xFF)
            }
            for (i in 16 until 80) w[i] = leftRotate(w[i - 3] xor w[i - 8] xor w[i - 14] xor w[i - 16], 1)

            var a = h0
            var b = h1
            var c = h2
            var d = h3
            var e = h4

            for (i in 0 until 80) {
                val (f, k) = when (i) {
                    in 0..19 -> ((b and c) or (b.inv() and d)) to 0x5A827999
                    in 20..39 -> (b xor c xor d) to 0x6ED9EBA1
                    in 40..59 -> ((b and c) or (b and d) or (c and d)) to 0x8F1BBCDC.toInt()
                    else -> (b xor c xor d) to 0xCA62C1D6.toInt()
                }
                val temp = leftRotate(a, 5) + f + e + k + w[i]
                e = d
                d = c
                c = leftRotate(b, 30)
                b = a
                a = temp
            }

            h0 += a
            h1 += b
            h2 += c
            h3 += d
            h4 += e
            chunk += 64
        }

        val out = ByteArray(20)
        val hs = intArrayOf(h0, h1, h2, h3, h4)
        for (i in hs.indices) {
            val v = hs[i]
            out[i * 4] = ((v ushr 24) and 0xFF).toByte()
            out[i * 4 + 1] = ((v ushr 16) and 0xFF).toByte()
            out[i * 4 + 2] = ((v ushr 8) and 0xFF).toByte()
            out[i * 4 + 3] = (v and 0xFF).toByte()
        }
        return out
    }

    private fun base64(bytes: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val out = StringBuilder(((bytes.size + 2) / 3) * 4)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0

            val n = (b0 shl 16) or (b1 shl 8) or b2
            out.append(alphabet[(n ushr 18) and 0x3F])
            out.append(alphabet[(n ushr 12) and 0x3F])
            out.append(if (i + 1 < bytes.size) alphabet[(n ushr 6) and 0x3F] else '=')
            out.append(if (i + 2 < bytes.size) alphabet[n and 0x3F] else '=')
            i += 3
        }
        return out.toString()
    }

    actual companion object {
        actual val TAG: String = "Multiplayer"
        actual val DEFAULT_PORT: Int = 8082
        actual val DEFAULT_HOST_IP: String = "127.0.0.1"
        private const val WS_GUID: String = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    }
}
