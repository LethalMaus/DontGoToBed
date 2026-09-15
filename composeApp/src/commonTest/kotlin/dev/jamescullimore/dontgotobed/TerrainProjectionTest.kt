package dev.jamescullimore.dontgotobed

import kotlin.test.*
import kotlin.random.Random

class TerrainProjectionTest {
    @Test fun optimizedProjectionMatchesOriginalForGapsRampsMaterialsHealthAndAxes() {
        val map = TileMap(48, 30)
        map.seedGroundBand()
        val random = Random(18)
        repeat(1200) {
            val axis = WorldAxis.entries[random.nextInt(2)]
            val slice = random.nextInt(map.width)
            map.setActiveSlice(axis, slice, slice)
            val col = random.nextInt(map.width)
            val row = random.nextInt(3, 27)
            map.placePiece(col, row, BlockMaterial.entries[random.nextInt(3)], BlockShape.entries[random.nextInt(6)])
            if (it % 4 == 0) map.damagePieceAt(col, row, 10)
        }
        for (axis in WorldAxis.entries) for (slice in 0 until map.width) {
            map.setActiveSlice(axis, slice, slice)
            assertEquals(legacyProjection(map), map.snapshotRenderableBlocks(), "$axis / $slice")
        }
    }

    fun legacyProjection(map: TileMap): List<Block> {
        val source = map.snapshotBlocks().sortedWith(compareBy<Block> { it.row }.thenBy { it.col })
        val merged = mutableListOf<Block>()
        source.forEach { block ->
            val horizontalPrevious = merged.lastOrNull()
            val canMergeHorizontally = horizontalPrevious != null &&
                horizontalPrevious.shape == BlockShape.Square && block.shape == BlockShape.Square &&
                horizontalPrevious.row == block.row && horizontalPrevious.h == block.h &&
                horizontalPrevious.color == block.color && horizontalPrevious.material == block.material && horizontalPrevious.health == block.health &&
                horizontalPrevious.maxHealth == block.maxHealth && horizontalPrevious.col + horizontalPrevious.w == block.col
            if (canMergeHorizontally) {
                merged[merged.lastIndex] = horizontalPrevious!!.copy(w = horizontalPrevious.w + block.w)
            } else {
                val verticalIndex = merged.indexOfLast { previous ->
                    previous.shape == BlockShape.Square && block.shape == BlockShape.Square &&
                        previous.col == block.col && previous.w == block.w &&
                        previous.row + previous.h == block.row &&
                        previous.color == block.color && previous.material == block.material && previous.health == block.health &&
                        previous.maxHealth == block.maxHealth
                }
                if (verticalIndex >= 0) {
                    val previous = merged[verticalIndex]
                    merged[verticalIndex] = previous.copy(h = previous.h + block.h)
                } else merged += block
            }
        }
        return merged
    }
}
