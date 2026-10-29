package dev.jamescullimore.dontgotobed

import androidx.compose.runtime.*
import kotlin.math.abs

internal enum class SpecialKind(val title:String) { Drums("Thunder drums"), Dog("Hound rush"), Fists("Papa power") }
internal fun Player.special()=when(this) { Player.Leo->SpecialKind.Drums;Player.Ian->SpecialKind.Dog;Player.Papa->SpecialKind.Fists }
internal data class SpecialActor(val player:Player,val outfit:PlayerOutfit,val point:RopePoint,val axis:WorldAxis)
internal data class SpecialTarget(val kind:String,val id:Long,val point:RopePoint)
internal data class SpecialView(val left:Float,val right:Float,val bottom:Float,val top:Float) {
    fun valid()=left.isFinite() && right.isFinite() && bottom.isFinite() && top.isFinite() && left in -160f..0f && right in 0f..160f && right-left in 6f..160f && bottom in 0f..54f && top in 0f..54f && top>bottom
}
internal data class SpecialEvent(val id:Long,val actor:Int,val character:SpecialActor,val view:SpecialView,val elapsed:Int=0,val targets:List<SpecialTarget> = emptyList()) {
    val kind get()=character.player.special()
    val intro get()=elapsed<INTRO_MS
    val effectTime get()=(elapsed-INTRO_MS).coerceAtLeast(0)
    companion object { const val INTRO_MS=3500;const val EFFECT_MS=1900 }
}
/** The schedule affects presentation only. Every selected enemy receives one authoritative hit. */
internal object DogAttackSchedule {
    fun starts(enemies:Int):List<Int> {
        val n=enemies.coerceIn(0,6)
        val spacing=when(n) { 0,1->0;2,3->600;4->500;5->400;else->300 }
        return List(n) { it*spacing }
    }
    fun active(enemies:Int,elapsed:Int)=starts(enemies).indexOfFirst { elapsed in it until it+300 }
}
internal class SpecialGame(private val map:TileMap) {
    var isHost=true
    var selfId=0
    var event by mutableStateOf<SpecialEvent?>(null)
        private set
    val cooldowns=mutableStateMapOf<Int,Int>()
    val power=mutableStateMapOf<Int,Int>()
    var actors:()->Map<Int,SpecialActor> = {emptyMap()}
    var enemies:()->List<SpecialTarget> = {emptyList()}
    var impact:(SpecialKind,List<SpecialTarget>)->Unit={_,_->}
    var send:(String)->Unit={}
    private var serial=0L
    private var received=-1L
    private var publishIn=0
    fun use(view:SpecialView) { if(isHost) activate(selfId,view) else send("SPECIAL_USE;${view.left};${view.right};${view.bottom};${view.top}") }
    private fun activate(actor:Int,view:SpecialView) {
        val character=actors()[actor] ?: return
        if(event!=null || (cooldowns[actor] ?: 0)>0 || !view.valid()) return
        event=SpecialEvent(++serial,actor,character,view)
        cooldowns[actor]=30_000
        publish()
    }
    fun tick(ms:Int) {
        val dt=ms.coerceIn(0,100)
        for(m in listOf(cooldowns,power)) m.toMap().forEach { (id,t)->if(t<=dt)m.remove(id) else m[id]=t-dt }
        val e=event ?: return
        val next=e.elapsed+dt
        var update=e.copy(elapsed=next)
        if(isHost && e.elapsed<SpecialEvent.INTRO_MS && next>=SpecialEvent.INTRO_MS) {
            val actor=actors()[e.actor]
            if(actor==null) { event=null;publish();return }
            val chosen=enemies().filter { t ->
                val dx=wrappedDelta(e.character.point.along(e.character.axis),t.point.along(e.character.axis),map.width)
                val dz=abs(wrappedDelta(e.character.point.other(e.character.axis),t.point.other(e.character.axis),map.depth))
                dz<2 && dx>=e.view.left-2 && dx<=e.view.right && t.point.row+3>e.view.bottom && t.point.row<e.view.top &&
                    (!map.exploration || ExplorationWorld.biome(t.point)==ExplorationWorld.biome(e.character.point))
            }.distinctBy { it.kind to it.id }
            update=update.copy(targets=chosen)
            if(e.kind==SpecialKind.Fists) power[e.actor]=8000
            impact(e.kind,chosen)
        }
        event=if(next>=SpecialEvent.INTRO_MS+SpecialEvent.EFFECT_MS) null else update
        publishIn-=dt
        if(isHost && (publishIn<=0 || event==null || e.intro!=update.intro)) { publish();publishIn=100 }
    }
    fun publish() {
        if(!isHost)return
        val e=event
        send("SPECIAL_STATE;${++serial};${cooldowns.entries.joinToString(",") { "${it.key}:${it.value}" }.ifEmpty { "_" }};${power.entries.joinToString(",") { "${it.key}:${it.value}" }.ifEmpty { "_" }};"+
            if(e==null) "_" else "${e.id};${e.actor};${e.character.player.ordinal};${e.character.outfit.wireId};${e.character.point.encode()};${e.character.axis.ordinal};${e.view.left},${e.view.right},${e.view.bottom},${e.view.top};${e.elapsed};${e.targets.joinToString("/") { "${it.kind},${it.id},${it.point.encode()}" }.ifEmpty { "_" }}")
    }
    fun receive(sender:Int,payload:String):Boolean {
        if(payload.startsWith("SPECIAL_USE;")) {
            if(isHost) runCatching { val p=payload.split(';');activate(sender,SpecialView(p[1].toFloat(),p[2].toFloat(),p[3].toFloat(),p[4].toFloat())) }
            return true
        }
        if(!payload.startsWith("SPECIAL_STATE;"))return false
        if(isHost||sender!=0)return true
        runCatching {
            val p=payload.split(';');val revision=p[1].toLong();if(revision<=received)return true
            fun timers(s:String)=if(s=="_") emptyMap() else s.split(',').associate { val f=it.split(':');f[0].toInt() to f[1].toInt().coerceIn(0,30000) }
            val cd=timers(p[2]);val buff=timers(p[3])
            val e=if(p[4]=="_") null else {
                val player=Player.entries[p[6].toInt()]
                val actor=SpecialActor(player,validOutfit(player,decodePlayerOutfit(p[7])),RopePoint.decode(p[8]),WorldAxis.entries[p[9].toInt()])
                val v=p[10].split(',').map(String::toFloat);val view=SpecialView(v[0],v[1],v[2],v[3]);require(view.valid())
                val targets=if(p[12]=="_") emptyList() else p[12].split('/').map { val f=it.split(',');SpecialTarget(f[0],f[1].toLong(),RopePoint.decode(f.drop(2).joinToString(","))) }
                SpecialEvent(p[4].toLong(),p[5].toInt(),actor,view,p[11].toInt().coerceIn(0,5400),targets)
            }
            cooldowns.clear();cooldowns.putAll(cd);power.clear();power.putAll(buff);event=e;received=revision
        }
        return true
    }
}
