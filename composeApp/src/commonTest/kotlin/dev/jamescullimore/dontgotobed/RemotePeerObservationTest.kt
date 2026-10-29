package dev.jamescullimore.dontgotobed

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

class RemotePeerObservationTest {
    @Test fun positionArrivingAfterCharacterAnnouncementInvalidatesVisibility() {
        val peers = mutableStateMapOf(0 to RemotePeer(selected = Player.Ian))
        val observer = SnapshotStateObserver { it() }
        var invalidated = false
        observer.start()
        try {
            observer.observeReads(Unit, { invalidated = true }) {
                // Rendering skips a character until its first position arrives.
                peers.values.filter { it.hasPos && it.longitude == 10 }
            }
            Snapshot.withMutableSnapshot {
                val peer = peers.getValue(0)
                peer.latitude = 0
                peer.longitude = 10
                peer.hasPos = true
            }
            assertTrue(invalidated, "A late position must redraw the existing peer without local input")
        } finally {
            observer.stop()
            observer.clear()
        }
    }

    @Test fun interpolatedMovementInvalidatesAStationaryPlayersView() {
        val peer = RemotePeer(hasPos = true)
        val observer = SnapshotStateObserver { it() }
        var invalidated = false
        observer.start()
        try {
            observer.observeReads(Unit, { invalidated = true }) {
                peer.worldXDp to peer.heightPx
            }
            Snapshot.withMutableSnapshot {
                peer.worldXDp = 15.dp
                peer.heightPx = 30f
            }
            assertTrue(invalidated, "Remote movement must redraw even when the local character is idle")
        } finally {
            observer.stop()
            observer.clear()
        }
    }
}
