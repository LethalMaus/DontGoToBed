package dev.jamescullimore.dontgotobed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerInventoryTest {
    @Test fun materialsStaySeparateAndMatchingStacksMerge() {
        val bag = PlayerInventory()
        for (material in BlockMaterial.entries) assertTrue(bag.add(ItemType.Block, 1, material))
        assertTrue(bag.add(ItemType.Potion))
        assertTrue(bag.add(ItemType.Arrow))
        val before = bag.slots[1]
        assertTrue(bag.add(ItemType.Block, 3, BlockMaterial.Wood))
        assertEquals(1, before.count) // Previously published values are never mutated.
        assertEquals(listOf(1, 4, 1, 1, 1, 0, 0), bag.slots.map { it.count })
        assertEquals(BlockMaterial.entries, bag.slots.take(3).map { it.material })
        assertTrue(bag.slots.take(3).map { it.material!!.color }.toSet().size == 3)
        assertNull(bag.slots[3].material)
    }

    @Test fun stackOverflowAndFullBagCannotConvertMaterials() {
        val bag = PlayerInventory(capacity = 3)
        assertTrue(bag.add(ItemType.Block, 21, BlockMaterial.Wood))
        assertEquals(listOf(20, 1, 0), bag.slots.map { it.count })
        assertTrue(bag.add(ItemType.Block, 20, BlockMaterial.Grass))
        val before = bag.slots.toList()
        assertFalse(bag.add(ItemType.Block, 1, BlockMaterial.Stone))
        assertEquals(before, bag.slots)
        assertTrue(bag.add(ItemType.Block, 2, BlockMaterial.Wood))
        assertEquals(3, bag.slots[1].count)
        assertTrue(bag.consume(1, 3))
        assertEquals(InventorySlot(), bag.slots[1])
        assertTrue(bag.add(ItemType.Arrow))
        assertNull(bag.slots[1].material)
        assertFalse(bag.consume(1, 2))
        assertEquals(1, bag.slots[1].count)
    }

    @Test fun harvestedMaterialSurvivesDestructionAndPlacement() {
        for (material in BlockMaterial.entries) for (shape in BlockShape.entries) {
            val map = TileMap(24, 12)
            val bag = PlayerInventory()
            map.setActiveSlice(WorldAxis.Latitude, 0, 7)
            map.placePiece(4, 3, material, shape)
            val first = assertNotNull(map.damagePieceAt(4, 3, 1))
            assertFalse(first.destroyed)
            assertEquals(material.durability - 1, first.remainingHealth)
            val broken = assertNotNull(map.damagePieceAt(4, 3, 100))
            assertTrue(broken.destroyed)
            assertEquals(material, broken.material)
            assertNull(map.damagePieceAt(4, 3, 100))
            assertTrue(bag.add(ItemType.Block, 1, broken.material))
            assertTrue(bag.placeBlock(0, map, 10, 3, shape) >= 0)
            assertEquals(InventorySlot(), bag.slots[0])
            assertEquals(material.durability, map.getHealth(10, 3))
            val placed = map.snapshotBlocks().first { it.col == 10 && it.row == 3 }
            assertEquals(material, placed.material)
            assertEquals(material.color, placed.color)
            map.setActiveSlice(WorldAxis.Longitude, 10, 7)
            assertEquals(material, map.damagePieceAt(7, 3, 100)?.material)
        }
    }

    @Test fun placementConsumesOnlySelectedStackAndOnlyOnSuccess() {
        val map = TileMap(24, 12)
        val bag = PlayerInventory()
        bag.add(ItemType.Block, 12, BlockMaterial.Grass)
        bag.add(ItemType.Block, 2, BlockMaterial.Stone)
        bag.add(ItemType.Potion)
        assertTrue(bag.placeBlock(1, map, 4, 3, BlockShape.Square) >= 0)
        assertEquals(12, bag.slots[0].count)
        assertEquals(1, bag.slots[1].count)
        assertEquals(-1, bag.placeBlock(1, map, 4, 3, BlockShape.Square))
        assertEquals(-1, bag.placeBlock(1, map, 4, 12, BlockShape.Single))
        assertEquals(-1, bag.placeBlock(2, map, 10, 3, BlockShape.Single))
        assertEquals(1, bag.slots[1].count)
        assertEquals(1, bag.slots[2].count)
    }

    @Test fun rampEmptyHalfStillAwardsItsMaterial() {
        val map = TileMap(24, 12)
        map.placePiece(23, 3, BlockMaterial.Wood, BlockShape.RampLeft)
        assertFalse(map.get(23, 5))
        val result = assertNotNull(map.damagePieceAt(23, 5, 100))
        assertTrue(result.destroyed)
        assertEquals(BlockMaterial.Wood, result.material)
        assertFalse(map.hasPieceAt(0, 4))
    }
}
