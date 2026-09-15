package dev.jamescullimore.dontgotobed

expect class MultiplayerManager(
    isHost: Boolean,
    hostIp: String,
    port: Int = DEFAULT_PORT,
    initialListener: Listener
) {
    interface Listener {
        fun onRoundEvent(senderId: Int, payload: String)
        fun onPeerInput(id: Int, left: Boolean, right: Boolean, jump: Boolean, hit: Boolean, place: Boolean)
        fun onPeerPos(id: Int, col: Int, row: Int, facingRight: Boolean, latitude: Int?, longitude: Int?)
        fun onTileDestroyed(x: Int, y: Int)
        fun onTilePlaced(x: Int, y: Int)
        fun onTileHealth(x: Int, y: Int, health: Int)
        fun onPeerSelectedPlayer(id: Int, name: String)
        fun onPeerConnected(id: Int)
        fun onPeerDisconnected(id: Int)
        fun onConnectionChanged(connected: Boolean)
        fun onNpcState(kind: String, id: Long, xMilliTiles: Int, yMilliTiles: Int, facingRight: Boolean, hp: Int, stateOrdinal: Int, pulseMilli: Int)
        fun onNpcGone(kind: String, id: Long)
        fun onArrowState(id: Long, xMilliTiles: Int, yMilliTiles: Int)
        fun onArrowGone(id: Long)
        fun onPotionState(id: Long, col: Int, row: Int, type: ItemType)
        fun onPotionGone(id: Long)
    }

    fun updateListener(newListener: Listener)
    fun start()
    fun stop()
    fun sendRoundEvent(payload: String)
    fun sendInput(left: Boolean, right: Boolean, jump: Boolean, hit: Boolean, place: Boolean = false)
    fun sendPos(col: Int, row: Int, facingRight: Boolean, latitude: Int, longitude: Int)
    fun sendTileDestroyed(x: Int, y: Int)
    fun sendTilePlaced(x: Int, y: Int)
    fun sendTileHealth(x: Int, y: Int, health: Int)
    fun sendSelectedPlayer(name: String)
    fun sendNpcState(kind: String, id: Long, xMilliTiles: Int, yMilliTiles: Int, facingRight: Boolean, hp: Int, stateOrdinal: Int, pulseMilli: Int)
    fun sendNpcGone(kind: String, id: Long)
    fun sendArrowState(id: Long, xMilliTiles: Int, yMilliTiles: Int)
    fun sendArrowGone(id: Long)
    fun sendPotionState(id: Long, col: Int, row: Int, type: ItemType)
    fun sendPotionGone(id: Long)
    fun getSelfId(): Int

    companion object {
        val TAG: String
        val DEFAULT_PORT: Int
        val DEFAULT_HOST_IP: String
    }
}
