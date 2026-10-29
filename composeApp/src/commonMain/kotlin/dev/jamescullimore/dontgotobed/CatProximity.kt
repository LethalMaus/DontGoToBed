package dev.jamescullimore.dontgotobed

import kotlin.math.sqrt

internal object CatProximity {
    const val RADIUS = 7f * WorldViewport.BLOCK_CELLS
    fun gain(player: RopePoint, cat: GuideCat?, map: TileMap): Float {
        if(cat == null || cat.mood in listOf(CatMood.Flee,CatMood.Gone)) return 0f
        if(map.exploration && ExplorationWorld.biome(player) != ExplorationWorld.biome(cat.point)) return 0f
        val x=wrappedDelta(player.lat,cat.point.lat,map.width)
        val z=wrappedDelta(player.lon,cat.point.lon,map.depth)
        val y=player.row-cat.point.row
        val distance=sqrt(x*x+z*z+y*y)
        return if(distance>RADIUS) 0f else (.15f+.85f*(1f-distance/RADIUS)).coerceIn(0f,1f)
    }
}
