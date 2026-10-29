package dev.jamescullimore.dontgotobed

import kotlin.test.*

class AlienGameTest {
    private fun world()=TileMap(48,18).also { it.seedGroundBand() }
    private fun advance(game: AlienGame, ms: Int, hunting: Boolean=true, round: Long=1) {
        repeat(ms/100) { game.tick(100,round,hunting) }
    }
    @Test fun appearsBehindEitherFacingAndAxisAndWarnsBeforeDamage() {
        for(axis in WorldAxis.entries) for(right in listOf(true,false)) {
            val game=AlienGame(world());val p=AlienPlayer(RopePoint(12f,12f,3f),axis,right)
            game.players={mapOf(0 to p)};var damage=0;game.damage={_,n->damage+=n}
            advance(game,2500)
            val s=assertNotNull(game.state)
            assertEquals(AlienPhase.Warning,s.phase)
            assertEquals(if(right) -3f else 3f,s.point.along(axis)-p.point.along(axis))
            assertEquals(p.point.other(axis),s.point.other(axis))
            advance(game,1400);assertEquals(0,damage)
            advance(game,100);assertEquals(1,damage)
            advance(game,300);assertEquals(1,damage)
        }
    }
    @Test fun hitCancelsAttackTeleportsAgainAndEventuallyDefeatsForTheNight() {
        val game=AlienGame(world());game.players={mapOf(0 to AlienPlayer(RopePoint(12f,12f,3f),WorldAxis.Latitude,true))}
        var damage=0;game.damage={_,n->damage+=n}
        advance(game,2500)
        assertTrue(game.hit(0,10,12,4,2));assertEquals(1,game.state!!.hp)
        assertEquals(AlienPhase.Leaving,game.state!!.phase)
        assertFalse(game.hit(0,10,12,4,2))
        advance(game,2900);assertEquals(AlienPhase.Warning,game.state!!.phase)
        assertEquals(0,damage)
        assertTrue(game.hit(0,10,12,4,1))
        advance(game,10000);assertEquals(AlienPhase.Defeated,game.state!!.phase)
        advance(game,2500,round=2);assertEquals(3,game.state!!.hp)
    }
    @Test fun leavingTheDimensionAvoidsAttackAndSafeTimeRemovesAlien() {
        val game=AlienGame(world());var p=AlienPlayer(RopePoint(12f,12f,3f),WorldAxis.Latitude,true)
        game.players={mapOf(0 to p)};var damage=0;game.damage={_,n->damage+=n}
        advance(game,2500);p=p.copy(point=p.point.copy(lon=13f))
        assertFalse(game.hit(0,10,13,4,1))
        p=p.copy(point=p.point.copy(lon=20f))
        advance(game,1500);assertEquals(0,damage)
        advance(game,100,hunting=false);assertNull(game.state)
    }
    @Test fun hostOwnsSnapshotsAndLateJoinCannotReplayOlderState() {
        val host=AlienGame(world());val client=AlienGame(world()).also { it.isHost=false }
        host.players={mapOf(0 to AlienPlayer(RopePoint(12f,12f,3f),WorldAxis.Latitude,true))}
        var packet="";host.send={packet=it};advance(host,2500)
        val old=packet
        client.receive(7,packet);assertNull(client.state)
        host.publish();client.receive(0,packet);assertEquals(host.state,client.state)
        host.hit(0,10,12,4,1);client.receive(0,packet)
        client.receive(0,old);assertEquals(AlienPhase.Leaving,client.state!!.phase)
        val before=host.state;host.receive(7,old);assertEquals(before,host.state)
    }
    @Test fun cannotSpawnInsideTerrainAndRopeReleasesOnTeleport() {
        val map=world();val game=AlienGame(map)
        val p=AlienPlayer(RopePoint(12f,12f,3f),WorldAxis.Latitude,true)
        game.players={mapOf(0 to p)}
        for(z in 12..13) { map.setActiveSlice(WorldAxis.Latitude,6,z);map.placeBlock(6,3,6,5,BlockMaterial.Stone.color) }
        advance(game,6000);assertNull(game.state)
        val clear=world();val alien=AlienGame(clear);alien.players=game.players
        advance(alien,2500)
        val rope=CompanionGame(clear);rope.players={mapOf(0 to p.point)};rope.hasRope={true}
        rope.targetPoint={alien.targetPoint};rope.moveTarget={_,point->alien.tug(point)}
        rope.tick(100,null,1);assertTrue(rope.command(0,"ATTACH",RopeTarget("A",0)))
        assertTrue(rope.command(0,"PULL"));assertEquals(10f,alien.targetPoint!!.lat)
        assertTrue(alien.hit(0,10,12,4,1));rope.tick(100,null,1);assertTrue(rope.links.isEmpty())
    }
    @Test fun remoteMeleeUsesClientPlaneAndGloveDamage() {
        val map=world();val game=AlienGame(map)
        game.players={mapOf(1 to AlienPlayer(RopePoint(12f,12f,3f),WorldAxis.Longitude,true))}
        advance(game,2500)
        val authority=SharedWorldAuthority(map,GameViewModel(),30f,60f,90f,WorldAxis.Latitude,
            { WorldLocation(12,12,3) },{ listOf(WorldLocation(12,12,3)) },
            hitAlien={actor,lat,lon,row,strength->game.hit(actor,lat,lon,row,strength)},punchDamage={2})
        val hit=WorldAction(1,"HIT",WorldAxis.Longitude,12000,12000,3000,listOf("10","4",Direction.Left.ordinal.toString()))
        assertEquals("true;_;_",authority.apply(1,hit))
        assertEquals(1,game.state!!.hp)
        assertEquals(AlienPhase.Leaving,game.state!!.phase)
        assertEquals("false;_;_",authority.apply(1,hit))
    }
    @Test fun shieldAndArmourApplyToAlienDamage() {
        val game=AlienGame(world());game.players={mapOf(0 to AlienPlayer(RopePoint(12f,12f,3f),WorldAxis.Latitude,true))}
        val gear=PlayerEquipment();gear.loadout=EquipmentLoadout(shield=true,armour=true);gear.blocking=true
        var hp=5;game.damage={_,n->hp-=gear.damage(n)}
        advance(game,4000);assertEquals(5,hp)
        gear.blocking=false;advance(game,4800);assertEquals(5,hp)
        advance(game,4800);assertEquals(4,hp)
    }
}
