package dev.jamescullimore.dontgotobed

import androidx.compose.ui.graphics.Color
import kotlin.test.*
import kotlin.random.Random

class HuntBedTest {
    @Test fun bedHasTwoSolidLayersAndClearSpaceFromBothAxes() {
        val map = TileMap(24, 12).also { it.seedGroundBand() }
        val bed = HuntBed(1, 23, 23, 3)
        val voxels = HuntBedVoxels(map)
        voxels.set(bed)
        for (x in 0..2) for (z in 0..2) {
            assertTrue(map.getVoxel(23 + x, 23 + z, 3))
            assertTrue(map.getVoxel(23 + x, 23 + z, 4))
            assertFalse(map.getVoxel(23 + x, 23 + z, 5))
        }
        for (axis in WorldAxis.entries) {
            map.setActiveSlice(axis, 0, 0)
            assertEquals(Color.White, map.snapshotBlocks().first { it.row == 4 }.color)
            assertEquals(BlockMaterial.Wood.color, map.snapshotBlocks().first { it.row == 3 }.color)
        }
        voxels.set(null)
        assertFalse(map.getVoxel(0, 0, 3))
        assertTrue(map.getVoxel(0, 0, 2))
    }

    @Test fun topContactIsLethalButSidesAndUndersideAreSafe() {
        val map = TileMap(24, 12)
        val bed = HuntBed(1, 23, 23, 3)
        for (axis in WorldAxis.entries) {
            assertTrue(bed.onTop(map, 0f, 0f, 5f, axis))
            assertFalse(bed.onTop(map, 0f, 0f, 3f, axis))
            assertFalse(bed.onTop(map, 3f, 3f, 5f, axis))
        }
    }

    @Test fun placementPreservesExistingWorldAndAvoidsCharacters() {
        val map = TileMap(24, 12).also { it.seedGroundBand() }
        val forbidden = WorldLocation(10, 10, 3)
        val bed = assertNotNull(HuntBedPlacement.find(map, listOf(forbidden), Random(1)))
        assertFalse(bed.contains(map, 10, 10, bed.row))
        assertFalse(map.getVoxel(bed.latitude, bed.longitude, bed.row))
        assertNull(HuntBedPlacement.find(TileMap(24, 12), emptyList(), Random(1)))
    }

    @Test fun sevenSlotsAndExactOverflowPreserveAllRewards() {
        val bag = PlayerInventory()
        assertEquals(7, bag.slots.size)
        bag.add(ItemType.Block, 120, BlockMaterial.Grass)
        assertEquals(PlayerInventory.Added(1, 0), bag.addExact(ItemType.Map))
        assertEquals(PlayerInventory.Added(0, 5), bag.addExact(ItemType.Potion, 5))
        bag.removeAll(ItemType.Map)
        assertEquals(PlayerInventory.Added(20, 5), bag.addExact(ItemType.Potion, 25))
    }
}
