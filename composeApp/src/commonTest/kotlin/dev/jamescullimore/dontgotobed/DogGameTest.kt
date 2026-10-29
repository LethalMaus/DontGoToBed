package dev.jamescullimore.dontgotobed

import kotlin.test.*
import kotlin.random.Random

class DogGameTest {
    @Test fun startsAwayApproachesSlowlyAndHowlsOnlyOnDiscovery() {
        val map=TileMap(96,24).apply { seedGroundBand() }
        var player=RopePoint(20f,10f,3f)
        val messages=mutableListOf<String>();var finds=0
        val host=DogGame(map,Random(7)).apply { players={mapOf(0 to player)};send={messages+=it};found={_,_->finds++} }
        host.tick(100);val first=assertNotNull(host.dog)
        assertEquals(-1,first.owner);assertEquals(0,finds)
        host.tick(100)
        assertTrue(kotlin.math.abs(wrappedDelta(first.point.lat,host.dog!!.point.lat,map.width))<=.181f)
        player=host.dog!!.point
        host.tick(100);assertEquals(0,host.dog?.owner);assertEquals(1,finds)
        repeat(10) { host.tick(100) };assertEquals(1,finds)
        val client=DogGame(map).apply { isHost=false }
        client.receive(9,messages.last());assertNull(client.dog)
        client.receive(0,messages.last());assertEquals(0,client.dog?.owner)
        val before=client.dog;client.tick(100);assertEquals(before,client.dog)
        client.receive(0,messages.first());assertEquals(before,client.dog)
        var heard=0;client.found={_,_->heard++}
        val discovery=messages.first { it.startsWith("DOG_FOUND;") }
        client.receive(0,discovery);client.receive(0,discovery);client.receive(9,discovery)
        assertEquals(1,heard)
    }
    @Test fun discoveredDogFollowsOwnerAcrossPortalAndRejectsDistantCall() {
        val map=TileMap(576,24,exploration=true)
        for(z in listOf(10,298)) for(dz in -4..4) {
            map.setActiveSlice(WorldAxis.Latitude,40,z+dz)
            map.placeBlock(40,0,30,3,BlockMaterial.Grass.color)
        }
        var owner=RopePoint(50f,10f,3f)
        val dog=DogGame(map,Random(2)).apply { players={mapOf(0 to owner)} }
        dog.tick(100);owner=dog.dog!!.point;dog.tick(100)
        assertEquals(0,dog.dog?.owner)
        dog.players={mapOf(0 to owner,1 to RopePoint(54f,298f,3f))}
        owner=RopePoint(50f,10f,3f);dog.tick(100)
        dog.receive(1,"DOG_FOLLOW");assertEquals(0,dog.dog?.owner)
        owner=owner.copy(lon=298f);dog.tick(100)
        assertEquals(ExplorationBiome.Lava,ExplorationWorld.biome(assertNotNull(dog.dog).point))
        dog.receive(1,"DOG_FOLLOW");assertEquals(1,dog.dog?.owner)
    }
}
