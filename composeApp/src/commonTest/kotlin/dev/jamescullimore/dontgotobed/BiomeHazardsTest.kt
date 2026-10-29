package dev.jamescullimore.dontgotobed

import kotlin.test.*

class BiomeHazardsTest {
    private fun fixture(h:BiomeHazard):TileMap = TileMap(576,54,exploration=true).also { map ->
        for(z in h.longitude until h.longitude+h.depth) {
            map.setActiveSlice(WorldAxis.Latitude,h.latitude,z)
            map.placeBlock(h.latitude,0,h.width,3,BlockMaterial.Grass.color)
        }
    }
    @Test fun lavaDamagesOnlyTheTouchingPlayerWithACooldownAndJumpingAvoidsIt() {
        val lava=BiomeHazards.all.first { it.kind==HazardKind.Lava };val map=fixture(lava)
        val p=RopePoint(lava.latitude.toFloat(),lava.longitude.toFloat(),3f)
        val clock=HazardDamageClock();val hits=mutableListOf<Int>()
        repeat(12) { clock.advance(100,map,mapOf(0 to p.copy(lon=10f),1 to p,2 to p.copy(row=5f)),0) { hits+=it } }
        assertEquals(listOf(1),hits)
        clock.advance(100,map,mapOf(1 to p),0) { hits+=it }
        assertEquals(listOf(1,1),hits)
    }
    @Test fun fireHasAWarningIntervalAndSafeWindow() {
        val h=BiomeHazards.all.first { it.kind==HazardKind.Fire }
        assertFalse(h.active(0));assertFalse(h.active(1999));assertTrue(h.active(2000));assertFalse(h.active(4000))
    }
    @Test fun jumpingOutOfQuicksandRestoresMovementAndBuildingAbovePoolsIsSafe() {
        val h=BiomeHazards.all.first { it.kind==HazardKind.Quicksand };val map=fixture(h)
        val p=RopePoint(h.latitude.toFloat(),h.longitude.toFloat(),3f)
        assertEquals(.3f,BiomeHazards.speed(BiomeHazards.contacts(map,p)))
        assertEquals(1f,BiomeHazards.speed(BiomeHazards.contacts(map,p.copy(row=5f))))
        assertTrue(BiomeHazards.contacts(map,p.copy(row=6f)).isEmpty())
    }
    @Test fun gatewaysAreSafeAndEveryNewBiomeHasItsOwnHazards() {
        for(b in ExplorationBiome.entries) {
            val p=ExplorationWorld.arrival(b).let { RopePoint(it.latitude.toFloat(),it.longitude.toFloat(),it.row.toFloat()) }
            assertTrue(BiomeHazards.all.none { it.touches(p) })
        }
        assertEquals(setOf(ExplorationBiome.Lava,ExplorationBiome.Jungle,ExplorationBiome.Desert),
            BiomeHazards.all.map { ExplorationWorld.biome(it.latitude,it.longitude) }.toSet())
    }
}
