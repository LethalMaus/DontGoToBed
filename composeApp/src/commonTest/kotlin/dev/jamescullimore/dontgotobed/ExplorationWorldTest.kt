package dev.jamescullimore.dontgotobed

import kotlin.test.*

class ExplorationWorldTest {
    @Test fun seededPortalsAreSparseReciprocalConnectedAndRepeatable() {
        assertNotEquals(ExplorationWorld.portals(41), ExplorationWorld.portals(42))
        repeat(100) { seed ->
            val gates = ExplorationWorld.portals(seed + 1)
            assertEquals(gates, ExplorationWorld.portals(seed + 1))
            assertEquals(10, gates.size)
            for (biome in ExplorationBiome.entries) {
                val exits = gates.filter { ExplorationWorld.biome(it.location.latitude,it.location.longitude) == biome }
                assertTrue(exits.size in 1..2)
                assertEquals(exits.size, exits.map { it.destination }.distinct().size)
                for (gate in exits) {
                    assertNotEquals(biome, gate.destination)
                    assertTrue(gates.any { it.destination == biome && ExplorationWorld.biome(it.location.latitude,it.location.longitude) == gate.destination })
                    assertEquals(gate, ExplorationWorld.touching(gate.location.latitude.toFloat(),gate.location.longitude.toFloat(),3f,gates))
                }
                for (destination in ExplorationBiome.entries.filter { it != biome }) {
                    var at = biome
                    repeat(5) { if(at != destination) at = assertNotNull(ExplorationWorld.nextPortal(gates,at,destination)).destination }
                    assertEquals(destination,at)
                }
            }
        }
    }

    @Test fun portalHitsRequireOverlapOrAimedMeleeRangeInEitherView() {
        val portal = ExplorationWorld.portals().first()
        val p = portal.location
        for (axis in WorldAxis.entries) {
            val along = p.along(axis)
            val across = p.across(axis).toFloat()
            fun hit(offset: Int, facing: Direction, other: Float = across): WorldPortal? {
                val left = along + offset
                return ExplorationWorld.hitPortal(
                    if (axis == WorldAxis.Latitude) left.toFloat() else other,
                    if (axis == WorldAxis.Longitude) left.toFloat() else other,
                    3f, axis, MeleeTargeting.candidates(left, left + 1, 3, 5, facing))
            }
            assertEquals(portal, hit(-3, Direction.Right))
            assertEquals(portal, hit(4, Direction.Left))
            assertEquals(portal, hit(0, Direction.Left))
            assertNull(hit(-3, Direction.Left))
            assertNull(hit(-6, Direction.Right))
            assertNull(hit(-3, Direction.Right, across + 10))
        }
    }

    @Test fun eachBiomeHasOutboundAndReturnPortalsWithoutArrivalLoops() {
        for (biome in ExplorationBiome.entries) {
            val exits = ExplorationWorld.portals().filter { ExplorationWorld.biome(it.location.latitude,it.location.longitude) == biome }
            assertEquals(2, exits.size)
            assertTrue(exits.none { it.destination==biome })
            val reached=mutableSetOf(biome)
            repeat(6) { ExplorationWorld.portals().filter { ExplorationWorld.biome(it.location.latitude,it.location.longitude) in reached }.forEach { reached+=it.destination } }
            assertEquals(ExplorationBiome.entries.toSet(),reached)
            val arrival = ExplorationWorld.arrival(biome)
            assertEquals(biome, ExplorationWorld.biome(arrival.latitude,arrival.longitude))
            assertNull(ExplorationWorld.touching(arrival.latitude.toFloat(), arrival.longitude.toFloat(), arrival.row.toFloat()))
            for (exit in exits) {
                val touching = ExplorationWorld.touching(exit.location.latitude.toFloat(), exit.location.longitude.toFloat(), 3f)
                assertEquals(exit, touching)
                assertNull(ExplorationWorld.touching(exit.location.latitude.toFloat(), 30f, 3f))
            }
        }
    }

    @Test fun cavesAndPortalExitsAreWalkableAndBordersSurviveMining() {
        val map = TileMap(576, 54, exploration = true)
        ExplorationWorld.populate(map)
        // Check every permitted placement, not merely one lucky random seed.
        for (b in ExplorationBiome.entries) {
            val o = ExplorationWorld.origin(b)
            for(x in listOf(18,24,30,36,42,150,156,162,168)) for(band in listOf(0,96,192)) for(z in listOf(18,24,30)) {
                for(dx in -1..3) for(dz in -1..3) {
                    assertTrue(map.getVoxel(o.latitude+x+dx,o.longitude+band+z+dz,2))
                    for(y in 3..8) assertFalse(map.getVoxel(o.latitude+x+dx,o.longitude+band+z+dz,y), "Portal blocked: $b $x ${band+z} $y")
                }
                assertTrue(BiomeHazards.contacts(map,RopePoint((o.latitude+x).toFloat(),(o.longitude+band+z).toFloat(),3f)).isEmpty())
            }
        }
        for (biome in ExplorationBiome.entries) {
            val start = ExplorationWorld.origin(biome).latitude
            val z = ExplorationWorld.origin(biome).longitude
            assertEquals(ExplorationWorld.arrival(biome), ExplorationWorld.safeArrival(map, biome))
            // Entrance, tunnel, central chamber: all support a three-cell player.
            for (x in 60..143) {
                assertTrue(map.getVoxel(start + x, z + 10, 2))
                for (y in 3..5) assertFalse(map.getVoxel(start + x, z + 10, y), "Blocked cave entrance at $x,$y")
            }
            // New settlement: a hollow house, accessible from either dimension, outside portal paths.
            val raised=if(biome==ExplorationBiome.Jungle) 4 else 0
            val base=3+raised
            assertTrue(map.getVoxel(start+24,z+62,base), "House floor")
            assertFalse(map.getVoxel(start+24,z+56,base+2), "Front doorway")
            assertFalse(map.getVoxel(start+18,z+62,base+2), "Side doorway")
            assertFalse(map.getVoxel(start+24,z+62,base+3), "Usable interior")
            assertTrue(map.getVoxel(start+24,z+62,base+10), "Pitched roof")
            assertTrue(map.getVoxel(start + 100, z + 26, 17), "Chamber needs a roof")
            for (y in 3..12) assertFalse(map.getVoxel(start + 100, z + 26, y))
            val view = map.collisionView()
            view.setActiveSlice(WorldAxis.Latitude, start, z + 10)
            assertTrue(view.get(start, 12))
            view.damage(start, 12, Int.MAX_VALUE)
            assertTrue(map.getVoxel(start, z + 10, 12))
            assertEquals(-1, view.placePiece(start, 12, BlockMaterial.Grass, BlockShape.Single))
            // Mining in one biome cannot change another; returning sees the original edit.
            map.damageVoxel(start + 33, z + 10, 1, 100)
            assertFalse(map.getVoxel(start + 33, z + 10, 1))
        }
    }

    @Test fun blockedPortalLandingFindsSupportBesideIt() {
        val map = TileMap(576, 12, exploration = true)
        val at = ExplorationWorld.arrival(ExplorationBiome.Forest)
        for (z in 10..11) {
            map.setActiveSlice(WorldAxis.Latitude, at.latitude, z)
            map.placeBlock(at.latitude - 3, 0, 9, 3, BlockMaterial.Grass.color)
            map.placeBlock(at.latitude, 3, 2, 9, BlockMaterial.Stone.color)
        }
        val safe = assertNotNull(ExplorationWorld.safeArrival(map, ExplorationBiome.Forest))
        assertNotEquals(at.latitude, safe.latitude)
        assertEquals(3, safe.row)
    }

    @Test fun authoritativeTerrainUpdatesKeepRegionCoordinates() {
        val map = TileMap(576, 12, exploration = true)
        val forest = ExplorationWorld.arrival(ExplorationBiome.Forest)
        map.setActiveSlice(WorldAxis.Longitude, forest.latitude, forest.longitude)
        map.placePiece(forest.longitude, 3, BlockMaterial.Wood, BlockShape.Square)
        val changes = map.takeNetworkChanges()
        val replica = TileMap(576, 12, exploration = true)
        replica.applyNetworkBlocks(changes)
        assertTrue(replica.getVoxel(forest.latitude, forest.longitude, 3))
        assertFalse(replica.getVoxel(ExplorationWorld.arrival(ExplorationBiome.Meadow).latitude, forest.longitude, 3))
        map.damageVoxel(forest.latitude, forest.longitude, 3, 100)
        replica.applyNetworkBlocks(map.takeNetworkChanges())
        assertFalse(replica.getVoxel(forest.latitude, forest.longitude, 3))
        assertTrue(replica.getVoxel(192, 10, 3))
    }
    @Test fun portalPairsReturnToTheEnteredGateAcrossSeeds() {
        for (seed in listOf(0, 1, 42, 99, 1234)) {
            val gates = ExplorationWorld.portals(seed)
            for (gate in gates) {
                val exit = assertNotNull(ExplorationWorld.returnGate(gates, gate))
                assertEquals(gate, ExplorationWorld.returnGate(gates, exit))
                assertEquals(gate.destination, ExplorationWorld.biome(exit.location.latitude, exit.location.longitude))
            }
        }
    }
}
