package dev.jamescullimore.dontgotobed

import androidx.compose.runtime.*

internal data class ShockTarget(val kind: String, val id: Long)

/** Remaining active-play time, not wall-clock timestamps. Host applies shocks; clients age snapshots. */
internal class ShockGame {
    var isHost = true
    val remaining = mutableStateMapOf<ShockTarget,Int>()
    var send: (String)->Unit = {}
    var clock by mutableLongStateOf(0L)
        private set
    private val playerRecovery=mutableMapOf<Long,Int>()
    private var revision=0L
    private var receivedRevision=-1L
    fun active(kind: String,id: Long)= (remaining[ShockTarget(kind,id)] ?: 0)>0
    fun apply(kind: String,id: Long,duration: Int = 2000) {
        if(!isHost) return
        if(kind=="P" && (active(kind,id) || (playerRecovery[id] ?: 0)>0)) return
        remaining[ShockTarget(kind,id)]=duration.coerceIn(0,6000)
        publish()
    }
    fun tick(ms: Int) {
        val dt=ms.coerceIn(0,200);clock+=dt
        playerRecovery.toMap().forEach { (id,t)->if(t<=dt) playerRecovery.remove(id) else playerRecovery[id]=t-dt }
        var expired=false
        remaining.toMap().forEach { (target,time) ->
            if(time<=dt) { remaining.remove(target);if(target.kind=="P") playerRecovery[target.id]=1000;expired=true } else remaining[target]=time-dt
        }
        if(expired && isHost) publish()
    }
    fun clear() { remaining.clear();playerRecovery.clear();if(isHost) publish() }
    fun publish() {
        if(!isHost) return
        send("SHOCK;${++revision};${remaining.entries.joinToString("/") { "${it.key.kind},${it.key.id},${it.value}" }.ifEmpty { "_" }}")
    }
    fun receive(actor: Int,payload: String): Boolean {
        if(!payload.startsWith("SHOCK;")) return false
        if(isHost || actor!=0) return true
        runCatching {
            val p=payload.split(';');val rev=p[1].toLong()
            if(rev<=receivedRevision) return true
            val next=if(p[2]=="_") emptyMap() else p[2].split('/').associate {
                val f=it.split(',');require(f[0] in listOf("P","Z","S","E"))
                ShockTarget(f[0],f[1].toLong()) to f[2].toInt().coerceIn(0,6000)
            }
            remaining.clear();remaining.putAll(next);receivedRevision=rev
        }
        return true
    }
}
