package dev.jamescullimore.dontgotobed

import androidx.compose.ui.graphics.Color

class TileMap(val width: Int, val height: Int) {
    val maxHealth = 30
    private val occ = IntArray(width * height) { -1 }
    private val blocks = mutableMapOf<Int, Block>()
    private var nextId = 1
    private val lock = Any()

    fun forEachBlock(action: (Block) -> Unit) {
        // Iterate over a stable snapshot to avoid ConcurrentModification during rendering
        val snapshot = synchronized(lock) { blocks.values.map { it.copy() } }
        snapshot.forEach(action)
    }

    fun idx(x: Int, y: Int): Int = (y * width) + ((x % width) + width) % width
    fun inY(y: Int) = y in 0..<height
    fun get(x: Int, y: Int): Boolean = inY(y) && occ[idx(x, y)] != -1
    fun getHealth(x: Int, y: Int): Int {
        if (!inY(y)) return 0
        val id = occ[idx(x, y)]
        return if (id == -1) 0 else (blocks[id]?.health ?: 0)
    }
    fun setHealth(x: Int, y: Int, health: Int) {
        if (!inY(y)) return
        synchronized(lock) {
            val id = occ[idx(x, y)]
            val b = blocks[id] ?: return
            val clamped = health.coerceIn(0, b.maxHealth)
            b.health = clamped
            if (b.health <= 0) removeBlockLocked(id)
        }
    }
    fun set(x: Int, y: Int, solid: Boolean) {
        if (!inY(y)) return
        if (solid) {
            // Place a 3x3 block anchored at (x,y)
            placeBlock(x, y, 3, 3, Color(0xFF2E8B57))
        } else {
            val id = occ[idx(x, y)]
            if (id != -1) removeBlock(id)
        }
    }
    fun placeBlock(col: Int, row: Int, w: Int, h: Int, color: Color, health: Int = maxHealth): Int {
        synchronized(lock) {
            // Check bounds and emptiness
            if (!inY(row) || !inY(row + h - 1)) return -1
            val anchorColWrapped = ((col % width) + width) % width
            for (dx in 0 until w) {
                val cx = (anchorColWrapped + dx) % width
                for (dy in 0 until h) {
                    val ry = row + dy
                    if (!inY(ry) || occ[idx(cx, ry)] != -1) return -1
                }
            }
            val id = nextId++
            val b = Block(id, anchorColWrapped, row, w, h, health, maxHealth, color)
            blocks[id] = b
            for (dx in 0 until w) {
                val cx = (anchorColWrapped + dx) % width
                for (dy in 0 until h) occ[idx(cx, row + dy)] = id
            }
            return id
        }
    }
    private fun removeBlockLocked(id: Int) {
        val b = blocks[id] ?: return
        for (dx in 0 until b.w) {
            val cx = (b.col + dx) % width
            for (dy in 0 until b.h) occ[idx(cx, b.row + dy)] = -1
        }
        blocks.remove(id)
    }
    fun removeBlock(id: Int) {
        synchronized(lock) {
            removeBlockLocked(id)
        }
    }
    fun damage(x: Int, y: Int, amount: Int): Int {
        if (!inY(y)) return 0
        return synchronized(lock) {
            val id = occ[idx(x, y)]
            val b = blocks[id] ?: return@synchronized 0
            b.health = (b.health - amount).coerceAtLeast(0)
            if (b.health <= 0) removeBlockLocked(id)
            b.health
        }
    }
    fun snapshotBlocks(): List<Block> = synchronized(lock) { blocks.values.map { it.copy() } }
}
