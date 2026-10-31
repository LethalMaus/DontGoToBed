package dev.jamescullimore.dontgotobed

import kotlin.test.*

class CraftingTest {
    @Test fun combinesSplitStacksAndPreservesUnrelatedItems() {
        val bag=PlayerInventory(7,2)
        bag.add(ItemType.Block,4,BlockMaterial.Wood);bag.add(ItemType.Block,1,BlockMaterial.Stone);bag.add(ItemType.Map)
        assertEquals(CraftResult.Crafted,Crafting.make(bag,"shield"))
        assertEquals(0,Crafting.count(bag,BlockMaterial.Wood));assertEquals(0,Crafting.count(bag,BlockMaterial.Stone))
        assertEquals(1,bag.slots.sumOf { if(it.type==ItemType.Shield) it.count else 0 })
        assertEquals(1,bag.slots.sumOf { if(it.type==ItemType.Map) it.count else 0 })
    }
    @Test fun rejectsMissingMaterialsUnknownRecipesAndPartialOutputWithoutLoss() {
        val bag=PlayerInventory(3)
        bag.add(ItemType.Block,20,BlockMaterial.Wood);bag.add(ItemType.Block,20,BlockMaterial.Stone);bag.add(ItemType.Arrow,18)
        val before=bag.slots.toList()
        assertEquals(CraftResult.BagFull,Crafting.make(bag,"arrows"));assertContentEquals(before,bag.slots)
        assertEquals(CraftResult.MissingMaterials,Crafting.make(bag,"rope"));assertContentEquals(before,bag.slots)
        assertEquals(CraftResult.UnknownRecipe,Crafting.make(bag,"anything"));assertContentEquals(before,bag.slots)
    }
    @Test fun ingredientsCanFreeTheOutputSlotAndSecondCraftNeedsNewMaterials() {
        val bag=PlayerInventory(1);bag.add(ItemType.Block,3,BlockMaterial.Grass)
        assertEquals(CraftResult.Crafted,Crafting.make(bag,"rope"))
        assertEquals(InventorySlot(ItemType.Rope,1),bag.slots.single())
        assertEquals(CraftResult.MissingMaterials,Crafting.make(bag,"rope"))
        assertEquals(InventorySlot(ItemType.Rope,1),bag.slots.single())
    }
    @Test fun benchAccessIsLocalAndHostReceiptsDoNotSpendHostsBag() {
        val map=TileMap(576,12,exploration=true)
        val bench=Crafting.bench(ExplorationBiome.Meadow)
        map.setActiveSlice(WorldAxis.Latitude,bench.latitude,bench.longitude)
        map.placeBlock(bench.latitude,0,3,3,BlockMaterial.Wood.color)
        val point=RopePoint(53f,10f,3f)
        assertNotNull(Crafting.nearby(map,point))
        assertNull(Crafting.nearby(map,RopePoint(50f,10f,3f))) // Old distant activation.
        for (p in listOf(RopePoint(56f,10f,3f), RopePoint(61f,10f,3f),
            RopePoint(56f,7f,3f), RopePoint(56f,15f,3f))) assertNotNull(Crafting.nearby(map,p))
        for (p in listOf(RopePoint(51f,10f,3f), RopePoint(62f,10f,3f),
            RopePoint(56f,5f,3f), RopePoint(56f,16f,3f), RopePoint(53f,7f,3f)))
            assertNull(Crafting.nearby(map,p)) // Beyond adjacent block or diagonally separated.

        assertNull(Crafting.nearby(map,RopePoint(50f,30f,3f)))
        assertNull(Crafting.nearby(map,RopePoint(50f,10f,20f)))
        val vm=GameViewModel();val original=vm.bag.slots.toList()
        val authority=SharedWorldAuthority(map,vm,15f,30f,45f,WorldAxis.Latitude,
            knownPosition={WorldLocation(53,10,3)},positions={emptyList()})
        val action=WorldAction(123,"CRAFT",WorldAxis.Latitude,53000,10000,3000,listOf("rope"))
        val receipts=ActionReceipts();var applications=0
        repeat(3) { assertEquals("true;_;_",receipts.resolve(action.id) { applications++;authority.apply(1,action) }) }
        assertEquals(1,applications);assertContentEquals(original,vm.bag.slots)
        assertEquals("false;_;_",authority.apply(1,action.copy(latitude=80000)))
        assertEquals("false;_;_",authority.apply(1,action.copy(latitude=50000)))
        assertEquals("false;_;_",authority.apply(1,action.copy(arguments=listOf("unknown"))))
    }
}
