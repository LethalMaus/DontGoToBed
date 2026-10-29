package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

private val climbingVines = BiomeHazards.all.filter { it.kind==HazardKind.Vine }
private val foregroundHazards = BiomeHazards.all.filter { it.kind!=HazardKind.Vine }

@Composable
internal fun BoxScope.BiomeHazardLayer(map: TileMap, player: RopePoint, axis: WorldAxis,
    unit: Dp, centerX: Dp, timeMs: Long, reduceMotion: Boolean, vinesOnly:Boolean=false) {
    Canvas(Modifier.matchParentSize()) {
        val u=unit.toPx()
        (if(vinesOnly) climbingVines else foregroundHazards).forEach { h ->
            val fixed=if(axis==WorldAxis.Latitude) h.longitude else h.latitude
            val depth=if(axis==WorldAxis.Latitude) h.depth else h.width
            if(map.activeSlice !in fixed until fixed+depth) return@forEach
            val along=if(axis==WorldAxis.Latitude) h.latitude else h.longitude
            val width=if(axis==WorldAxis.Latitude) h.width else h.depth
            val x=centerX.toPx()+(along-player.along(axis))*u
            if(x>size.width || x+width*u<0) return@forEach
            val y=size.height-h.row*u
            if(h.kind!=HazardKind.Vine && (along until along+width).none { map.get(it,h.row-1) }) return@forEach
            when(h.kind) {
                HazardKind.Lava, HazardKind.Water, HazardKind.Quicksand -> {
                    val color=when(h.kind) { HazardKind.Lava->Color(0xFFED682B);HazardKind.Water->Color(0xFF238CBA);else->Color(0xFFC49D54) }
                    drawRect(color,Offset(x,y-u*.18f),Size(width*u,u*.45f))
                    for(i in 0 until width) drawLine(if(h.kind==HazardKind.Lava) Color(0xFFFFCE66) else Color.White.copy(alpha=.45f),
                        Offset(x+i*u,y-u*.08f),Offset(x+(i+.6f)*u,y-u*.08f),u*.1f)
                }
                HazardKind.Fire -> {
                    drawRect(Color(0xFFB8633F),Offset(x,y-u*.25f),Size(width*u,u*.25f))
                    if(h.active(timeMs)) for(i in 0 until width) {
                        val top=y-u*(if(reduceMotion) 5f else 4.5f+(timeMs.mod(400)/400f))
                        val flame=Path().apply { moveTo(x+i*u,y);lineTo(x+(i+.5f)*u,top);lineTo(x+(i+1)*u,y);close() }
                        drawPath(flame,Color(0xFFE87D3E))
                        drawLine(Color(0xFFFFDC87),Offset(x+(i+.5f)*u,y),Offset(x+(i+.5f)*u,top+u*2),u*.2f)
                    }
                }
                HazardKind.Tornado -> for(i in 0..8) {
                    val w=u*(1+i*.5f);val shift=if(reduceMotion) 0f else kotlin.math.sin(timeMs/300f+i)*u*.25f
                    drawOval(Color(0xFFDDD1AF).copy(alpha=.75f),Offset(x+width*u/2-w/2+shift,y-i*u),Size(w,u*.4f),style=Stroke(u*.15f))
                }
                HazardKind.Vine -> {
                    drawLine(Color(0xFF304D30),Offset(x+u,y),Offset(x+u,y-h.height*u),u*.3f)
                    for(i in 0 until h.height step 2) {
                        val side=if(i%4==0) 1 else -1
                        drawOval(Color(0xFF76AD42),Offset(x+u+side*u*.5f,y-(i+1)*u),Size(u*.7f,u*.4f))
                    }
                }
            }
        }
    }
}
