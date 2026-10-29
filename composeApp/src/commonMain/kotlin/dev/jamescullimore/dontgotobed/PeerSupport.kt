package dev.jamescullimore.dontgotobed

import kotlin.math.abs
import kotlin.math.floor

/** Uses the same column overlap and peer top as the landing collision. */
internal fun standingPeerTop(
    leftPx: Float,
    bottomPx: Float,
    widthPx: Float,
    heightPx: Float,
    unitPx: Float,
    peers: Iterable<RemotePeer>,
    onPlayerPlane: (RemotePeer) -> Boolean
): Float? {
    val firstCol = floor(leftPx / unitPx).toInt()
    val lastCol = floor((leftPx + widthPx - 0.001f) / unitPx).toInt()
    return peers.firstNotNullOfOrNull { peer ->
        if (!onPlayerPlane(peer)) return@firstNotNullOfOrNull null
        val peerFirstCol = floor(peer.worldXPx / unitPx).toInt()
        val peerLastCol = floor((peer.worldXPx + widthPx - 0.001f) / unitPx).toInt()
        val top = peer.heightPx + heightPx
        top.takeIf {
            firstCol <= peerLastCol && lastCol >= peerFirstCol && abs(top - bottomPx) < 0.5f
        }
    }
}
