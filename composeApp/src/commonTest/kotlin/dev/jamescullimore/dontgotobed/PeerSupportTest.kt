package dev.jamescullimore.dontgotobed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PeerSupportTest {
    private val peer = RemotePeer(worldXPx = 100f, heightPx = 60f, hasPos = true)

    private fun support(left: Float = 100f, bottom: Float = 120f,
                        peers: List<RemotePeer> = listOf(peer), samePlane: Boolean = true) =
        standingPeerTop(left, bottom, 40f, 60f, 20f, peers) { it.hasPos && samePlane }

    @Test fun landingOnPeerRemainsSupportedOnSubsequentFrames() {
        var bottom = peer.heightPx + 60f
        repeat(120) {
            val top = support(bottom = bottom)
            assertEquals(120f, top)
            // A standing character must not enter passive fall after landing.
            bottom = top!!
        }
    }

    @Test fun movingOutFromUnderStandingPlayerRemovesSupport() {
        assertEquals(120f, support())
        peer.worldXPx = 140f
        assertNull(support())
        peer.worldXPx = 60f
        assertNull(support())
    }

    @Test fun walkingOffPeerRemovesSupport() {
        assertEquals(120f, support(left = 139f))
        assertNull(support(left = 140f))
        assertNull(support(left = 60f))
    }

    @Test fun disconnectDifferentPlaneAndUnknownPositionAreNotSupport() {
        assertNull(support(peers = emptyList()))
        assertNull(support(samePlane = false))
        peer.hasPos = false
        assertNull(support())
    }

    @Test fun onlyFeetAtPeerTopAreSupported() {
        assertNull(support(bottom = 60f))
        assertNull(support(bottom = 140f))
        assertEquals(120f, support(bottom = 120.2f))
    }
}
