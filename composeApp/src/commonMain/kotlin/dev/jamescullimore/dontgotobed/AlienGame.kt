package dev.jamescullimore.dontgotobed

import androidx.compose.runtime.*
import kotlin.math.*

internal data class AlienPlayer(val point: RopePoint, val axis: WorldAxis, val facingRight: Boolean)
internal enum class AlienPhase { Hidden, Warning, Attack, Leaving, Defeated }
internal data class AlienState(val point: RopePoint, val phase: AlienPhase, val hp: Int = 3, val facingRight: Boolean = true)

/** One night-time alien. Host owns teleports, hit timing and damage. */
internal class AlienGame(private val map: TileMap) {
    var state by mutableStateOf<AlienState?>(null)
        private set
    var isHost = true
    var players: () -> Map<Int,AlienPlayer> = { emptyMap() }
    var send: (String)->Unit = {}
    var isShocked: ()->Boolean = { false }
    var damage: (Int,Int)->Unit = { _,_-> }
    var notice: (String)->Unit = {}
    private var round = Long.MIN_VALUE
    private var revision = 0L
    private var receivedRevision = -1L
    private var remaining = 2500L
    private var target: Int? = null
    private var nextTarget = 0
    private var elapsed = 0L
    private var lastHit = -1000L
    val targetPoint get() = state?.takeIf { it.phase in listOf(AlienPhase.Warning,AlienPhase.Attack) }?.point

    fun contains(lat: Int, lon: Int, row: Int): Boolean = targetPoint?.let {
        map.wrap(lat-floor(it.lat).toInt()) < 2 && map.wrap(lon-floor(it.lon).toInt(),map.depth) < 2 &&
            row in floor(it.row).toInt() until floor(it.row).toInt()+3
    } ?: false

    private fun spawn(player: AlienPlayer): RopePoint? {
        val p=player.point
        // Search behind, never inside the player or unsupported terrain.
        for (distance in 3..6) {
            val along=map.wrap(floor(p.along(player.axis)).toInt() + if(player.facingRight) -distance else distance)
            val other=floor(p.other(player.axis)).toInt()
            val lat=if(player.axis==WorldAxis.Latitude) along else other
            val lon=if(player.axis==WorldAxis.Longitude) along else other
            for (row in minOf(map.height-3,floor(p.row).toInt()+2) downTo maxOf(1,floor(p.row).toInt()-2)) {
                val clear=(0..1).all { x -> (0..1).all { z ->
                    map.getVoxel(lat+x,lon+z,row-1) && (0..2).none { y -> map.getVoxel(lat+x,lon+z,row+y) }
                } }
                val occupied=players().values.any { otherPlayer ->
                    abs(wrappedDelta(lat.toFloat(),otherPlayer.point.lat,map.width))<2 &&
                        abs(wrappedDelta(lon.toFloat(),otherPlayer.point.lon,map.depth))<2 && abs(row-otherPlayer.point.row)<3
                }
                if(clear && !occupied) return RopePoint(lat.toFloat(),lon.toFloat(),row.toFloat())
            }
        }
        return null
    }

    fun tick(delta: Long, roundId: Long, hunting: Boolean) {
        if(!isHost) return
        if(round!=roundId) {
            round=roundId;state=null;remaining=2500;target=null;elapsed=0;lastHit=-1000;publish()
        }
        if(!hunting) {
            if(state!=null) { state=null;remaining=2500;publish() }
            return
        }
        val dt=delta.coerceIn(0,200);elapsed+=dt
        if(state?.phase==AlienPhase.Defeated) return
        if(isShocked()) return
        remaining-=dt
        if(remaining>0) return
        when(state?.phase ?: AlienPhase.Hidden) {
            AlienPhase.Hidden -> {
                val roster=players().toList().sortedBy { it.first }
                if(roster.isEmpty()) { remaining=1000;return }
                val (id,player)=roster[nextTarget.mod(roster.size)];nextTarget++
                val point=spawn(player) ?: run { remaining=1000;return }
                target=id
                state=AlienState(point,AlienPhase.Warning,state?.hp ?: 3,player.facingRight)
                remaining=1500;notice("Alien behind you — turn and hit!")
            }
            AlienPhase.Warning -> {
                val s=state!!;val player=target?.let { players()[it] }
                if(player!=null && floor(s.point.other(player.axis))==floor(player.point.other(player.axis)) &&
                    abs(wrappedDelta(s.point.along(player.axis),player.point.along(player.axis),map.width))<=3 &&
                    abs(s.point.row-player.point.row)<=2) damage(target!!,1)
                state=s.copy(phase=AlienPhase.Attack);remaining=350
            }
            AlienPhase.Attack -> { state=state!!.copy(phase=AlienPhase.Leaving);remaining=350 }
            AlienPhase.Leaving -> { state=state!!.copy(phase=AlienPhase.Hidden);remaining=2500 }
            AlienPhase.Defeated -> Unit
        }
        publish()
    }

    fun hit(actor: Int, lat: Int, lon: Int, row: Int, strength: Int): Boolean {
        if(!isHost || !contains(lat,lon,row) || elapsed-lastHit<200) return false
        val player=players()[actor] ?: return false
        val p=player.point
        if(floor(p.other(player.axis))!=floor(state!!.point.other(player.axis))) return false
        if(abs(wrappedDelta(p.lat,lat.toFloat(),map.width))>5 || abs(wrappedDelta(p.lon,lon.toFloat(),map.depth))>5 || abs(p.row-row)>5) return false
        lastHit=elapsed
        val s=state!!;val hp=(s.hp-strength.coerceIn(1,4)).coerceAtLeast(0)
        state=s.copy(hp=hp,phase=if(hp==0) AlienPhase.Defeated else AlienPhase.Leaving)
        remaining=350
        notice(if(hp==0) "Alien defeated for this night!" else "Alien hit — watch behind you!")
        publish();return true
    }

    fun arrowHit(lat: Int,lon: Int,row: Int,electric: Boolean): Boolean {
        if(!isHost || !contains(lat,lon,row)) return false
        val s=state!!;val hp=(s.hp-1).coerceAtLeast(0)
        state=s.copy(hp=hp,phase=if(hp==0) AlienPhase.Defeated else if(electric) AlienPhase.Warning else AlienPhase.Leaving)
        remaining=if(electric) 1500 else 350
        publish();return true
    }

    fun specialDamage(amount: Int) {
        if(!isHost) return
        val s=state ?: return
        val hp=(s.hp-amount.coerceAtLeast(0)).coerceAtLeast(0)
        state=s.copy(hp=hp,phase=if(hp==0) AlienPhase.Defeated else AlienPhase.Leaving)
        remaining=350;publish()
    }

    fun tug(point: RopePoint) {
        if(!isHost || targetPoint==null) return
        state=state!!.copy(point=point);publish()
    }

    fun publish() {
        if(!isHost) return
        val s=state;revision++
        send("ALIEN;$round;$revision;${if(s==null) "_" else "${s.point.encode()},${s.phase.ordinal},${s.hp},${s.facingRight}"}")
    }

    fun receive(actor: Int, payload: String): Boolean {
        if(!payload.startsWith("ALIEN;")) return false
        if(isHost || actor!=0) return true
        runCatching {
            val p=payload.split(';');val r=p[1].toLong();val rev=p[2].toLong()
            if(r<round || (r==round && rev<=receivedRevision)) return true
            val next=if(p[3]=="_") null else p[3].split(',').let {
                AlienState(RopePoint.decode(it.take(3).joinToString(",")),AlienPhase.entries[it[3].toInt()],it[4].toInt().coerceIn(0,3),it[5].toBooleanStrict())
            }
            round=r;receivedRevision=rev;state=next
        }
        return true
    }
}
