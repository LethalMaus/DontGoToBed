package dev.jamescullimore.dontgotobed

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

enum class WorldAxis { Latitude, Longitude }

enum class BlockMaterial(val color: Color, val durability: Int) {
    Grass(Color(0xFF2E8B57), 30), Wood(Color(0xFF8B5A2B), 42), Stone(Color(0xFF667085), 60)
}

enum class BlockShape(val width: Int, val height: Int) {
    Single(1, 1), Platform(3, 1), Pillar(1, 3), Square(3, 3), RampLeft(3, 3), RampRight(3, 3)
}

data class BlockDamageResult(val material: BlockMaterial, val remainingHealth: Int) {
    val destroyed get() = remainingHealth == 0
}

/** A wrapped voxel world. Legacy x/y calls intentionally address the active 2D slice. */
class TileMap(val width: Int, val height: Int, val depth: Int = width, private val source: TileMap? = null) {
    var projectileRevision by mutableIntStateOf(0)
        private set
    val maxHealth = BlockMaterial.Grass.durability
    var activeAxis: WorldAxis = WorldAxis.Latitude
        private set
    var activeSlice: Int = 0
        private set
    private val occ: IntArray = source?.occ ?: IntArray(width * depth * height) { -1 }
    private val blocks: MutableMap<Int, Block> = source?.blocks ?: mutableMapOf<Int, Block>()
    private val ramps: MutableMap<Int, Block> = source?.ramps ?: mutableMapOf<Int, Block>()
    private val blocksBySlice: Array<MutableSet<Int>> = source?.blocksBySlice ?: Array(depth) { mutableSetOf<Int>() }
    private var nextId = 1
    private var nextManagedId = Int.MAX_VALUE
    private val changedBlocks: MutableSet<Int> = source?.changedBlocks ?: mutableSetOf()

    fun networkBlocks(): List<Block> = blocks.values.filter { !it.managed }.map { it.copy() }

    fun takeNetworkChanges(): List<Pair<Int, Block?>> = changedBlocks.map { it to blocks[it]?.copy() }.also { changedBlocks.clear() }

    /** Install authoritative pieces without changing the local camera plane. */
    fun applyNetworkBlocks(changes: List<Pair<Int, Block?>>, reset: Boolean = false) {
        if (reset) blocks.values.filter { !it.managed }.map { it.id }.forEach(::removeBlockLocked)
        changes.forEach { (id, _) -> if (blocks[id]?.managed != true) removeBlockLocked(id) }
        changes.forEach { (id, b) ->
            if (b != null && !b.managed) {
                blocks[id] = b.copy()
                if (b.shape == BlockShape.RampLeft || b.shape == BlockShape.RampRight) ramps[id] = blocks.getValue(id)
                blocksBySlice[b.fixedCoordinate].add(id)
                for (dx in 0 until b.w) for (dy in 0 until b.h) {
                    if (b.shape == BlockShape.RampLeft && dy > dx) continue
                    if (b.shape == BlockShape.RampRight && dy >= b.w - dx) continue
                    val lat = if (b.axis == WorldAxis.Latitude) b.col + dx else b.fixedCoordinate
                    val lon = if (b.axis == WorldAxis.Latitude) b.fixedCoordinate else b.col + dx
                    occ[idx(lat, lon, b.row + dy)] = id
                }
                nextId = maxOf(nextId, id + 1)
            }
        }
        changedBlocks.clear()
        projectileRevision++
    }

    /** Independent collision plane over shared voxels, without copying the world. */
    fun collisionView() = TileMap(width, height, depth, this)

    fun setActiveSlice(axis: WorldAxis, latitude: Int, longitude: Int) {
        activeAxis = axis
        activeSlice = wrap(if (axis == WorldAxis.Latitude) longitude else latitude, depth)
    }
    fun wrap(value: Int, size: Int = width): Int = ((value % size) + size) % size
    fun idx(latitude: Int, longitude: Int, y: Int): Int = ((y * depth) + wrap(longitude, depth)) * width + wrap(latitude)
    fun inY(y: Int) = y in 0..<height
    fun getVoxel(latitude: Int, longitude: Int, y: Int): Boolean =
        inY(y) && occ[idx(latitude, longitude, y)] != -1
    fun damageVoxel(latitude: Int, longitude: Int, y: Int, amount: Int) {
        if (!inY(y)) return
        val block = blocks[occ[idx(latitude, longitude, y)]] ?: return
        // Encounter objects are damaged atomically by their owner, never by a stray arrow.
        if (block.managed) return
        block.health = (block.health - amount).coerceAtLeast(0)
        if (!block.managed) changedBlocks.add(block.id)
        if (block.health == 0) removeBlockLocked(block.id)
        projectileRevision++
    }
    /** Hot collision path: return the array index without allocating a Triple per tile. */
    private fun sliceIndex(x: Int, y: Int): Int = if (activeAxis == WorldAxis.Latitude) {
        (y * depth + activeSlice) * width + wrap(x)
    } else (y * depth + wrap(x, depth)) * width + wrap(activeSlice)

    fun get(x: Int, y: Int): Boolean {
        if (!inY(y)) return false
        return occ[sliceIndex(x, y)] != -1
    }
    fun getHealth(x: Int, y: Int): Int {
        if (!inY(y)) return 0
        return blocks[occ[sliceIndex(x, y)]]?.health ?: 0
    }
    fun setHealth(x: Int, y: Int, health: Int) {
        if (!inY(y)) return
        val block = blocks[occ[sliceIndex(x, y)]] ?: return
        block.health = health.coerceIn(0, block.maxHealth)
        if (!block.managed) changedBlocks.add(block.id)
        if (block.health <= 0) removeBlockLocked(block.id)
    }
    fun set(x: Int, y: Int, solid: Boolean) {
        if (solid) placeBlock(x, y, 3, 3, Color(0xFF2E8B57))
        else if (inY(y)) {
            occ[sliceIndex(x, y)].takeIf { it != -1 }?.let(::removeBlockLocked)
        }
    }
    fun placePiece(x: Int, row: Int, material: BlockMaterial, shape: BlockShape, axis: WorldAxis = activeAxis): Int =
        placeBlock(x, row, shape.width, shape.height, material.color, material.durability, material, shape, axis)

    /** Creates a stable floor on every longitude so depth traversal never opens into the void. */
    fun seedGroundBand(rows: Int = 3) {
        val originalAxis = activeAxis
        val originalSlice = activeSlice
        activeAxis = WorldAxis.Latitude
        for (longitude in 0 until depth) {
            activeSlice = longitude
            for (latitude in 0 until width step 3) {
                placeBlock(latitude, 0, 3, rows, BlockMaterial.Grass.color, BlockMaterial.Grass.durability)
            }
        }
        activeAxis = originalAxis
        activeSlice = originalSlice
    }

    /**
     * Seeds gentle, repeatable landmarks through both horizontal axes.  Each feature is
     * deliberately only a tile high at its entrance, so it teaches walking and ramps
     * without turning a fresh world into an obstacle course.
     */
    fun seedAdventureTerrain() {
        val originalAxis = activeAxis
        val originalSlice = activeSlice

        fun seedSlice(axis: WorldAxis, fixed: Int) {
            activeAxis = axis
            activeSlice = fixed
            val offset = (fixed * 17 + 11) % width
            fun at(delta: Int) = wrap(offset + delta)

            // A short ramp, viewing platform, and return ramp form a safe traversal lesson.
            placeBlock(at(0), 3, 3, 3, BlockMaterial.Stone.color, BlockMaterial.Stone.durability,
                BlockMaterial.Stone, BlockShape.RampLeft, axis)
            placeBlock(at(3), 5, 3, 1, BlockMaterial.Wood.color, BlockMaterial.Wood.durability,
                BlockMaterial.Wood, BlockShape.Platform, axis)
            placeBlock(at(6), 3, 3, 3, BlockMaterial.Stone.color, BlockMaterial.Stone.durability,
                BlockMaterial.Stone, BlockShape.RampRight, axis)

            // Sparse one-tile stepping stones keep every slice interesting but traversable.
            placeBlock(at(18), 3, 1, 1, BlockMaterial.Grass.color, BlockMaterial.Grass.durability,
                BlockMaterial.Grass, BlockShape.Single, axis)
            placeBlock(at(26), 3, 1, 1, BlockMaterial.Stone.color, BlockMaterial.Stone.durability,
                BlockMaterial.Stone, BlockShape.Single, axis)

            // Every twelfth slice gets a small wooden shelter as a recognisable landmark.
            if (fixed % 12 == 0) {
                val house = at(42)
                placeBlock(house, 3, 1, 3, BlockMaterial.Wood.color, BlockMaterial.Wood.durability,
                    BlockMaterial.Wood, BlockShape.Pillar, axis)
                placeBlock(house + 4, 3, 1, 3, BlockMaterial.Wood.color, BlockMaterial.Wood.durability,
                    BlockMaterial.Wood, BlockShape.Pillar, axis)
                placeBlock(house, 6, 5, 1, BlockMaterial.Wood.color, BlockMaterial.Wood.durability,
                    BlockMaterial.Wood, BlockShape.Platform, axis)
            }
        }

        for (longitude in 0 until depth) seedSlice(WorldAxis.Latitude, longitude)
        for (latitude in 0 until width) seedSlice(WorldAxis.Longitude, latitude)
        activeAxis = originalAxis
        activeSlice = originalSlice
    }
    fun placeBlock(
        col: Int, row: Int, w: Int, h: Int, color: Color, health: Int = maxHealth,
        material: BlockMaterial = BlockMaterial.Grass, shape: BlockShape = BlockShape.Square,
        axis: WorldAxis = activeAxis, managed: Boolean = false
    ): Int {
        if (!inY(row) || !inY(row + h - 1)) return -1
        val fixed = activeSlice
        val anchor = wrap(col)
        val occupied = IntArray(w * h)
        var occupiedCount = 0
        for (dx in 0 until w) for (dy in 0 until h) {
            if (shape == BlockShape.RampLeft && dy > dx) continue
            if (shape == BlockShape.RampRight && dy >= w - dx) continue
            val lat = if (axis == WorldAxis.Latitude) wrap(anchor + dx) else fixed
            val lon = if (axis == WorldAxis.Latitude) fixed else wrap(anchor + dx, depth)
            val y = row + dy
            if (occ[idx(lat, lon, y)] != -1) return -1
            occupied[occupiedCount++] = idx(lat, lon, y)
        }
        if (occupiedCount == 0) return -1
        val id = if (managed) nextManagedId-- else if (source != null) source.allocateId() else allocateId()
        if (!managed) changedBlocks.add(id)
        blocks[id] = Block(id, anchor, row, w, h, health, health, color, material, shape, axis, fixed, managed)
        if (shape == BlockShape.RampLeft || shape == BlockShape.RampRight) ramps[id] = blocks.getValue(id)
        blocksBySlice[fixed].add(id)
        for (i in 0 until occupiedCount) occ[occupied[i]] = id
        return id
    }
    private fun allocateId(): Int = nextId++

    private fun removeBlockLocked(id: Int) {
        val block = blocks.remove(id) ?: return
        if (!block.managed) changedBlocks.add(id)
        for (dx in 0 until block.w) for (dy in 0 until block.h) {
            val lat = if (block.axis == WorldAxis.Latitude) block.col + dx else block.fixedCoordinate
            val lon = if (block.axis == WorldAxis.Latitude) block.fixedCoordinate else block.col + dx
            val index = idx(lat, lon, block.row + dy)
            if (occ[index] == id) occ[index] = -1
        }
        ramps.remove(id)
        blocksBySlice[block.fixedCoordinate].remove(id)
    }
    fun removeBlock(id: Int) = removeBlockLocked(id)
    fun damage(x: Int, y: Int, amount: Int): Int {
        if (!inY(y)) return 0
        val block = blocks[occ[sliceIndex(x, y)]] ?: return 0
        block.health = (block.health - amount).coerceAtLeast(0)
        if (!block.managed) changedBlocks.add(block.id)
        if (block.health == 0) removeBlockLocked(block.id)
        return block.health
    }

    private fun pieceAt(x: Int, y: Int): Block? {
        if (!inY(y)) return null
        blocks[occ[sliceIndex(x, y)]]?.let { return it }
        val column = wrap(x)
        return ramps.values.firstOrNull { block ->
            val appearsOnActivePlane = if (block.axis == activeAxis) {
                block.fixedCoordinate == activeSlice && (0 until block.w).any { wrap(block.col + it) == column }
            } else {
                (0 until block.w).any { wrap(block.col + it) == activeSlice } &&
                    block.fixedCoordinate == column
            }
            appearsOnActivePlane && y in block.row until (block.row + block.h)
        }
    }

    /** True for every visible part of a placed shape, including the empty half of a ramp. */
    fun hasPieceAt(x: Int, y: Int): Boolean = pieceAt(x, y) != null

    /** Damages the whole placed shape even when a triangular ramp cell is visually empty. */
    fun damagePieceAt(x: Int, y: Int, amount: Int): BlockDamageResult? {
        val block = pieceAt(x, y) ?: return null
        block.health = (block.health - amount).coerceAtLeast(0)
        if (!block.managed) changedBlocks.add(block.id)
        if (block.health == 0) removeBlockLocked(block.id)
        return BlockDamageResult(block.material, block.health)
    }
    /**
     * Projects occupied voxels onto the active latitude/longitude plane.  This is intentionally
     * derived from occupancy rather than placement orientation: after an axis swap, the terrain
     * intersecting the player's current column remains visible (ramps become solid profile cells).
     */
    fun snapshotBlocks(): List<Block> {
        val projected = mutableListOf<Block>()
        for (row in 0 until height) {
            var col = 0
            while (col < width) {
                val first = blocks[occ[sliceIndex(col, row)]]
                if (first == null) {
                    col++
                    continue
                }
                var end = col + 1
                while (end < width) {
                    val next = blocks[occ[sliceIndex(end, row)]] ?: break
                    if (next.color != first.color || next.material != first.material || next.health != first.health || next.maxHealth != first.maxHealth) break
                    end++
                }
                projected += Block(
                    id = first.id, col = col, row = row, w = end - col, h = 1,
                    health = first.health, maxHealth = first.maxHealth, color = first.color,
                    material = first.material, shape = BlockShape.Square,
                    axis = activeAxis, fixedCoordinate = activeSlice
                )
                col = end
            }
        }
        return projected
    }

    /**
     * Projection already emits maximal horizontal runs in row/column order. Only a run
     * starting at the same column can continue vertically, so index it instead of sorting
     * and scanning every previously emitted rectangle for each row.
     */
    fun snapshotRenderableBlocks(): List<Block> {
        val merged = ArrayList<Block>()
        val lastAtColumn = IntArray(width) { -1 }
        for (block in snapshotBlocks()) {
            val previousIndex = lastAtColumn[block.col]
            val previous = if (previousIndex >= 0) merged[previousIndex] else null
            if (previous != null && previous.w == block.w && previous.row + previous.h == block.row &&
                previous.color == block.color && previous.material == block.material &&
                previous.health == block.health && previous.maxHealth == block.maxHealth) {
                merged[previousIndex] = previous.copy(h = previous.h + block.h)
            } else {
                lastAtColumn[block.col] = merged.size
                merged.add(block)
            }
        }
        return merged
    }
}
