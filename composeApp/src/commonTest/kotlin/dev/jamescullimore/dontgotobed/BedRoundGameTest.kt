package dev.jamescullimore.dontgotobed

import kotlin.test.*

class BedRoundGameTest {
    private fun world() = TileMap(48, 18).also { it.seedGroundBand() }

    @Test fun huntSpawnsOnceRescueClearsMapAndPeaceDelaysMammyRelocation() {
        var now = 1000L
        val bag = PlayerInventory()
        val game = BedRoundGame(world(), bag, now = { now })
        assertNull(game.bed)
        game.advance(300_000, emptyList())
        val bed = assertNotNull(game.bed)
        game.position = { WorldLocation(bed.latitude, bed.longitude, bed.row) }
        repeat(5) { game.hit(bed.latitude, bed.longitude, bed.row + it % 2); now += 250 }
        assertNull(game.bed)
        assertEquals(1, bag.slots.filter { it.type == ItemType.Map }.sumOf { it.count })
        assertEquals(5, bag.slots.filter { it.type == ItemType.Potion }.sumOf { it.count })
        assertEquals(10, bag.slots.filter { it.type == ItemType.Arrow }.sumOf { it.count })
        game.advance(1000, emptyList())
        assertNull(game.bed)
        val previousMammy = game.mammy
        game.position = { previousMammy }
        game.rescue()
        assertTrue(game.cycle.safe)
        assertTrue(bag.slots.none { it.type == ItemType.Map })
        game.advance(299_999, emptyList())
        assertEquals(previousMammy, game.mammy)
        assertTrue(game.cycle.safe)
        game.advance(1, emptyList())
        assertFalse(game.cycle.safe)
        assertNotEquals(previousMammy, game.mammy)
        assertNull(game.bed)
        game.advance(300_000, emptyList())
        assertNotNull(game.bed)
    }

    @Test fun fullBagDropsEverythingAndRescueRemovesOnlyMapLoot() {
        var now = 1000L
        val bag = PlayerInventory().also { it.add(ItemType.Block, 140, BlockMaterial.Wood) }
        val game = BedRoundGame(world(), bag, now = { now })
        game.advance(300_000, emptyList())
        val bed = assertNotNull(game.bed)
        game.position = { WorldLocation(bed.latitude, bed.longitude, bed.row) }
        repeat(5) { game.hit(bed.latitude, bed.longitude, bed.row); now += 250 }
        assertEquals(16, game.loot.sumOf { it.count })
        bag.consume(0, 20)
        val map = game.loot.first { it.type == ItemType.Map }
        game.collect(map.id, 1)
        game.collect(map.id, 1)
        assertEquals(1, bag.slots.filter { it.type == ItemType.Map }.sumOf { it.count })
        game.position = { game.mammy }
        game.rescue()
        assertTrue(game.loot.none { it.type == ItemType.Map })
        assertEquals(15, game.loot.sumOf { it.count })
    }

    @Test fun multiplayerSnapshotsAndDuplicateRewardsAreSafe() {
        var now = 1000L
        val messages = mutableListOf<String>()
        val hostBag = PlayerInventory()
        val clientBag = PlayerInventory()
        val host = BedRoundGame(world(), hostBag, send = { messages += it }, now = { now })
        val client = BedRoundGame(world(), clientBag).also { it.isHost = false; it.selfId = 7 }
        host.advance(300_000, emptyList())
        client.receive(0, host.snapshot())
        assertEquals(host.bed, client.bed)
        val b = assertNotNull(host.bed)
        host.position = { WorldLocation(b.latitude, b.longitude, b.row) }
        repeat(5) { host.receive(7, "HIT;${host.roundId};${b.id};${b.latitude};${b.longitude};${b.row}"); now += 250 }
        val grant = messages.single { it.startsWith("GRANT;") }
        client.receive(0, grant)
        client.receive(0, grant)
        assertEquals(16, clientBag.slots.sumOf { it.count })
        assertEquals(0, hostBag.slots.sumOf { it.count })
        client.receive(0, host.snapshot())
        assertNull(client.bed)
        host.position = { host.mammy }
        host.rescue()
        client.receive(0, host.snapshot())
        assertTrue(client.cycle.safe)
        assertTrue(clientBag.slots.none { it.type == ItemType.Map })
    }
    @Test fun remoteOverflowAndRepeatedPickupConserveRewards() {
        var now = 1000L
        val clientBag = PlayerInventory().also { it.add(ItemType.Block, 140, BlockMaterial.Grass) }
        lateinit var host: BedRoundGame
        lateinit var client: BedRoundGame
        host = BedRoundGame(world(), PlayerInventory(), send = { client.receive(0, it) }, now = { now })
        client = BedRoundGame(world(), clientBag, send = { host.receive(7, it) }).also { it.isHost = false; it.selfId = 7 }
        host.advance(300_000, emptyList())
        host.publish()
        val b = assertNotNull(host.bed)
        host.position = { WorldLocation(b.latitude, b.longitude, b.row) }
        repeat(5) { client.hit(b.latitude, b.longitude, b.row); now += 250 }
        assertEquals(16, host.loot.sumOf { it.count })
        assertEquals(host.loot, client.loot)
        val pickup = host.loot.first { it.type == ItemType.Map }
        clientBag.consume(0, 20)
        client.collect(pickup.id, 1)
        client.collect(pickup.id, 1)
        assertEquals(1, clientBag.slots.filter { it.type == ItemType.Map }.sumOf { it.count })
        assertEquals(15, host.loot.sumOf { it.count })
    }

    @Test fun lateJoinInPeaceHasNoBedOrMapAndStaleCommandsAreIgnored() {
        val host = BedRoundGame(world(), PlayerInventory())
        host.advance(300_000, emptyList())
        val oldBed = assertNotNull(host.bed)
        host.position = { host.mammy }
        host.rescue()
        val bag = PlayerInventory().also { it.add(ItemType.Map) }
        val client = BedRoundGame(world(), bag).also { it.isHost = false; it.selfId = 7 }
        client.receive(0, host.snapshot())
        assertTrue(client.cycle.safe)
        assertNull(client.bed)
        assertTrue(bag.slots.none { it.type == ItemType.Map })
        host.receive(7, "HIT;${host.roundId};${oldBed.id};${oldBed.latitude};${oldBed.longitude};${oldBed.row}")
        assertNull(host.bed)
        assertTrue(host.loot.isEmpty())
        host.receive(7, "HIT;invalid")
        assertTrue(host.cycle.safe)
    }
}
