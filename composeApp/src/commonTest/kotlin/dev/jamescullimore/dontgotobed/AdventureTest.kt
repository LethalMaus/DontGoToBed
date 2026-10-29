package dev.jamescullimore.dontgotobed

import kotlin.test.*

class AdventureTest {
    @Test fun unlimitedNeverForcesNightOrPeacefulRoundAndCanRewardOptionalMammy() {
        var clock=MammyCycle(unlimited=true)
        repeat(200) { clock=clock.advance(60_000) }
        assertFalse(clock.hunting);assertFalse(clock.safe);assertEquals(clock,clock.found())
        val map=TileMap(48,24).apply { seedGroundBand() }
        val bag=PlayerInventory()
        val round=BedRoundGame(map,bag,unlimited=true)
        round.position={round.mammy}
        round.advance(600_000,emptyList());val previous=round.mammy
        round.rescue()
        assertFalse(round.cycle.safe);assertFalse(round.cycle.hunting)
        assertNotEquals(previous,round.mammy)
        assertEquals("true", round.snapshot().split(';')[7])
        assertEquals(previous, round.celebrationLocation)
        assertEquals(3200L, round.celebrationRemainingMs)
        val rewardCount = bag.slots.sumOf { it.count }
        round.rescue()
        assertEquals(rewardCount, bag.slots.sumOf { it.count })
        round.advance(3200, emptyList())
        assertNull(round.celebrationLocation)
    }
    @Test fun swingMovesOnAnArcAndStopsAtSolidTerrain() {
        val vine=SwingVine(90f,70f)
        var swing=VineSwing(vine)
        repeat(80) { swing=swing.advance(.016f,1f) { true } }
        assertTrue(swing.point.lat>vine.latitude+5)
        assertTrue(swing.point.row>3)
        val before=swing.point
        swing=swing.advance(.016f,1f) { false }
        assertEquals(before,swing.point)
        val fixed=VineSwing(SwingVine(90f,70f,24f,18f,false))
        assertEquals(fixed,fixed.advance(.016f,1f) { true })
        assertTrue(fixed.climb(.016f,1f) { true }.point.row>fixed.point.row)
        val map=TileMap(576,54,exploration=true)
        assertNotNull(SwingVines.grab(SwingVines.all.first().point(0f),map))
        assertNull(SwingVines.grab(RopePoint(0f,0f,3f),map))
    }
    @Test fun swingingVineCanBeClimbedBothWaysWithoutPassingThroughTerrainOrOffItsEnds() {
        var swing=VineSwing(SwingVine(90f,70f),angle=.3f)
        val bottom=swing.point
        repeat(100) { swing=swing.climb(.016f,1f) { true }.advance(.016f,0f) { true } }
        assertTrue(swing.point.row>bottom.row+7)
        assertTrue(swing.reach<12f)
        val before=swing
        assertEquals(before,swing.climb(.1f,1f) { false })
        repeat(500) { swing=swing.climb(.016f,1f) { true } }
        assertEquals(swing.vine.topReach,swing.reach)
        assertTrue(swing.atTop)
        repeat(500) { swing=swing.climb(.016f,-1f) { true } }
        assertEquals(swing.vine.length,swing.reach)
        assertEquals(swing.vine.point(swing.angle),swing.point)
    }
    @Test fun allDogAnimationSchedulesStayBelowTwoSecondsAndNeverExceedSix() {
        for(n in 0..100) {
            val starts=DogAttackSchedule.starts(n)
            assertEquals(n.coerceAtMost(6),starts.size)
            assertTrue(starts.zipWithNext().all { (a,b)->b-a>=300 })
            assertTrue((starts.lastOrNull() ?: 0)+300<=2000)
        }
        assertEquals(listOf(0,600,1200),DogAttackSchedule.starts(3))
        assertEquals(listOf(0,500,1000,1500),DogAttackSchedule.starts(4))
        assertEquals(listOf(0,400,800,1200,1600),DogAttackSchedule.starts(5))
    }
    @Test fun specialHitsEveryVisibleEnemyOnceAndPreservesCostumeInLateJoinState() {
        val map=TileMap(576,54,exploration=true)
        val actor=SpecialActor(Player.Ian,PlayerOutfit.Trex,RopePoint(50f,10f,3f),WorldAxis.Latitude)
        val targets=(0..8).map { SpecialTarget("Z",it.toLong(),RopePoint(52f+it,10f,3f)) }
        var hits=0;var hitTargets=emptyList<SpecialTarget>();val messages=mutableListOf<String>()
        val host=SpecialGame(map).apply {
            actors={mapOf(0 to actor)}
            enemies={targets+targets.first()+SpecialTarget("S",99,RopePoint(54f,298f,3f))+SpecialTarget("S",100,RopePoint(54f,20f,3f))}
            impact={_,t->hits++;hitTargets=t};send={messages+=it}
        }
        host.use(SpecialView(-20f,30f,0f,40f))
        host.use(SpecialView(-20f,30f,0f,40f))
        repeat(34) { host.tick(100) };assertEquals(0,hits)
        host.tick(100);assertEquals(1,hits);assertEquals(targets,hitTargets)
        val client=SpecialGame(map).apply { isHost=false;impact={_,_->fail("Client cannot deal damage")} }
        client.receive(2,messages.last());assertNull(client.event)
        client.receive(0,messages.last());assertEquals(PlayerOutfit.Trex,client.event?.character?.outfit)
        repeat(20) { host.tick(100);client.tick(100) };assertEquals(1,hits);assertNull(host.event)
        host.use(SpecialView(-20f,30f,0f,40f));assertNull(host.event)
        repeat(250) { host.tick(100) }
        host.use(SpecialView(-500f,500f,0f,54f));assertNull(host.event)
        host.use(SpecialView(-20f,30f,0f,40f));assertNotNull(host.event)
    }
}
