package dev.jamescullimore.dontgotobed

import kotlin.math.abs

internal data class CraftRecipe(val id: String, val item: ItemType, val count: Int, val materials: Map<BlockMaterial, Int>) {
    val name get() = InventorySlot(item, count).displayName
}
internal enum class CraftResult { Crafted, MissingMaterials, BagFull, UnknownRecipe }
internal data class CraftPlan(val result: CraftResult, val slots: List<InventorySlot>? = null)

/** Personal bag transactions; a shared bench never owns or removes another player's materials. */
internal object Crafting {
    val recipes = listOf(
        CraftRecipe("arrows", ItemType.Arrow, 4, mapOf(BlockMaterial.Wood to 1, BlockMaterial.Stone to 1)),
        CraftRecipe("rope", ItemType.Rope, 1, mapOf(BlockMaterial.Grass to 3)),
        CraftRecipe("shield", ItemType.Shield, 1, mapOf(BlockMaterial.Wood to 4, BlockMaterial.Stone to 1))
    )
    fun bench(biome: ExplorationBiome) = ExplorationWorld.arrival(biome).let { it.copy(latitude = it.latitude + 6) }
    fun benchSupported(map: TileMap, at: WorldLocation) = map.getVoxel(at.latitude, at.longitude, at.row - 1) &&
        map.getVoxel(at.latitude + 2, at.longitude, at.row - 1)
    fun nearby(map: TileMap, point: RopePoint): WorldLocation? {
        if (!map.exploration) return null
        val at = bench(ExplorationWorld.biome(point))
        // One 3-cell block directly beside the 3×3 workbench footprint, not a radius.
        // Use the player's 2×2 footprint so access is symmetric from either world axis.
        val overlapsLatitude = point.lat < at.latitude + 3 && point.lat + 2 > at.latitude
        val overlapsLongitude = point.lon < at.longitude + 3 && point.lon + 2 > at.longitude
        val besideLatitude = point.lat < at.latitude + 6 && point.lat + 2 > at.latitude - 3
        val besideLongitude = point.lon < at.longitude + 6 && point.lon + 2 > at.longitude - 3
        return at.takeIf { ((overlapsLatitude && besideLongitude) || (overlapsLongitude && besideLatitude)) &&
            abs(point.row - it.row) <= 2 && benchSupported(map, it) }
    }
    fun count(bag: PlayerInventory, material: BlockMaterial) = bag.slots.sumOf {
        if (it.type == ItemType.Block && it.material == material) it.count else 0
    }
    fun plan(bag: PlayerInventory, id: String): CraftPlan {
        val recipe = recipes.firstOrNull { it.id == id } ?: return CraftPlan(CraftResult.UnknownRecipe)
        if (recipe.materials.any { (material, amount) -> count(bag, material) < amount }) return CraftPlan(CraftResult.MissingMaterials)
        val candidate = PlayerInventory(bag.capacity, bag.stackLimit).also { it.restore(bag.slots.toList()) }
        for ((material, amount) in recipe.materials) {
            var remaining = amount
            for (index in candidate.slots.indices) {
                val slot = candidate.slots[index]
                if (slot.type == ItemType.Block && slot.material == material) {
                    val used = minOf(slot.count, remaining)
                    if (used > 0) { candidate.consume(index, used); remaining -= used }
                }
            }
        }
        if (candidate.addExact(recipe.item, recipe.count).remaining != 0) return CraftPlan(CraftResult.BagFull)
        return CraftPlan(CraftResult.Crafted, candidate.slots.toList())
    }
    fun make(bag: PlayerInventory, id: String): CraftResult {
        val plan = plan(bag, id)
        plan.slots?.let { bag.restore(it) }
        return plan.result
    }
}
