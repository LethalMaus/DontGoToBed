package dev.jamescullimore.dontgotobed

import androidx.compose.ui.unit.dp
import kotlin.test.*

class WorldReplicationTest {
    @Test fun fullBaselineIncludesBothPlanesAndRemovesClientOnlyTerrain() {
        val host = TileMap(24, 18)
        val client = TileMap(24, 18)
        client.placePiece(2, 8, BlockMaterial.Grass, BlockShape.Square)
        for (axis in WorldAxis.entries) {
            host.setActiveSlice(axis, 11, 7)
            host.placePiece(23, 4, BlockMaterial.Stone, BlockShape.RampLeft)
            host.placePiece(5, 10, BlockMaterial.Wood, BlockShape.Platform)
            host.damagePieceAt(5, 10, 10)
        }
        client.setActiveSlice(WorldAxis.Longitude, 9, 2)
        transfer(host, client, baseline = true)
        assertEquals(WorldAxis.Longitude, client.activeAxis)
        assertEquals(9, client.activeSlice)
        assertWorldEqual(host, client)
        assertFalse(client.getVoxel(2, 0, 8))
    }

    @Test fun editsAndProjectileDamageReachEveryClientAndLateJoiner() {
        val host = TileMap(24, 18)
        val a = TileMap(24, 18)
        val b = TileMap(24, 18)
        host.seedGroundBand()
        val ramp = host.placePiece(20, 4, BlockMaterial.Stone, BlockShape.RampRight)
        transfer(host, a, true); transfer(host, b, true)
        host.takeNetworkChanges()
        val view = host.collisionView().apply { setActiveSlice(WorldAxis.Longitude, 8, 4) }
        view.placePiece(12, 5, BlockMaterial.Wood, BlockShape.Pillar)
        host.removeBlock(ramp)
        host.damageVoxel(0, 0, 1, 10)
        val delta = WorldReplication.encodeBlocks(host.takeNetworkChanges())
        listOf(a, b).forEach { it.applyNetworkBlocks(WorldReplication.decodeBlocks(delta, it)); assertWorldEqual(host, it) }
        val late = TileMap(24, 18)
        transfer(host, late, true)
        assertWorldEqual(host, late)
    }

    @Test fun collisionViewsAllocateUniqueIdsWithoutReplacingExistingBlocks() {
        val map = TileMap(24, 18)
        val original = map.placePiece(1, 5, BlockMaterial.Wood, BlockShape.Single)
        val view = map.collisionView()
        val second = view.placePiece(3, 5, BlockMaterial.Stone, BlockShape.Single)
        val third = map.placePiece(5, 5, BlockMaterial.Grass, BlockShape.Single)
        assertEquals(3, setOf(original, second, third).size)
        assertEquals(3, map.networkBlocks().size)
    }

    @Test fun enemiesProjectAcrossOppositeAxesAndDifferentDisplayDensities() {
        val host = GameViewModel()
        host.upsertZombieSnapshot(1, 12250, 4000, true, 3, NpcState.Chase.ordinal, 0, 30f, { it.dp }, 8750)
        host.upsertSkeletonSnapshot(2, 15000, 6000, false, 2, NpcState.Attacking.ordinal, 450, 30f, { it.dp }, 9250)
        host.upsertArrowSnapshot(3, 14250, 7000, 30f, 9500)
        host.upsertPotionSnapshot(4, 12, 3, ItemType.Arrow, 8)
        val wire = WorldReplication.encodeEntities(host.worldEntities(WorldAxis.Latitude, 30f))
        val client = GameViewModel()
        client.applyWorldEntities(WorldReplication.decodeEntities(wire), WorldAxis.Longitude, 45f, { it.dp })
        assertEquals(8.75f * 45, client.zombies.value.single().worldXPx)
        assertEquals(12.25f * 45, client.zombies.value.single().otherAxisPx)
        assertEquals(host.worldEntities(WorldAxis.Latitude, 30f), client.worldEntities(WorldAxis.Longitude, 45f))
        client.swapNpcAxes { it.dp }
        client.applyWorldEntities(WorldReplication.decodeEntities(wire), WorldAxis.Latitude, 45f, { it.dp })
        assertEquals(12.25f * 45, client.zombies.value.single().targetWorldXPx)
        client.applyWorldEntities(emptyList(), WorldAxis.Latitude, 45f, { it.dp })
        assertTrue(client.zombies.value.isEmpty() && client.skeletons.value.isEmpty() && client.arrows.value.isEmpty() && client.potions.value.isEmpty())
    }

    @Test fun snapshotsAreAtomicChunkedAndRejectDuplicatesAndMissingChunks() {
        val sender = WorldPacketStream(123)
        val receiver = WorldPacketStream()
        val body = "terrain".repeat(8000)
        val packets = sender.packets("BASE", body)
        assertTrue(packets.size > 1)
        assertTrue(packets.all { it.encodeToByteArray().size < 16_000 })
        packets.dropLast(1).forEach { assertNull(receiver.receive(it)) }
        assertEquals("BASE" to body, receiver.receive(packets.last()))
        packets.forEach { assertNull(receiver.receive(it)) }
        val broken = sender.packets("TERRAIN", body)
        receiver.receive(broken.first())
        assertFails { receiver.receive(broken.last()) }
        val recovery = sender.packets("BASE", "recovered")
        assertEquals("BASE" to "recovered", receiver.receive(recovery.single()))
    }

    @Test fun actionReceiptsApplyPickupAndRewardOnlyOnceAcrossRetries() {
        val receipts = ActionReceipts()
        var drops = 1
        var rewards = 0
        repeat(5) {
            assertEquals("accepted", receipts.resolve(123) { drops--; rewards++; "accepted" })
        }
        assertEquals(0, drops)
        assertEquals(1, rewards)
    }

    @Test fun commandsRetainAimPlaneAndValidateWrappedReach() {
        val action = WorldAction(1, "HIT", WorldAxis.Longitude, 7000, 23500, 3000, listOf("0", "4", "7"))
        assertEquals(action, WorldAction.decode(action.encode()))
        val map = TileMap(24, 18)
        assertTrue(action.nearKnownPosition(WorldLocation(7, 0, 3), map))
        assertTrue(action.nearTarget(0, 4, map))
        assertFalse(action.nearTarget(12, 4, map))
        assertFalse(action.nearKnownPosition(WorldLocation(16, 12, 3), map))
    }

    private fun transfer(host: TileMap, client: TileMap, baseline: Boolean) {
        val sender = WorldPacketStream(42)
        val receiver = WorldPacketStream()
        val encoded = WorldReplication.encodeBlocks(host.networkBlocks().map { it.id to it })
        sender.packets("BASE", encoded).forEach { packet ->
            receiver.receive(packet)?.let { client.applyNetworkBlocks(WorldReplication.decodeBlocks(it.second, client), baseline) }
        }
    }
    private fun assertWorldEqual(a: TileMap, b: TileMap) {
        assertEquals(a.networkBlocks().sortedBy { it.id }, b.networkBlocks().sortedBy { it.id })
        for (lat in 0 until a.width) for (lon in 0 until a.depth) for (y in 0 until a.height)
            assertEquals(a.getVoxel(lat, lon, y), b.getVoxel(lat, lon, y), "$lat,$lon,$y")
    }
}
