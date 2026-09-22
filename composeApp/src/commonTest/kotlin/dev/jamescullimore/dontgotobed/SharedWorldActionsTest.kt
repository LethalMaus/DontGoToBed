package dev.jamescullimore.dontgotobed

import androidx.compose.ui.unit.dp
import kotlin.test.*

class SharedWorldActionsTest {
    private val position = WorldLocation(7, 10, 4)
    private fun authority(map: TileMap, vm: GameViewModel) = SharedWorldAuthority(map, vm, 30f, 60f, 90f,
        WorldAxis.Latitude, { position }, { listOf(position) })
    private fun action(id: Long, verb: String, vararg args: String) = WorldAction(id, verb, WorldAxis.Longitude,
        7000, 10000, 4000, args.toList())

    @Test fun remoteHitDamagesTheEnemyInTheClientsPlaneAndReconcilesBothScreens() {
        val map = TileMap(24, 18)
        val host = GameViewModel()
        host.upsertZombieSnapshot(10, 7000, 4000, true, 3, 0, 0, 30f, { it.dp }, 12000)
        host.upsertZombieSnapshot(11, 12000, 4000, true, 3, 0, 0, 30f, { it.dp }, 7000)
        val result = authority(map, host).apply(1, action(100, "HIT", "12", "5", Direction.Right.ordinal.toString()))
        assertEquals("true;_;_", result)
        assertEquals(2, host.zombies.value.first { it.id == 10L }.hp)
        assertEquals(3, host.zombies.value.first { it.id == 11L }.hp)
        val client = GameViewModel()
        client.applyWorldEntities(host.worldEntities(WorldAxis.Latitude, 30f), WorldAxis.Longitude, 15f, { it.dp })
        assertEquals(2, client.zombies.value.first { it.id == 10L }.hp)
    }

    @Test fun remoteMiningAwardsTheRequesterAndNeverChangesHostsInventory() {
        val map = TileMap(24, 18)
        val host = GameViewModel()
        map.setActiveSlice(WorldAxis.Longitude, 7, 10)
        map.placePiece(12, 5, BlockMaterial.Grass, BlockShape.Single)
        map.setActiveSlice(WorldAxis.Latitude, 0, 2)
        val before = host.inventory.toList()
        val authority = authority(map, host)
        repeat(2) { assertEquals("true;_;_", authority.apply(1, action(it.toLong(), "HIT", "12", "5", Direction.Right.ordinal.toString()))) }
        assertEquals("true;Block;Grass", authority.apply(1, action(3, "HIT", "12", "5", Direction.Right.ordinal.toString())))
        assertContentEquals(before, host.inventory)
        assertFalse(map.getVoxel(7, 12, 5))
        assertEquals(WorldAxis.Latitude, map.activeAxis)
        assertEquals(2, map.activeSlice)
    }

    @Test fun placementPreservesMaterialShapeAndPlaneAndRejectsPlayerOverlap() {
        val map = TileMap(24, 18)
        val host = GameViewModel()
        val authority = authority(map, host)
        assertEquals("true;_;_", authority.apply(1, action(1, "PLACE", "12", "4", "2", BlockShape.RampLeft.ordinal.toString())))
        val piece = map.networkBlocks().single()
        assertEquals(BlockMaterial.Stone, piece.material)
        assertEquals(BlockShape.RampLeft, piece.shape)
        assertEquals(WorldAxis.Longitude, piece.axis)
        assertEquals(7, piece.fixedCoordinate)
        assertEquals("false;_;_", authority.apply(1, action(2, "PLACE", "10", "4", "1", "0")))
    }

    @Test fun competingPickupsHaveOnlyOneWinnerAndRetriesDoNotDuplicateRewards() {
        val map = TileMap(24, 18)
        val host = GameViewModel()
        host.upsertPotionSnapshot(50, 7, 4, ItemType.Potion, 10)
        val authority = authority(map, host)
        val receipts = ActionReceipts()
        val take = action(1, "TAKE", "50")
        repeat(4) { assertEquals("true;Potion;_", receipts.resolve(take.id) { authority.apply(1, take) }) }
        assertEquals("false;_;_", authority.apply(2, action(2, "TAKE", "50")))
        assertTrue(host.potions.value.isEmpty())
    }

    @Test fun projectilesKeepMovingWhenNoSkeletonExists() {
        val map = TileMap(24, 18)
        val host = GameViewModel()
        authority(map, host).apply(1, action(1, "FIRE", "18000", "6100"))
        assertTrue(host.skeletons.value.isEmpty())
        val before = host.arrows.value.single().copy()
        host.advanceProjectiles(map, 30f, 60f, 90f, 0.016f)
        val after = host.arrows.value.single()
        assertTrue(after.otherAxisPx > before.otherAxisPx)
        assertTrue(after.vY < before.vY)
        host.paused = true
        host.advanceProjectiles(map, 30f, 60f, 90f, 0.016f)
        assertEquals(after, host.arrows.value.single())
    }

    @Test fun remoteArrowTravelsOnClientsAxisWithoutRotatingHostWorld() {
        val map = TileMap(24, 18)
        val host = GameViewModel()
        assertEquals("true;_;_", authority(map, host).apply(1, action(1, "FIRE", "18000", "6100")))
        val arrow = host.arrows.value.single()
        assertEquals(7 * 30f, arrow.worldXPx)
        assertEquals(11 * 30f, arrow.otherAxisPx)
        assertEquals(0f, arrow.vX)
        assertTrue(arrow.otherVelocity > 0)
        assertEquals(WorldAxis.Latitude, map.activeAxis)
    }
}
