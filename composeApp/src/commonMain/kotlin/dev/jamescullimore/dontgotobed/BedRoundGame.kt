package dev.jamescullimore.dontgotobed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

/** Host owns round objects; clients receive snapshots and idempotent, addressed rewards. */
class BedRoundGame(private val map: TileMap, private val bag: PlayerInventory, private val send: (String) -> Unit = {}, private val now: () -> Long = ::currentTimeMillis) {
    var cycle by mutableStateOf(MammyCycle())
    var mammy by mutableStateOf(WorldLayouts.randomMammy(map))
    var bed by mutableStateOf<HuntBed?>(null)
        private set
    var loot by mutableStateOf<List<BedLoot>>(emptyList())
        private set
    var roundId by mutableStateOf(nanoTime())
        private set
    var revision by mutableStateOf(0)
        private set
    var isHost = true
    var selfId = 0
    var position: (Int) -> WorldLocation? = { null }
    var clearEnemies: () -> Unit = {}
    var notify: (String) -> Unit = {}
    private val voxels = HuntBedVoxels(map)
    private var bedSpawned = false
    private var retryAt = 0L
    private val appliedGrants = mutableSetOf<Long>()
    private val returnedGrants = mutableSetOf<Long>()
    private val grantReturns = mutableMapOf<Long, List<BedLoot>>()
    private val issuedGrants = mutableMapOf<Long, Pair<Int, List<BedLoot>>>()
    private val lastHit = mutableMapOf<Int, Long>()

    private fun install(value: HuntBed?) {
        bed = value
        voxels.set(value)
        revision++
    }

    fun advance(delta: Long, forbidden: List<WorldLocation>): Boolean {
        if (!isHost) return false
        val previous = cycle
        cycle = cycle.advance(delta)
        val newSearch = previous.safe && !cycle.safe
        if (newSearch) {
            mammy = WorldLayouts.randomMammy(map, excluding = mammy)
            roundId = nanoTime()
            bedSpawned = false
            retryAt = 0L
            appliedGrants.clear(); grantReturns.clear(); returnedGrants.clear(); issuedGrants.clear(); lastHit.clear()
        }
        if (cycle.hunting && !bedSpawned && cycle.elapsedMs >= retryAt) {
            val candidate = HuntBedPlacement.find(map, forbidden + mammy)
            if (candidate != null) {
                install(candidate)
                bedSpawned = true
                notify("A dangerous bed has appeared. Break it for a map and supplies!")
            }
            retryAt = cycle.elapsedMs + 1000
        }
        return newSearch
    }

    fun rescue(actor: Int = selfId) {
        if (!isHost) { send("RESCUE;$roundId"); return }
        if (cycle.safe) return
        val p = position(actor) ?: return
        if (!near(p, mammy, 4)) return
        cycle = cycle.found()
        install(null)
        loot = loot.filter { it.type != ItemType.Map }
        bag.removeAll(ItemType.Map)
        clearEnemies()
        publish()
        notify("Found Mammy! Five peaceful minutes.")
    }

    fun hit(lat: Int, lon: Int, row: Int, actor: Int = selfId) {
        val b = bed ?: return
        if (!b.contains(map, lat, lon, row)) return
        if (!isHost) { send("HIT;$roundId;${b.id};$lat;$lon;$row"); return }
        val p = position(actor) ?: return
        if (!near(p, WorldLocation(lat, lon, row), 5)) return
        val now = now()
        if (now - (lastHit[actor] ?: 0L) < 200L) return
        lastHit[actor] = now
        if (b.health > 10) install(b.copy(health = b.health - 10))
        else {
            install(null)
            val items = listOf(ItemType.Map to 1, ItemType.Potion to 5, ItemType.Arrow to 10).mapIndexed { index, (type, count) ->
                BedLoot(nanoTime(), type, count, map.wrap(b.latitude + index), b.longitude, b.row)
            }
            grant(actor, items)
        }
        publish()
    }

    fun collect(id: Long, count: Int, actor: Int = selfId) {
        if (!isHost) { send("TAKE;$roundId;$id;$count"); return }
        val item = loot.firstOrNull { it.id == id } ?: return
        val p = position(actor) ?: return
        if (!near(p, WorldLocation(item.latitude, item.longitude, item.row), 2)) return
        val amount = count.coerceIn(0, item.count)
        if (amount == 0) return
        loot = if (amount == item.count) loot.filterNot { it.id == id }
            else loot.map { if (it.id == id) it.copy(count = it.count - amount) else it }
        grant(actor, listOf(item.copy(count = amount)))
        publish()
    }

    private fun grant(actor: Int, items: List<BedLoot>) {
        val id = nanoTime()
        issuedGrants[id] = actor to items
        val payload = "GRANT;$roundId;$id;$actor;${encodeLoot(items)}"
        if (actor == selfId) applyGrant(id, items) else send(payload)
    }

    private fun applyGrant(id: Long, items: List<BedLoot>) {
        if (!appliedGrants.add(id)) {
            if (!isHost) send("OVERFLOW;$roundId;$id;${encodeLoot(grantReturns[id].orEmpty())}")
            return
        }
        val overflow = items.mapNotNull { item ->
            if (item.type == ItemType.Map && cycle.safe) return@mapNotNull null
            val remaining = bag.addExact(item.type, item.count).remaining
            if (remaining > 0) item.copy(count = remaining) else null
        }
        grantReturns[id] = overflow
        notify("Bed supplies received${if (overflow.isEmpty()) "" else "; overflow dropped nearby"}")
        if (isHost) returnOverflow(selfId, id, overflow)
        else send("OVERFLOW;$roundId;$id;${encodeLoot(overflow)}")
    }

    fun replayRewards(actor: Int) {
        if (!isHost) return
        issuedGrants.forEach { (id, grant) ->
            if (grant.first == actor && id !in returnedGrants)
                send("GRANT;$roundId;$id;$actor;${encodeLoot(grant.second)}")
        }
    }

    private fun returnOverflow(actor: Int, id: Long, items: List<BedLoot>) {
        val issued = issuedGrants[id] ?: return
        if (issued.first != actor || !returnedGrants.add(id)) return
        val accepted = items.distinctBy { it.id }.mapNotNull { item ->
            val original = issued.second.firstOrNull { it.id == item.id && it.type == item.type } ?: return@mapNotNull null
            if (original.type == ItemType.Map && cycle.safe) null
            else original.copy(id = nanoTime(), count = item.count.coerceIn(0, original.count)).takeIf { it.count > 0 }
        }
        loot = loot + accepted
        publish()
    }

    private fun near(a: WorldLocation, b: WorldLocation, distance: Int): Boolean {
        fun d(x: Int, y: Int, span: Int): Int { val raw = abs(x - y) % span; return minOf(raw, span - raw) }
        return d(a.latitude, b.latitude, map.width) <= distance &&
            d(a.longitude, b.longitude, map.depth) <= distance && abs(a.row - b.row) <= distance
    }

    fun snapshot(): String {
        val b = bed
        val encodedBed = if (b == null) "_" else "${b.id},${b.latitude},${b.longitude},${b.row},${b.health}"
        return "STATE;$roundId;${cycle.elapsedMs};${cycle.safe};${mammy.latitude},${mammy.longitude},${mammy.row};$encodedBed;${encodeLoot(loot)}"
    }
    fun publish() { if (isHost) send(snapshot()) }

    fun receive(actor: Int, payload: String) {
        // Malformed or stale messages cannot change a subsequent round.
        runCatching {
            val parts = payload.split(';')
            val id = parts[1].toLong()
            if (!isHost && actor == 0 && parts[0] == "STATE") {
                val nextCycle = MammyCycle(parts[2].toLong().coerceAtLeast(0), parts[3].toBooleanStrict())
                val m = parts[4].split(',').map(String::toInt)
                val nextBed = if (parts[5] == "_") null else parts[5].split(',').let {
                    HuntBed(it[0].toLong(), it[1].toInt(), it[2].toInt(), it[3].toInt(), it[4].toInt())
                }
                val nextLoot = decodeLoot(parts[6])
                if (roundId != id) { appliedGrants.clear(); grantReturns.clear() }
                if (roundId != id || nextCycle.safe) bag.removeAll(ItemType.Map)
                if (!cycle.safe && nextCycle.safe) clearEnemies()
                roundId = id
                cycle = nextCycle
                mammy = WorldLocation(m[0], m[1], m[2])
                if (bed != nextBed) install(nextBed)
                loot = nextLoot
                return
            }
            if (id != roundId) return
            if (!isHost) {
                if (actor == 0 && parts[0] == "GRANT" && parts[3].toInt() == selfId)
                    applyGrant(parts[2].toLong(), decodeLoot(parts[4]))
                return
            }
            when (parts[0]) {
                "HIT" -> if (bed?.id == parts[2].toLong()) hit(parts[3].toInt(), parts[4].toInt(), parts[5].toInt(), actor)
                "RESCUE" -> rescue(actor)
                "TAKE" -> collect(parts[2].toLong(), parts[3].toInt(), actor)
                "OVERFLOW" -> returnOverflow(actor, parts[2].toLong(), decodeLoot(parts[3]))
            }
        }
    }

    companion object {
        fun encodeLoot(items: List<BedLoot>): String = items.joinToString("/") {
            "${it.id},${it.type.name},${it.count},${it.latitude},${it.longitude},${it.row}"
        }.ifEmpty { "_" }
        fun decodeLoot(value: String): List<BedLoot> = if (value == "_") emptyList() else value.split('/').map {
            val p = it.split(',')
            val type = ItemType.valueOf(p[1])
            require(type != ItemType.Block)
            BedLoot(p[0].toLong(), type, p[2].toInt().also { count -> require(count in 1..20) }, p[3].toInt(), p[4].toInt(), p[5].toInt())
        }
    }
}
