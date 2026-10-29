package dev.jamescullimore.dontgotobed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.*
import kotlin.random.Random

internal data class RopePoint(val lat: Float, val lon: Float, val row: Float) {
    fun along(axis: WorldAxis) = if (axis == WorldAxis.Latitude) lat else lon
    fun other(axis: WorldAxis) = if (axis == WorldAxis.Latitude) lon else lat
    fun encode() = "${(lat * 1000).toInt()},${(lon * 1000).toInt()},${(row * 1000).toInt()}"
    companion object { fun decode(s: String) = s.split(',').map { it.toInt() / 1000f }.let { RopePoint(it[0], it[1], it[2]) } }
}
internal data class RopeTarget(val kind: String, val id: Long) {
    init { require(kind in listOf("C", "P", "Z", "S", "A")) }
    fun encode() = "$kind,$id"
    companion object { fun decode(s: String) = s.split(',').let { RopeTarget(it[0], it[1].toLong()) } }
}
internal data class RopeLink(val owner: Int, val target: RopeTarget)
internal enum class CatMood { Wander, Nearby, Guide, Sleeping, Flee, Gone }
internal data class GuideCat(val point: RopePoint, val mood: CatMood = CatMood.Wander, val facingRight: Boolean = true, val bedId: Long? = null)
internal fun wrappedDelta(from: Float, to: Float, span: Int): Float = ((to - from + span / 2f) % span + span) % span - span / 2f

/** A rope leaves the visible plane at the target's column; it never draws to an invisible sprite. */
internal fun ropeEndpoint(target: RopePoint, axis: WorldAxis, slice: Int, span: Int): Pair<RopePoint, Boolean> {
    val offPlane = floor(target.other(axis)).toInt().mod(span) != slice.mod(span)
    return (if (!offPlane) target else if (axis == WorldAxis.Latitude) target.copy(lon = slice.toFloat()) else target.copy(lat = slice.toFloat())) to offPlane
}

/** One host owns cat state, attachments, cooldowns and collision-checked tugs. */
internal class CompanionGame(private val map: TileMap, private val random: Random = Random.Default) {
    var cat by mutableStateOf<GuideCat?>(null)
        private set
    var links by mutableStateOf<List<RopeLink>>(emptyList())
        private set
    var isHost = true
    var selfId = 0
    var players: () -> Map<Int, RopePoint> = { emptyMap() }
    var targetPoint: (RopeTarget) -> RopePoint? = { null }
    var hasRope: (Int) -> Boolean = { false }
    var moveTarget: (RopeTarget, RopePoint) -> Unit = { _, _ -> }
    var send: (String) -> Unit = {}
    var notice: (String) -> Unit = {}
    var prepareGuideBed: (Int) -> Boolean = { false }
    private var elapsed = 0L
    private var fleeTravel = 0f
    private val lastPull = mutableMapOf<Int, Long>()
    private var lastRequest = 0L
    private val commands = mutableMapOf<Int, Long>()
    private var previousBed: Long? = null
    private var revision = 0L
    private var receivedRevision = -1L
    private var round = 0L
    private var routeGoal: Pair<Int,Int>? = null
    private var route = emptyList<RopePoint>()

    fun point(target: RopeTarget): RopePoint? = when (target.kind) {
        "C" -> cat?.takeIf { it.mood != CatMood.Gone }?.point
        "P" -> players()[target.id.toInt()]
        else -> targetPoint(target)
    }
    fun distance(a: RopePoint, b: RopePoint): Float = maxOf(abs(wrappedDelta(a.lat,b.lat,map.width)), abs(wrappedDelta(a.lon,b.lon,map.depth)), abs(a.row-b.row))

    private fun guideDistance(a: RopePoint, b: RopePoint): Float {
        val dx = wrappedDelta(a.lat, b.lat, map.width)
        val dz = wrappedDelta(a.lon, b.lon, map.depth)
        return sqrt(dx * dx + dz * dz + (a.row - b.row).pow(2))
    }

    fun reset(roundId: Long) {
        if (!isHost || round == roundId) return
        round = roundId; route = emptyList(); routeGoal = null; cat = null; links = emptyList(); previousBed = null; fleeTravel = 0f; commands.clear(); lastPull.clear()
    }

    fun request(verb: String, target: RopeTarget? = null) {
        if (isHost) command(selfId, verb, target)
        else { lastRequest++; send("ROPE_CMD;$round;$lastRequest;$verb;${target?.encode() ?: "_"}") }
    }

    fun command(actor: Int, verb: String, target: RopeTarget? = null): Boolean {
        if (!isHost) return false
        if (verb == "RELEASE") {
            links = links.filterNot { it.owner == actor || it.target == RopeTarget("P", actor.toLong()) }
            if (cat?.mood == CatMood.Guide && links.none { it.target.kind == "C" }) cat = cat?.copy(mood = CatMood.Nearby)
            publish(); return true
        }
        val owner = players()[actor] ?: return false
        if (!hasRope(actor)) return false
        when (verb) {
            "ATTACH" -> {
                target ?: return false
                val dest = point(target) ?: return false
                if (target == RopeTarget("P", actor.toLong()) || distance(owner,dest) > 5f) return false
                if (links.any { it.target == target && it.owner != actor }) return false
                if (target.kind == "C" && cat?.mood in listOf(CatMood.Flee,CatMood.Gone,CatMood.Sleeping)) return false
                links = links.filterNot { it.owner == actor } + RopeLink(actor,target)
                if (target.kind == "C") {
                    cat = cat?.copy(mood = CatMood.Guide)
                    route = emptyList(); routeGoal = null
                    notice(tr(if (prepareGuideBed(actor)) "Follow the cat to the bed. Stay close." else "The cat is waiting for a clear bed location."))
                } else notice(tr("Rope attached. Pull or release."))
            }
            "PULL" -> {
                val link = links.firstOrNull { it.owner == actor } ?: return false
                if (link.target.kind == "C") return false
                val dest = point(link.target) ?: return false
                if (elapsed - (lastPull[actor] ?: -2000L) < 1500 || distance(owner,dest) > 32f) return false
                lastPull[actor] = elapsed
                // Move at most three blocks, in quarter-block collision steps. Never cross solid terrain.
                var next = dest
                repeat(12) {
                    val dx = wrappedDelta(next.lat,owner.lat,map.width)
                    val dz = wrappedDelta(next.lon,owner.lon,map.depth)
                    val len = sqrt(dx*dx+dz*dz)
                    if (len > 2f) {
                        val candidate = next.copy(lat = wrap(next.lat + dx/len*.25f,map.width), lon = wrap(next.lon+dz/len*.25f,map.depth))
                        if (clear(candidate, 2, 3)) next = candidate
                    }
                }
                if (next != dest) moveTarget(link.target,next)
            }
            else -> return false
        }
        publish(); return true
    }

    private fun wrap(v: Float, span: Int) = (v % span + span) % span
    private fun clear(p: RopePoint, width: Int, height: Int): Boolean {
        for (x in 0 until width) for (z in 0 until width) for (y in 0 until height)
            if (map.getVoxel(floor(p.lat).toInt()+x,floor(p.lon).toInt()+z,floor(p.row).toInt()+y)) return false
        return p.row >= 0 && p.row + height < map.height
    }
    private fun ground(lat: Float, lon: Float, fromRow: Float): Float? {
        val x = floor(lat).toInt(); val z = floor(lon).toInt()
        for (y in minOf(map.height-3, floor(fromRow).toInt()+2) downTo 0) {
            if (map.getVoxel(x,z,y) && !map.getVoxel(x,z,y+1) && !map.getVoxel(x,z,y+2)) return (y+1).toFloat()
        }
        return null
    }
    internal fun findRoute(from: RopePoint, to: RopePoint): List<RopePoint> {
        data class Cell(val x: Int,val z: Int,val y: Int)
        val start=Cell(floor(from.lat).toInt(),floor(from.lon).toInt(),floor(from.row).toInt())
        val gx=floor(to.lat).toInt().mod(map.width);val gz=floor(to.lon).toInt().mod(map.depth)
        fun heuristic(c:Cell)=abs(wrappedDelta(c.x.toFloat(),gx.toFloat(),map.width))+abs(wrappedDelta(c.z.toFloat(),gz.toFloat(),map.depth))
        val open=RouteFrontier<Cell>().also { it.offer(start,heuristic(start)) };val cost=mutableMapOf(start to 0);val parent=mutableMapOf<Cell,Cell>()
        val closed=mutableSetOf<Cell>()
        repeat(5000) {
            val current=open.removeFirst() ?: return emptyList()
            if(current.x==gx && current.z==gz) {
                val result=mutableListOf<RopePoint>();var c=current
                while(c!=start) { result+=RopePoint(c.x.toFloat(),c.z.toFloat(),c.y.toFloat());c=parent.getValue(c) }
                return result.asReversed()
            }
            closed+=current
            for((dx,dz) in listOf(1 to 0,-1 to 0,0 to 1,0 to -1)) {
                val x=(current.x+dx).mod(map.width);val z=(current.z+dz).mod(map.depth)
                val y=ground(x.toFloat(),z.toFloat(),current.y.toFloat())?.toInt() ?: continue
                val next=Cell(x,z,y)
                if(next in closed || !clear(RopePoint(x.toFloat(),z.toFloat(),y.toFloat()),1,2)) continue
                val candidate=cost.getValue(current)+1
                if(candidate < (cost[next] ?: Int.MAX_VALUE)) { cost[next]=candidate;parent[next]=current;open.offer(next,candidate+heuristic(next)) }
            }
        }
        return emptyList()
    }
    private fun step(from: RopePoint, to: RopePoint, amount: Float): RopePoint {
        val goal=floor(to.lat).toInt() to floor(to.lon).toInt()
        if(routeGoal!=goal || route.isEmpty()) { routeGoal=goal;route=findRoute(from,to) }
        while(route.isNotEmpty() && abs(wrappedDelta(from.lat,route.first().lat,map.width))<.12f && abs(wrappedDelta(from.lon,route.first().lon,map.depth))<.12f) route=route.drop(1)
        val waypoint=route.firstOrNull() ?: to
        val dx=wrappedDelta(from.lat,waypoint.lat,map.width);val dz=wrappedDelta(from.lon,waypoint.lon,map.depth)
        // Snap the final fraction to the cell boundary: float wrap rounding
        // can otherwise leave the cat just below the bed's first solid column.
        val lat=if(abs(dx)<=amount) wrap(waypoint.lat,map.width) else wrap(from.lat+dx.coerceIn(-amount,amount),map.width)
        val lon=if(abs(dz)<=amount) wrap(waypoint.lon,map.depth) else wrap(from.lon+dz.coerceIn(-amount,amount),map.depth)
        val row=ground(lat,lon,from.row) ?: return from
        val candidate=RopePoint(lat,lon,row)
        if(clear(candidate,1,2)) return candidate
        route=emptyList()
        return from
    }

    fun tick(deltaMs: Long, bed: HuntBed?, roundId: Long) {
        if (!isHost) return
        reset(roundId); val dt = deltaMs.coerceIn(0,200); elapsed += dt
        val roster = players()
        links = links.filter { it.owner in roster && hasRope(it.owner) && point(it.target) != null }
        if (cat == null && roster.isNotEmpty()) {
            // Sample the map independently, never beside any player's spawn.
            val start = random.nextInt(map.width * map.depth)
            for (offset in 0 until map.width * map.depth) {
                val index = (start + offset) % (map.width * map.depth)
                val lat = (index % map.width).toFloat()
                val lon = (index / map.width).toFloat()
                val row = ground(lat, lon, roster.values.first().row) ?: continue
                val point = RopePoint(lat, lon, row)
                if (clear(point, 1, 2) && roster.values.all { distance(it, point) > 8f }) {
                    cat = GuideCat(point)
                    break
                }
            }
        }
        var c = cat ?: return
        if (c.mood == CatMood.Gone) return
        if (previousBed != null && bed == null && c.bedId == previousBed) {
            c = c.copy(mood = CatMood.Flee); links = links.filterNot { it.target.kind == "C" }; fleeTravel = 0f
        }
        previousBed = bed?.id
        val leader = links.firstOrNull { it.target.kind == "C" }?.owner
        if (c.mood == CatMood.Guide && leader == null) c = c.copy(mood = CatMood.Nearby)
        val nearest = roster.values.minByOrNull { distance(it,c.point) }
        val destination: RopePoint? = when {
            c.mood == CatMood.Flee -> {
                fleeTravel += dt / 1000f * 32f
                if (fleeTravel > 160f) c = c.copy(mood = CatMood.Gone)
                c.point.copy(lat = wrap(c.point.lat + if (c.facingRight) 8f else -8f,map.width))
            }
            c.mood == CatMood.Sleeping -> null
            leader != null && bed != null -> {
                // Use the nearest bed column so a cat approaching on a visible
                // bed slice does not cross into a hidden slice just to sleep.
                fun bedColumn(value: Float, anchor: Int, span: Int): Float =
                    (0..2).map { wrap((anchor + it).toFloat(), span) }
                        .minBy { abs(wrappedDelta(value, it, span)) }
                val approach = roster[leader] ?: c.point
                val goal = RopePoint(
                    bedColumn(approach.lat, bed.latitude, map.width),
                    bedColumn(approach.lon, bed.longitude, map.depth), bed.row+2f)
                c = c.copy(bedId = bed.id, mood = CatMood.Guide)
                if (distance(c.point,goal) < 1.6f && guideDistance(approach, goal) <= 5f) {
                    c = c.copy(point = goal,mood = CatMood.Sleeping)
                    links = links.filterNot { it.target.kind == "C" }
                    notice(tr("The cat is asleep. Rope released. Break the bed for the map."))
                    null
                } else if (guideDistance(approach, c.point) > 5f) approach else goal
            }
            nearest != null -> {
                val near = distance(nearest,c.point) <= 3f
                if (leader == null) c = c.copy(mood = if (near) CatMood.Nearby else CatMood.Wander)
                if (!near) nearest else if (elapsed % 6000 < 1500) nearest.copy(lat = wrap(nearest.lat+2.5f,map.width)) else null
            }
            else -> null
        }
        if (destination != null && c.mood != CatMood.Gone) {
            val next = if (c.mood == CatMood.Flee) c.point.copy(lat=wrap(c.point.lat+(if(c.facingRight) 1 else -1)*dt/1000f*32f,map.width))
                else step(c.point,destination,dt/1000f * if(c.mood==CatMood.Guide) 3f else .8f)
            val owner = leader?.let { roster[it] }
            val bounded = if (c.mood == CatMood.Guide && owner != null &&
                guideDistance(owner, next) > 5f && guideDistance(owner, next) >= guideDistance(owner, c.point)) c.point else next
            val dx = wrappedDelta(c.point.lat,bounded.lat,map.width)
            c = c.copy(point=bounded,facingRight=if(abs(dx)>.001f) dx>0 else c.facingRight)
        }
        cat = c
        publish()
    }

    fun publish() {
        if (!isHost) return
        revision++
        val c = cat
        send("COMPANION;$round;$revision;${if(c==null) "_" else "${c.point.encode()},${c.mood.ordinal},${c.facingRight},${c.bedId ?: -1}"};${links.joinToString("/") { "${it.owner},${it.target.encode()}" }.ifEmpty { "_" }}")
    }
    fun receive(actor: Int, payload: String): Boolean {
        if (!payload.startsWith("COMPANION;") && !payload.startsWith("ROPE_CMD;")) return false
        runCatching {
            val p = payload.split(';')
            if (isHost && p[0]=="ROPE_CMD" && p[1].toLong()==round) {
                val sequence = p[2].toLong()
                if(sequence <= (commands[actor] ?: -1)) return true
                commands[actor]=sequence
                command(actor,p[3],if(p[4]=="_") null else RopeTarget.decode(p[4]))
            } else if(!isHost && actor==0 && p[0]=="COMPANION") {
                val rev=p[2].toLong()
                if(p[1].toLong()==round && rev<=receivedRevision) return true
                val nextCat=if(p[3]=="_") null else p[3].split(',').let { f ->
                    val point=RopePoint.decode(f.take(3).joinToString(","))
                    require(point.lat.isFinite() && point.lon.isFinite() && point.row in 0f..map.height.toFloat())
                    GuideCat(point,CatMood.entries[f[3].toInt()],f[4].toBooleanStrict(),f[5].toLong().takeIf { it>=0 })
                }
                val nextLinks=if(p[4]=="_") emptyList() else p[4].split('/').map { l->l.split(',').let { RopeLink(it[0].toInt(),RopeTarget(it[1],it[2].toLong())) } }
                round=p[1].toLong();receivedRevision=rev;cat=nextCat;links=nextLinks
            }
        }
        return true
    }
}
