package dev.jamescullimore.dontgotobed

open class MultiplayerListenerAdapter : MultiplayerManager.Listener {
    override fun onRoundEvent(senderId: Int, payload: String) = Unit
    override fun onPeerInput(id: Int, left: Boolean, right: Boolean, jump: Boolean, hit: Boolean, place: Boolean) = Unit
    override fun onPeerPos(id: Int, col: Int, row: Int, facingRight: Boolean, latitude: Int?, longitude: Int?) = Unit
    override fun onTileDestroyed(x: Int, y: Int) = Unit
    override fun onTilePlaced(x: Int, y: Int) = Unit
    override fun onTileHealth(x: Int, y: Int, health: Int) = Unit
    override fun onPeerSelectedPlayer(id: Int, name: String) = Unit
    override fun onPeerConnected(id: Int) = Unit
    override fun onPeerDisconnected(id: Int) = Unit
    override fun onConnectionChanged(connected: Boolean) = Unit
    override fun onNpcState(kind: String, id: Long, xMilliTiles: Int, yMilliTiles: Int, facingRight: Boolean, hp: Int, stateOrdinal: Int, pulseMilli: Int) = Unit
    override fun onNpcGone(kind: String, id: Long) = Unit
    override fun onArrowState(id: Long, xMilliTiles: Int, yMilliTiles: Int) = Unit
    override fun onArrowGone(id: Long) = Unit
    override fun onPotionState(id: Long, col: Int, row: Int, type: ItemType) = Unit
    override fun onPotionGone(id: Long) = Unit
}
