package dev.jamescullimore.dontgotobed

import androidx.compose.ui.unit.dp
import kotlin.test.*

class ShockGameTest {
    @Test fun paralysisExpiresAtTwoSecondsAndNewHitsRefreshIt() {
        val game=ShockGame()
        game.apply("P",0)
        repeat(19) { game.tick(100) }
        assertTrue(game.active("P",0))
        game.tick(99);assertTrue(game.active("P",0))
        game.tick(1);assertFalse(game.active("P",0))
        game.apply("Z",7);repeat(10) { game.tick(100) };game.apply("Z",7)
        repeat(19) { game.tick(100) };assertTrue(game.active("Z",7))
        game.tick(100);assertFalse(game.active("Z",7))
    }

    @Test fun onlyHostCanApplyAndLateJoinReceivesRemainingDuration() {
        val host=ShockGame();val client=ShockGame().also { it.isHost=false }
        var packet="";host.send={ packet=it }
        host.apply("P",2);val old=packet
        repeat(12) { host.tick(100) };host.publish()
        client.receive(8,packet);assertFalse(client.active("P",2))
        client.apply("P",2);assertFalse(client.active("P",2))
        client.receive(0,packet);assertEquals(800,client.remaining[ShockTarget("P",2)])
        client.receive(0,old);assertEquals(800,client.remaining[ShockTarget("P",2)])
        repeat(8) { client.tick(100) };assertFalse(client.active("P",2))
        host.clear();client.receive(0,packet);assertTrue(client.remaining.isEmpty())
    }

    @Test fun electricSkeletonMetadataReplicatesAndDeterminesItsDrop() {
        for(electric in listOf(false,true)) {
            val vm=GameViewModel()
            val ammo=if(electric) ItemType.ElectricArrow else ItemType.Arrow
            vm.applyWorldEntities(listOf(NetworkEntity("S",5,7000,0,3000,item=ammo)),WorldAxis.Latitude,30f) { it.dp }
            assertEquals(electric,vm.skeletons.value.single().electricArrows)
            assertEquals(ammo,vm.worldEntities(WorldAxis.Latitude,30f).single().item)
            vm.damageSkeleton(5,30f,3)
            assertEquals(ammo,vm.potions.value.single().type)
        }
    }

    @Test fun electricArrowDamagesAndShocksBothEnemyKindsOnlyOnce() {
        for(kind in listOf("Z","S")) {
            val vm=GameViewModel();val hits=mutableListOf<ShockTarget>()
            vm.electricEnemyHit={k,id->hits.add(ShockTarget(k,id))}
            vm.applyWorldEntities(listOf(NetworkEntity(kind,5,7000,0,3000,hp=3),
                NetworkEntity("A",9,7500,0,4000,item=ItemType.ElectricArrow)),WorldAxis.Latitude,30f) { it.dp }
            val map=TileMap(24,18)
            vm.advanceProjectiles(map,30f,60f,90f,.016f)
            assertEquals(listOf(ShockTarget(kind,5)),hits)
            assertEquals(2,vm.worldEntities(WorldAxis.Latitude,30f).single { it.kind==kind }.hp)
            assertTrue(vm.arrows.value.isEmpty())
            vm.advanceProjectiles(map,30f,60f,90f,.016f);assertEquals(1,hits.size)
        }
    }

    @Test fun electricAlienHitFreezesItsAttackUntilStunExpires() {
        val alien=AlienGame(TileMap(48,18).also { it.seedGroundBand() })
        val shocks=ShockGame();var damage=0
        alien.players={mapOf(0 to AlienPlayer(RopePoint(12f,12f,3f),WorldAxis.Latitude,true))}
        alien.damage={_,n->damage+=n};alien.isShocked={shocks.active("E",0)}
        repeat(25) { alien.tick(100,1,true) }
        assertTrue(alien.arrowHit(10,12,4,true));shocks.apply("E",0)
        repeat(20) { alien.tick(100,1,true);shocks.tick(100) }
        assertEquals(0,damage);assertEquals(2,alien.state!!.hp)
        repeat(14) { alien.tick(100,1,true) };assertEquals(0,damage)
        alien.tick(100,1,true);assertEquals(1,damage)
    }
}
