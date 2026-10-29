package dev.jamescullimore.dontgotobed

import kotlin.math.*

internal data class SwingVine(val latitude: Float, val longitude: Float, val pivotRow: Float=27f, val length: Float=21f,val swingable:Boolean=true) {
    val topRow get()=pivotRow+if(swingable) 2f else 0f
    val topReach get()=pivotRow-topRow-3f
    fun point(angle: Float,reach:Float=length) = RopePoint(latitude+sin(angle)*reach.coerceAtLeast(0f),longitude,pivotRow-cos(angle)*reach-3)
}
internal data class VineSwing(val vine: SwingVine, val angle: Float=0f, val velocity: Float=0f,val reach:Float=vine.length) {
    val point get() = vine.point(angle,reach)
    val atTop get()=reach<=vine.topReach+.001f
    val atBottom get()=reach>=vine.length-.001f
    fun climb(seconds:Float,input:Float,clear:(RopePoint)->Boolean):VineSwing {
        val next=copy(reach=(reach-input.coerceIn(-1f,1f)*7f*seconds.coerceIn(0f,.1f)).coerceIn(vine.topReach,vine.length))
        return if(clear(next.point)) next else this
    }
    fun advance(seconds: Float, input: Float, clear: (RopePoint)->Boolean): VineSwing {
        if(!vine.swingable)return copy(angle=0f,velocity=0f)
        if(angle==0f && velocity==0f && input==0f)return this
        val dt=seconds.coerceIn(0f,.05f)
        val v=(velocity+(-1.8f*sin(angle)+input.coerceIn(-1f,1f)*1.8f-velocity*.16f)*dt).coerceIn(-1.6f,1.6f)
        val a=if(reach<=2f) angle*(1-dt*8f) else (angle+v*dt).coerceIn(-1.05f,1.05f)
        return if(clear(vine.point(a,reach))) copy(angle=a,velocity=if(a==angle) -v*.3f else v) else copy(velocity=-v*.25f)
    }
}
internal object SwingVines {
    val all = List(3) { SwingVine(ExplorationWorld.origin(ExplorationBiome.Jungle).latitude+90f, longitude(it)) } +
        BiomeHazards.all.filter { it.kind==HazardKind.Vine }.map { SwingVine(it.latitude.toFloat(),it.longitude.toFloat(),(it.row+it.height).toFloat(),(it.height-3).toFloat(),false) }
    val swinging = all.filter { it.swingable }
    private fun longitude(index: Int)=ExplorationWorld.origin(ExplorationBiome.Jungle).longitude+70f+index*96
    fun grab(point:RopePoint,map:TileMap):VineSwing? {
        if(!map.exploration) return null
        return all.firstOrNull { v -> abs(point.lat-v.latitude)<3 && abs(point.lon-v.longitude)<2 && point.row in 2f..v.topRow }?.let {
            VineSwing(it,reach=(it.pivotRow-point.row-3f).coerceIn(it.topReach,it.length))
        }
    }
}
