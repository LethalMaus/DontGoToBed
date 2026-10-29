package dev.jamescullimore.dontgotobed

import androidx.compose.ui.graphics.Color
import kotlin.random.Random

/** Biomes occupy disjoint coordinate regions, so all existing host authority stays in world space. */
internal enum class ExplorationBiome(val title: String, val ground: Color, val rock: Color, val sky: Color) {
    Meadow("Meadow", Color(0xFF639C50), Color(0xFF817563), Color(0xFF9ACAD0)),
    Forest("Deep forest", Color(0xFF355D41), Color(0xFF5C665C), Color(0xFF476D76)),
    Frost("Frost peaks", Color(0xFFB7D8DB), Color(0xFF667F99), Color(0xFFB1C8E1)),
    Lava("Lava lands", Color(0xFF583E46), Color(0xFF382F3E), Color(0xFF663844)),
    Jungle("Jungle", Color(0xFF347846), Color(0xFF496345), Color(0xFF6BAA94)),
    Desert("Desert", Color(0xFFD7B76C), Color(0xFFB68B55), Color(0xFFEBC28B))
}

internal data class WorldPortal(val location: WorldLocation, val destination: ExplorationBiome)

internal object ExplorationWorld {
    const val SIZE = 576
    const val REGION = 192
    const val BORDER = 6
    const val REGION_DEPTH = 288
    fun origin(biome: ExplorationBiome) = WorldLocation(biome.ordinal % 3 * REGION, biome.ordinal / 3 * REGION_DEPTH, 0)
    fun biome(latitude: Int, longitude: Int = 10) = ExplorationBiome.entries[latitude.mod(SIZE) / REGION + 3 * (longitude.mod(SIZE) / REGION_DEPTH)]
    fun biome(point: RopePoint) = biome(kotlin.math.floor(point.lat).toInt(), kotlin.math.floor(point.lon).toInt())
    fun boundary(latitude: Int, longitude: Int = 10) = latitude.mod(REGION) < BORDER || latitude.mod(REGION) >= REGION - BORDER ||
        longitude.mod(REGION_DEPTH) < BORDER || longitude.mod(REGION_DEPTH) >= REGION_DEPTH - BORDER
    private fun legacyPortals() = ExplorationBiome.entries.flatMap { biome ->
        val start = origin(biome)
        listOf(WorldPortal(WorldLocation(start.latitude + 24, start.longitude + 10, 3), ExplorationBiome.entries[(biome.ordinal + 1) % ExplorationBiome.entries.size]),
            WorldPortal(WorldLocation(start.latitude + 42, start.longitude + 10, 3), ExplorationBiome.entries[(biome.ordinal + ExplorationBiome.entries.size - 1) % ExplorationBiome.entries.size]))
    }
    /** A shuffled, bidirectional chain gives every biome one or two distinct neighbours. */
    fun portals(seed: Int = 0): List<WorldPortal> {
        if (seed == 0) return legacyPortals()
        val random = Random(seed)
        val route = ExplorationBiome.entries.shuffled(random)
        return route.flatMapIndexed { index, biome ->
            val neighbours = listOfNotNull(route.getOrNull(index - 1), route.getOrNull(index + 1))
            // Open lanes between caves, villages and hazards; distinct bands keep two gates apart.
            val bands = listOf(0, 96, 192).shuffled(random)
            val o = origin(biome)
            neighbours.mapIndexed { gate, destination ->
                val x = (listOf(18, 24, 30, 36, 42, 150, 156, 162, 168)).random(random)
                val z = bands[gate] + listOf(18, 24, 30).random(random)
                WorldPortal(WorldLocation(o.latitude + x, o.longitude + z, 3), destination)
            }
        }
    }

    /** Choose the first hop towards a target region, so an undiscovered dog cannot loop at an endpoint. */
    fun nextPortal(portals: List<WorldPortal>, from: ExplorationBiome, to: ExplorationBiome): WorldPortal? {
        val queue = mutableListOf<Pair<ExplorationBiome, WorldPortal?>>(from to null)
        val visited = mutableSetOf(from)
        var cursor = 0
        while (cursor < queue.size) {
            val (region, first) = queue[cursor++]
            if (region == to) return first
            portals.filter { biome(it.location.latitude, it.location.longitude) == region }.forEach { gate ->
                if (visited.add(gate.destination)) queue.add(gate.destination to (first ?: gate))
            }
        }
        return null
    }
    fun arrival(destination: ExplorationBiome) = origin(destination).let { WorldLocation(it.latitude + 50, it.longitude + 10, 3) }
    fun touching(lat: Float, lon: Float, row: Float, portals: List<WorldPortal> = portals()): WorldPortal? = portals.firstOrNull {
        lat < it.location.latitude + 3 && lat + 2 > it.location.latitude &&
            lon < it.location.longitude + 3 && lon + 2 > it.location.longitude &&
            row < it.location.row + 5 && row + 3 > it.location.row
    }
    /** A deliberate melee press works inside an arch or on its aimed face in either view. */
    fun hitPortal(lat: Float, lon: Float, row: Float, axis: WorldAxis, aimed: List<Cell>, portals: List<WorldPortal> = portals()): WorldPortal? =
        touching(lat, lon, row, portals) ?: portals.firstOrNull { portal ->
            val p = portal.location
            val across = if (axis == WorldAxis.Latitude) lon else lat
            across < p.across(axis) + 3 && across + 2 > p.across(axis) && aimed.any { cell ->
                cell.c in p.along(axis) until p.along(axis) + 3 && cell.r in p.row until p.row + 5
            }
        }

    /** Gates connect in pairs: arriving players can immediately hit the return gate. */
    fun returnGate(portals: List<WorldPortal>, entered: WorldPortal): WorldPortal? {
        val from = biome(entered.location.latitude, entered.location.longitude)
        return portals.firstOrNull {
            biome(it.location.latitude, it.location.longitude) == entered.destination && it.destination == from
        }
    }

    fun safeArrival(map: TileMap, entered: WorldPortal): WorldLocation? {
        val exit = returnGate(map.portals, entered) ?: return null
        return safeArrivalNear(map, exit.location)
    }

    /** Keep gateways usable after building/mining. Land beside the arrival marker, never inside a block. */
    fun safeArrival(map: TileMap, destination: ExplorationBiome): WorldLocation? {
        return safeArrivalNear(map, arrival(destination))
    }

    private fun safeArrivalNear(map: TileMap, base: WorldLocation): WorldLocation? {
        for (distance in 0..10) for (dx in listOf(distance, -distance).distinct()) {
            val x = base.latitude + dx
            for (y in base.row until map.height - 3) {
                if ((0..1).all { a -> (0..1).all { b ->
                    map.getVoxel(x + a, base.longitude + b, y - 1) &&
                        (0..2).none { c -> map.getVoxel(x + a, base.longitude + b, y + c) }
                } }) return WorldLocation(x, base.longitude, y)
            }
        }
        return null
    }
    fun populate(map: TileMap) {
        require(map.width == SIZE && map.height >= 36)
        // One-cell slices keep digging and axis turns consistent. Small ground pieces remain mineable.
        for (z in 0 until map.depth) for (x in BORDER until SIZE step 3) {
            if (boundary(x,z) || boundary(x + 2,z)) continue
            map.setActiveSlice(WorldAxis.Latitude, x, z)
            val biome = biome(x,z)
            map.placeBlock(x, 0, 3, 3, biome.ground, material = BlockMaterial.Grass)
        }
        for (biome in ExplorationBiome.entries) {
            val origin = origin(biome).latitude
            val zOrigin = origin(biome).longitude
            // Broad hills contain intersecting, six-cell-high passages, with walkable entrances on both axes.
            for (z0 in zOrigin until zOrigin + REGION_DEPTH step 96) {
                for (z in z0 + BORDER until z0 + 48) for (localX in 60 until 144 step 3) {
                    val edge = minOf(localX - 60, 143 - (localX + 2), z - z0, z0 + 47 - z)
                    val top = 3 + minOf(edge + 1, 24)
                    map.setActiveSlice(WorldAxis.Latitude, origin + localX, z)
                    val tunnel = z - z0 in 7..13 || localX in 96..102
                    val chamber = localX in 84..114 && z - z0 in 9..32
                    val bottom = if (chamber) minOf(14, top - 2) else if (tunnel) 9 else 3
                    if (top > bottom) map.placeBlock(origin + localX, bottom, 3, top - bottom,
                        biome.rock, health = BlockMaterial.Stone.durability, material = BlockMaterial.Stone)
                }
            }
            // Silhouettes and colours distinguish the open paths as well as the caves.
            for (z in zOrigin + 42 until zOrigin + REGION_DEPTH - BORDER step 48) for (x in listOf(14, 36, 156, 171)) {
                map.setActiveSlice(WorldAxis.Latitude, origin + x, z)
                val tall = if (biome in listOf(ExplorationBiome.Forest, ExplorationBiome.Jungle)) 9 else 5
                map.placeBlock(origin + x, 3, 2, tall, BlockMaterial.Wood.color, material = BlockMaterial.Wood)
                map.placeBlock(origin + x - 2, 3 + tall, 6, 3, biome.ground, material = BlockMaterial.Grass)
            }
        }
        BiomeHazards.all.filter { it.kind == HazardKind.Vine }.forEach { vine ->
            for (z in vine.longitude until vine.longitude + vine.depth) {
                map.setActiveSlice(WorldAxis.Latitude, vine.latitude, z)
                map.placeBlock(vine.latitude + 3, 20, 6, 1, ExplorationBiome.Jungle.ground, material = BlockMaterial.Wood)
            }
        }
        RichWorlds.populate(map)
        map.setActiveSlice(WorldAxis.Latitude, 12, 10)
    }
}
