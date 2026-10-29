package dev.jamescullimore.dontgotobed

import kotlin.test.*
import kotlin.random.Random

class CompanionGameTest {
    private fun world()=TileMap(48,18).also { it.seedGroundBand() }
    @Test fun catSpawnsAtVariedMapLocationsAwayFromEveryPlayer() {
        val locations = mutableSetOf<RopePoint>()
        repeat(12) { seed ->
            val game = CompanionGame(world(), Random(seed))
            val players = mapOf(0 to RopePoint(3f, 10f, 3f), 1 to RopePoint(30f, 30f, 3f))
            game.players = { players }
            game.tick(0, null, 1)
            val cat = assertNotNull(game.cat)
            assertTrue(players.values.all { game.distance(it, cat.point) > 8f })
            locations += cat.point
        }
        assertTrue(locations.size > 8)
    }

    @Test fun guideWaitsWithinFiveBlocksAndResumesWhenPlayerFollows() {
        val map = world()
        val game = CompanionGame(map, Random(7))
        var player = RopePoint(3f, 10f, 3f)
        game.players = { mapOf(0 to player) }; game.hasRope = { true }
        game.tick(0, null, 1)
        player = game.cat!!.point
        val start = player
        val bed = HuntBed(71, map.wrap(start.lat.toInt() + 17), map.wrap(start.lon.toInt() + 12), 3)
        HuntBedVoxels(map).set(bed)
        assertTrue(game.command(0, "ATTACH", RopeTarget("C", 0)))
        fun separation(): Float {
            val p = game.cat!!.point
            val dx = wrappedDelta(player.lat, p.lat, map.width)
            val dz = wrappedDelta(player.lon, p.lon, map.depth)
            return kotlin.math.sqrt(dx * dx + dz * dz + (player.row - p.row) * (player.row - p.row))
        }
        repeat(150) { game.tick(200, bed, 1); assertTrue(separation() <= 5.001f) }
        val waiting = game.cat!!.point
        repeat(20) { game.tick(200, bed, 1) }
        assertEquals(waiting, game.cat!!.point)
        assertEquals(CatMood.Guide, game.cat!!.mood)
        repeat(500) {
            player = game.cat!!.point.copy(lat = game.cat!!.point.lat - 2)
            game.tick(100, bed, 1)
            assertTrue(separation() <= 5.001f)
        }
        assertEquals(CatMood.Sleeping, game.cat!!.mood)
        assertTrue(game.links.isEmpty())
    }

    @Test fun ropingCatStartsBedQuestBeforeNightfall() {
        val map = world()
        var player = RopePoint(3f, 10f, 3f)
        val round = BedRoundGame(map, PlayerInventory(14))
        round.position = { WorldLocation(player.lat.toInt(), player.lon.toInt(), 3) }
        val game = CompanionGame(map, Random(7))
        game.players = { mapOf(0 to player) }; game.hasRope = { true }
        game.prepareGuideBed = round::ensureGuideBed
        game.tick(100, null, round.roundId)
        assertNull(round.bed)
        player = game.cat!!.point.copy(lat = game.cat!!.point.lat - 2)
        assertTrue(game.command(0, "ATTACH", RopeTarget("C", 0)))
        assertNotNull(round.bed)
        assertFalse(round.cycle.hunting)
        repeat(400) {
            player = game.cat!!.point.copy(lat = game.cat!!.point.lat - 2)
            game.tick(100, round.bed, round.roundId)
        }
        assertEquals(CatMood.Sleeping, game.cat!!.mood)
        assertTrue(game.links.isEmpty())
    }

    @Test fun catApproachesGuidesSleepsReleasesAndFleesAfterBedBreaks() {
        val map=world();val game=CompanionGame(map, Random(7))
        var player=RopePoint(3f,10f,3f)
        game.players={mapOf(0 to player)};game.hasRope={true}
        game.tick(100,null,1)
        assertEquals(CatMood.Wander,game.cat!!.mood)
        repeat(1000) { game.tick(100,null,1) }
        assertEquals(CatMood.Nearby,game.cat!!.mood)
        assertTrue(game.command(0,"ATTACH",RopeTarget("C",0)))
        val bed=HuntBed(42,15,12,3);HuntBedVoxels(map).set(bed)
        repeat(300) {
            player=game.cat!!.point.copy(lat=game.cat!!.point.lat-2)
            game.tick(100,bed,1)
        }
        assertEquals(CatMood.Sleeping,game.cat!!.mood)
        assertEquals(5f,game.cat!!.point.row)
        assertTrue(game.links.isEmpty())
        game.tick(100,null,1)
        assertEquals(CatMood.Flee,game.cat!!.mood)
        repeat(1000) { game.tick(100,null,1) }
        assertEquals(CatMood.Gone,game.cat!!.mood)
    }
    @Test fun sleepingCatStaysOnTheApproachSliceForBothWorldAxes() {
        for (axis in WorldAxis.entries) {
            val map=world(); val game=CompanionGame(map, Random(7))
            val player=if(axis==WorldAxis.Latitude) RopePoint(12f,10f,3f) else RopePoint(15f,7f,3f)
            game.players={mapOf(0 to player)};game.hasRope={true}
            val bed=HuntBed(43,15,10,3);HuntBedVoxels(map).set(bed)
            game.tick(100,bed,1)
            repeat(1000) { game.tick(100,bed,1) }
            assertTrue(game.command(0,"ATTACH",RopeTarget("C",0)))
            repeat(150) { game.tick(100,bed,1) }
            assertEquals(CatMood.Sleeping,game.cat!!.mood,"$axis ${game.cat}")
            assertEquals(player.other(axis),game.cat!!.point.other(axis))
            assertTrue(game.links.isEmpty())
        }
    }
    @Test fun tugsAreBoundedThrottledAndEitherPlayerCanRelease() {
        val map=world();val game=CompanionGame(map, Random(7))
        var target=RopePoint(7f,10f,3f)
        game.players={mapOf(0 to RopePoint(3f,10f,3f),1 to target)};game.hasRope={true}
        game.moveTarget={_,p->target=p};game.tick(100,null,1)
        assertTrue(game.command(0,"ATTACH",RopeTarget("P",1)))
        assertTrue(game.command(0,"PULL"));assertEquals(5f,target.lat)
        assertFalse(game.command(0,"PULL"))
        assertTrue(game.command(1,"RELEASE"));assertTrue(game.links.isEmpty())
    }
    @Test fun ropeNeedsEquipmentAndReachAndDoesNotPullThroughSolidBlocks() {
        val map=world();val game=CompanionGame(map, Random(7));var destination=RopePoint(8f,10f,3f)
        game.players={mapOf(0 to RopePoint(3f,10f,3f),1 to destination)}
        game.hasRope={false};game.tick(100,null,1)
        assertFalse(game.command(0,"ATTACH",RopeTarget("P",1)))
        game.hasRope={true};assertTrue(game.command(0,"ATTACH",RopeTarget("P",1)))
        map.setActiveSlice(WorldAxis.Latitude,6,10);map.placePiece(6,3,BlockMaterial.Stone,BlockShape.Pillar)
        game.moveTarget={_,p->destination=p}
        game.command(0,"PULL");assertTrue(destination.lat>=7f)
        destination=RopePoint(25f,10f,3f);game.command(0,"RELEASE")
        assertFalse(game.command(0,"ATTACH",RopeTarget("P",1)))
    }
    @Test fun hostSnapshotsSupportLateJoinAndRejectClientSnapshots() {
        val host=CompanionGame(world());val client=CompanionGame(world()).also { it.isHost=false }
        var snapshot="";host.send={snapshot=it};host.players={mapOf(0 to RopePoint(3f,10f,3f))};host.hasRope={true}
        host.tick(100,null,1);host.command(0,"ATTACH",RopeTarget("C",0))
        client.receive(0,snapshot)
        assertEquals(host.cat!!.point.lat,client.cat!!.point.lat,.0011f)
        assertEquals(host.cat!!.mood,client.cat!!.mood);assertEquals(host.links,client.links)
        val before=client.cat;client.receive(2,"COMPANION;1;999;_;_");assertEquals(before,client.cat)
        host.receive(2,"COMPANION;1;999;_;_");assertNotNull(host.cat)
    }
    @Test fun dimensionalRopeEndsAtSwitchColumn() {
        val target=RopePoint(12f,20f,3f)
        val (end,off)=ropeEndpoint(target,WorldAxis.Latitude,10,48)
        assertTrue(off);assertEquals(12f,end.lat);assertEquals(10f,end.lon)
        assertFalse(ropeEndpoint(target,WorldAxis.Longitude,12,48).second)
    }
    @Test fun catRoutesAroundWallsAndRopesReleaseWhenOwnerUnequips() {
        val map=world();val game=CompanionGame(map, Random(7));var player=RopePoint(3f,10f,3f)
        game.players={mapOf(0 to player)};var equipped=true;game.hasRope={equipped}
        game.tick(100,null,1);player=game.cat!!.point.copy(lat=game.cat!!.point.lat-2);assertTrue(game.command(0,"ATTACH",RopeTarget("C",0)))
        for(z in 8..12) { map.setActiveSlice(WorldAxis.Latitude,9,z);map.placeBlock(9,3,1,5,BlockMaterial.Stone.color,material=BlockMaterial.Stone) }
        val bed=HuntBed(3,15,10,3);HuntBedVoxels(map).set(bed)
        repeat(500) { player=game.cat!!.point.copy(lat=game.cat!!.point.lat-2);game.tick(100,bed,1) }
        assertEquals(CatMood.Sleeping,game.cat!!.mood)
        game.reset(2);game.tick(100,null,2);player=game.cat!!.point
        assertTrue(game.command(0,"ATTACH",RopeTarget("C",0)))
        equipped=false;game.tick(100,null,2);assertTrue(game.links.isEmpty())
    }

    @Test fun equipmentUsesIndividualSlotsWithoutChangingMaterialStacks() {
        val bag=PlayerInventory(capacity=3)
        assertEquals(PlayerInventory.Added(2,0),bag.addExact(ItemType.Rope,2))
        assertEquals(listOf(1,1,0),bag.slots.map { it.count })
        assertEquals(1,bag.roomFor(ItemType.Rope))
        assertEquals(PlayerInventory.Added(20,1),bag.addExact(ItemType.Block,21,BlockMaterial.Wood))
    }

    @Test fun equipmentSlotsAndProtectionDoNotStackOrResetOnToggle() {
        var e=EquipmentLoadout().toggle(ItemType.SpeedBoots).toggle(ItemType.JumpBoots)
        assertEquals(ItemType.JumpBoots,e.boots);assertEquals(1f,e.speedMultiplier);assertEquals(6f,e.jumpHeight)
        e=e.toggle(ItemType.Gloves).toggle(ItemType.Rope);assertEquals(ItemType.Rope,e.hand);assertEquals(1,e.punchDamage)
        assertEquals(e,EquipmentLoadout.decode(e.encode()))
        val equipment=PlayerEquipment();equipment.loadout=e.copy(armour=true)
        assertEquals(0,equipment.damage(1));equipment.loadout=e;equipment.loadout=e.copy(armour=true)
        assertEquals(1,equipment.damage(1))
        equipment.loadout=e.copy(shield=true);equipment.blocking=true;assertEquals(0,equipment.damage(2))
    }
}
