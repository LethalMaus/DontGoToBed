package dev.jamescullimore.dontgotobed

import androidx.compose.runtime.*
import kotlin.math.*
import kotlin.random.Random

internal data class PetDog(val point: RopePoint, val owner: Int, val walking: Boolean = false,
    val facingLatitude: Boolean = true, val facingLongitude: Boolean = true)

/** One friendly, non-blocking dog. The host simulates following and replicates it to every player. */
internal class DogGame(private val map: TileMap, private val random: Random = Random.Default) {
    var dog by mutableStateOf<PetDog?>(null)
        private set
    fun restore(saved:PetDog?) { dog=saved }
    var isHost = true
    var selfId = 0
    var players: () -> Map<Int,RopePoint> = { emptyMap() }
    var send: (String) -> Unit = {}
    private var revision = 0L
    private var received = -1L
    private var stuck = 0L
    var found: (Int, RopePoint) -> Unit = { _, _ -> }
    private val discoveries = mutableSetOf<Long>()
    private var discoveryId = 0L
    private fun spawn(): RopePoint? {
        repeat(3000) {
            val p = ground(random.nextInt(map.width).toFloat(), random.nextInt(map.depth).toFloat(), 3f)
            if(p != null && players().values.none { distance(it,p) < 12 }) return p
        }
        return players().values.firstOrNull()?.let(::beside)
    }
    private fun discover(actor: Int) {
        val d = dog ?: return
        if(d.owner >= 0) return
        dog = d.copy(owner = actor)
        discoveryId++
        found(actor,d.point)
        send("DOG_FOUND;$discoveryId;$actor;${d.point.encode()}")
    }
    private fun distance(a:RopePoint,b:RopePoint) = maxOf(abs(wrappedDelta(a.lat,b.lat,map.width)),abs(wrappedDelta(a.lon,b.lon,map.depth)),abs(a.row-b.row))
    private fun ground(lat: Float, lon: Float, row: Float): RopePoint? {
        val x=floor(lat).toInt();val z=floor(lon).toInt()
        if(map.exploration && ExplorationWorld.boundary(x,z)) return null
        for(y in minOf(map.height-3,floor(row).toInt()+1) downTo maxOf(0,floor(row).toInt()-4)) {
            if((0..1).any { dx -> (0..1).any { dz -> map.getVoxel(x+dx,z+dz,y) } } &&
                (0..1).all { dx -> (0..1).all { dz -> (1..2).none { dy -> map.getVoxel(x+dx,z+dz,y+dy) } } }) {
                val p=RopePoint(lat.mod(map.width.toFloat()),lon.mod(map.depth.toFloat()),y+1f)
                if(BiomeHazards.contacts(map,p).any { it.kind != HazardKind.Vine }) return null
                return p
            }
        }
        return null
    }
    private fun beside(owner:RopePoint): RopePoint? {
        for((dx,dz) in listOf(-3 to 0,3 to 0,0 to -3,0 to 3,-2 to -2,2 to 2)) {
            val p=ground(owner.lat+dx,owner.lon+dz,owner.row) ?: continue
            if(!map.exploration || ExplorationWorld.biome(p)==ExplorationWorld.biome(owner)) return p
        }
        return null
    }
    fun call() { if(isHost) follow(selfId) else send("DOG_FOLLOW") }
    private fun follow(actor:Int) {
        val d=dog ?: return;val p=players()[actor] ?: return
        if(distance(p,d.point)<=CatProximity.RADIUS && (!map.exploration || ExplorationWorld.biome(p)==ExplorationWorld.biome(d.point))) {
            if(d.owner < 0) discover(actor) else dog=d.copy(owner=actor);publish()
        }
    }
    fun tick(deltaMs:Long) {
        if(!isHost) return
        val roster=players();if(roster.isEmpty()) return
        if(dog == null) dog = spawn()?.let { PetDog(it,-1) }
        var d=dog ?: return
        if(d.owner < 0) {
            val finder = roster.entries.filter { (!map.exploration || ExplorationWorld.biome(it.value)==ExplorationWorld.biome(d.point)) && distance(it.value,d.point)<=9 }.minByOrNull { distance(it.value,d.point) }
            if(finder != null) { discover(finder.key); d=dog!! }
        }
        val owner=if(d.owner>=0) d.owner.takeIf { it in roster } ?: roster.keys.min() else -1
        val approaching=roster.entries.minBy { distance(d.point,it.value) + if(map.exploration && ExplorationWorld.biome(d.point)!=ExplorationWorld.biome(it.value)) 1000 else 0 }
        var target=roster[owner] ?: approaching.value
        if(owner>=0 && (d.owner!=owner || distance(d.point,target)>24 ||
            (map.exploration && ExplorationWorld.biome(d.point)!=ExplorationWorld.biome(target)))) {
            val safe=beside(target) ?: return
            d=PetDog(safe,owner);stuck=0
        }
        if(owner<0 && map.exploration && ExplorationWorld.biome(d.point)!=ExplorationWorld.biome(target)) {
            val region=ExplorationWorld.biome(d.point)
            val portal=ExplorationWorld.nextPortal(map.portals,region,ExplorationWorld.biome(target)) ?: return
            target=RopePoint(portal.location.latitude.toFloat(),portal.location.longitude.toFloat(),3f)
            if(distance(d.point,target)<2) {
                ExplorationWorld.safeArrival(map,portal.destination)?.let { d=d.copy(point=RopePoint(it.latitude.toFloat(),it.longitude.toFloat(),it.row.toFloat())) }
            }
        }
        val old=d.point
        val dx=wrappedDelta(old.lat,target.lat,map.width);val dz=wrappedDelta(old.lon,target.lon,map.depth)
        var next=old
        if(maxOf(abs(dx),abs(dz))>3) {
            val amount=(deltaMs.coerceIn(0,200)/1000f*(if(owner<0) 1.8f else 36f)).coerceAtMost(maxOf(abs(dx),abs(dz))-2.5f)
            val steps=ceil(amount/.25f).toInt().coerceAtLeast(1)
            repeat(steps) {
                val nx=next.lat+dx.sign*minOf(abs(dx),amount/steps)
                val nz=next.lon+dz.sign*minOf(abs(dz),amount/steps)
                val candidate=ground(nx,nz,next.row) ?: ground(nx,next.lon,next.row) ?: ground(next.lat,nz,next.row)
                if(candidate!=null) next=candidate
            }
            stuck=if(next==old) stuck+deltaMs else 0
            if(stuck>=2000) {
                if(owner>=0) beside(target)?.let { next=it }
                else {
                    // Try a slow sideways step around terrain instead of teleporting an undiscovered dog.
                    val side=if((stuck/2000)%2==0L) 1 else -1
                    ground(next.lat-dz.sign*.18f*side,next.lon+dx.sign*.18f*side,next.row)?.let { next=it }
                }
                if(owner>=0) stuck=0
            }
        }
        dog=d.copy(point=next,owner=owner,walking=next!=old,
            facingLatitude=if(abs(dx)>.1f) dx>0 else d.facingLatitude,
            facingLongitude=if(abs(dz)>.1f) dz>0 else d.facingLongitude)
        publish()
    }
    fun publish() {
        if(!isHost) return
        val d=dog;revision++
        send("DOG;$revision;${d?.let { "${it.point.encode()};${it.owner};${it.walking};${it.facingLatitude};${it.facingLongitude}" } ?: "_"}")
    }
    fun receive(sender:Int,payload:String):Boolean {
        if(payload.startsWith("DOG_FOUND;")) {
            if(!isHost && sender==0) runCatching {
                val p=payload.split(';');val id=p[1].toLong()
                if(discoveries.add(id)) found(p[2].toInt(),RopePoint.decode(p[3]))
            }
            return true
        }
        if(payload=="DOG_FOLLOW") { if(isHost) follow(sender);return true }
        if(!payload.startsWith("DOG;")) return false
        if(isHost || sender!=0) return true
        runCatching {
            val p=payload.split(';');val rev=p[1].toLong();if(rev<=received) return true
            val next=if(p[2]=="_") null else {
                val point=RopePoint.decode(p[2]);require(point.lat in 0f..map.width.toFloat() && point.lon in 0f..map.depth.toFloat() && point.row in 0f..map.height.toFloat())
                PetDog(point,p[3].toInt(),p[4].toBooleanStrict(),p[5].toBooleanStrict(),p[6].toBooleanStrict())
            }
            dog=next;received=rev
        }
        return true
    }
}
