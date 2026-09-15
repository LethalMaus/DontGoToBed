package dev.jamescullimore.dontgotobed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeleeTargetingTest {
    private fun select(direction: Direction, vararg targets: Cell) =
        MeleeTargeting.select(MeleeTargeting.candidates(10, 11, 3, 5, direction)) { it in targets }

    @Test fun horizontalAimIgnoresCloserFloorAndCeiling() {
        val wrongSide = arrayOf(Cell(9, 2), Cell(9, 6), Cell(12, 2), Cell(12, 6))
        assertEquals(Cell(8, 4), select(Direction.Left, *wrongSide, Cell(8, 4)))
        assertEquals(Cell(13, 4), select(Direction.Right, *wrongSide, Cell(13, 4)))
        assertEquals(Cell(9, 4), select(Direction.Left, *wrongSide))
        assertEquals(Cell(12, 4), select(Direction.Right, *wrongSide))
    }

    @Test fun verticalAimIgnoresCloserWalls() {
        val walls = arrayOf(Cell(9, 5), Cell(12, 5), Cell(9, 3), Cell(12, 3))
        for (direction in listOf(Direction.UpLeft, Direction.UpRight)) {
            assertEquals(Cell(10, 7), select(direction, *walls, Cell(10, 7)))
            assertFalse(select(direction, *walls) in walls)
        }
        for (direction in listOf(Direction.DownLeft, Direction.DownRight)) {
            assertEquals(Cell(11, 1), select(direction, *walls, Cell(11, 1)))
            assertFalse(select(direction, *walls) in walls)
        }
    }

    @Test fun diagonalSideAimStaysAtTheChosenEdge() {
        assertEquals(listOf(Cell(9, 5), Cell(8, 5)), MeleeTargeting.candidates(10, 11, 3, 5, Direction.LeftUp))
        assertEquals(listOf(Cell(12, 3), Cell(13, 3)), MeleeTargeting.candidates(10, 11, 3, 5, Direction.RightDown))
    }

    @Test fun holdingHitCannotSwitchToFloorAfterWallBreaks() {
        val map = TileMap(24, 12).also { it.seedGroundBand() }
        map.placePiece(12, 3, BlockMaterial.Wood, BlockShape.Square)
        map.placePiece(12, 6, BlockMaterial.Stone, BlockShape.Platform)
        val candidates = MeleeTargeting.candidates(10, 11, 3, 5, Direction.Right)
        repeat(12) {
            val target = MeleeTargeting.select(candidates) { map.hasPieceAt(it.c, it.r) }
            map.damagePieceAt(target.c, target.r, 10)
        }
        assertFalse(map.hasPieceAt(12, 4))
        assertEquals(BlockMaterial.Grass.durability, map.getHealth(12, 2))
        assertEquals(BlockMaterial.Stone.durability, map.getHealth(12, 6))
    }

    @Test fun targetsAcrossWorldSeamAndPartialPlayerColumnsStayDirectional() {
        val map = TileMap(24, 12).also { it.seedGroundBand() }
        map.placePiece(23, 4, BlockMaterial.Wood, BlockShape.Single)
        val left = MeleeTargeting.candidates(0, 2, 3, 6, Direction.Left)
        assertEquals(Cell(-1, 4), MeleeTargeting.select(left) { map.hasPieceAt(it.c, it.r) })
        assertTrue(left.all { it.c < 0 && it.r in 3..6 })
        val up = MeleeTargeting.candidates(0, 2, 3, 6, Direction.UpRight)
        assertTrue(up.all { it.c in 0..2 && it.r in 7..8 })
    }
}
