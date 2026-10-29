package dev.jamescullimore.dontgotobed

import kotlin.math.floor

internal enum class HazardKind(val title: String) {
    Lava("Lava — jump across"), Fire("Fire jets — watch the timing"), Quicksand("Quicksand — jump to escape"),
    Tornado("Tornado — keep your distance"), Water("Water — movement slows"), Vine("Press up to climb the vine")
}
internal data class BiomeHazard(val kind: HazardKind, val latitude: Int, val longitude: Int,
    val width: Int, val depth: Int, val row: Int = 3, val height: Int = 1) {
    fun touches(p: RopePoint): Boolean = p.lat < latitude + width && p.lat + 2 > latitude &&
        p.lon < longitude + depth && p.lon + 2 > longitude && p.row < row + height && p.row + 3 > row
    fun active(timeMs: Long) = kind != HazardKind.Fire || timeMs.mod(4000) >= 2000
}
internal object BiomeHazards {
    val all: List<BiomeHazard> = buildList {
        for (biome in listOf(ExplorationBiome.Lava, ExplorationBiome.Jungle, ExplorationBiome.Desert)) {
            val o = ExplorationWorld.origin(biome)
            for (dz in 0 until ExplorationWorld.REGION_DEPTH step 96) {
                fun add(kind: HazardKind, x: Int, z: Int, w: Int, d: Int, h: Int = 1) {
                    add(BiomeHazard(kind, o.latitude + x, o.longitude + dz + z, w, d, height = h))
                }
                when (biome) {
                    ExplorationBiome.Lava -> { add(HazardKind.Lava, 72, 8, 6, 6); add(HazardKind.Fire, 126, 8, 3, 6, 6) }
                    ExplorationBiome.Jungle -> { add(HazardKind.Water, 69, 8, 12, 6); add(HazardKind.Vine, 156, 9, 3, 3, 21) }
                    ExplorationBiome.Desert -> { add(HazardKind.Quicksand, 69, 8, 9, 6); add(HazardKind.Tornado, 150, 8, 6, 6, 9) }
                    else -> Unit
                }
            }
        }
    }
    fun contacts(map: TileMap, p: RopePoint): List<BiomeHazard> = if (!map.exploration) emptyList() else all.filter {
        it.touches(p) && (it.kind == HazardKind.Vine || (it.latitude until it.latitude + it.width).any { x ->
            map.getVoxel(x, floor(p.lon).toInt(), it.row - 1)
        })
    }
    fun speed(contacts: List<BiomeHazard>) = when {
        contacts.any { it.kind == HazardKind.Quicksand } -> .3f
        contacts.any { it.kind == HazardKind.Water } -> .55f
        else -> 1f
    }
}
/** One host damage clock per player; standing in overlapping hazards cannot multiply damage. */
internal class HazardDamageClock {
    private val cooldowns = mutableMapOf<Int, Long>()
    fun advance(deltaMs: Long, map: TileMap, players: Map<Int, RopePoint>, timeMs: Long, hit: (Int) -> Unit) {
        cooldowns.keys.retainAll(players.keys)
        players.forEach { (id, p) ->
            val remaining = ((cooldowns[id] ?: 0) - deltaMs.coerceIn(0, 200)).coerceAtLeast(0)
            cooldowns[id] = remaining
            val danger = BiomeHazards.contacts(map,p).any { it.kind in listOf(HazardKind.Lava, HazardKind.Fire) && it.active(timeMs) }
            if (danger && remaining == 0L) { cooldowns[id] = 1200; hit(id) }
        }
    }
}
