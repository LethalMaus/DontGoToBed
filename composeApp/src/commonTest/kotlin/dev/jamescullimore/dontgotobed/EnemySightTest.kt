package dev.jamescullimore.dontgotobed

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnemySightTest {
    private fun sees(map: TileMap, x: Float = 4f, other: Float = 4f, targetX: Float = 10f, targetOther: Float = 8f, bottom: Float = 0f) =
        EnemySight.visible(map, 1f, x, other, bottom, targetX, targetOther, bottom, 16f)

    @Test fun seesAcrossInactiveRowsUntilWallBlocksVision() {
        val map = TileMap(48, 12)
        assertTrue(sees(map))
        map.setActiveSlice(WorldAxis.Latitude, 0, 6)
        map.placePiece(8, 2, BlockMaterial.Stone, BlockShape.Single)
        assertFalse(sees(map))
        map.damageVoxel(8, 6, 2, 100)
        assertTrue(sees(map))
    }

    @Test fun axisSwitchPreservesVisionAndOcclusion() {
        val map = TileMap(48, 12)
        map.setActiveSlice(WorldAxis.Latitude, 0, 6)
        map.placePiece(8, 2, BlockMaterial.Stone, BlockShape.Single)
        map.setActiveSlice(WorldAxis.Longitude, 0, 0)
        // Centers account for the two-cell along-axis and one-cell depth footprint.
        assertFalse(sees(map, x = 3.5f, other = 4.5f, targetX = 7.5f, targetOther = 10.5f))
    }

    @Test fun rangeAndWorldSeamUseBothAxes() {
        val map = TileMap(48, 12)
        assertTrue(sees(map, x = 46f, other = 46f, targetX = 2f, targetOther = 2f))
        assertFalse(sees(map, targetX = 20f, targetOther = 20f))
        assertFalse(sees(map, targetX = 4f, targetOther = 23f))
    }

    @Test fun blocksBelowEyeHeightDoNotHidePlayer() {
        val map = TileMap(48, 12)
        map.setActiveSlice(WorldAxis.Latitude, 0, 6)
        map.placePiece(8, 0, BlockMaterial.Stone, BlockShape.Single)
        assertTrue(sees(map))
    }
    @Test fun pursuitLosesSightWhenTargetLeavesRange() {
        val map = TileMap(48, 12)
        val sight = EnemySightTracker()
        assertTrue(sight.canSee(0, map, 1f, 4f, 4f, 0f, 10f, 8f, 0f, 16f))
        assertFalse(sight.canSee(100, map, 1f, 4f, 4f, 0f, 24f, 24f, 0f, 16f))
        assertTrue(sight.canSee(200, map, 1f, 4f, 4f, 0f, 10f, 8f, 0f, 16f))
    }
}
