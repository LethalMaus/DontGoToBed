package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

@Composable internal fun BoxScope.SwingVineLayer(map:TileMap,player:RopePoint,axis:WorldAxis,unit:Dp,centerX:Dp,riders:List<VineSwing>) {
    Canvas(Modifier.matchParentSize()) {
        val u=unit.toPx()
        SwingVines.swinging.forEach { vine ->
            val fixed=if(axis==WorldAxis.Latitude) vine.longitude else vine.latitude
            if(kotlin.math.abs(map.activeSlice-fixed)>1) return@forEach
            val x=centerX.toPx()+((if(axis==WorldAxis.Latitude) vine.latitude else vine.longitude)-player.along(axis))*u
            if(x < -unit.toPx()*(vine.length+4) || x > size.width+unit.toPx()*(vine.length+4)) return@forEach
            val swing=riders.firstOrNull { it.vine==vine }
            val p=vine.point(swing?.angle ?: 0f,vine.length+3f)
            val endX=centerX.toPx()+(p.along(axis)-player.along(axis)+1)*u
            val start=Offset(x+u,size.height-vine.pivotRow*u)
            val end=Offset(endX,size.height-(p.row+3)*u)
            drawLine(Color(0xFF315532),start,end,u*.28f)
            for(i in 1..12) {
                val t=i/13f;val at=start+(end-start)*t
                drawCircle(Color(0xFF8DBB51),u*.23f,at+Offset(if(i%2==0) u*.25f else -u*.25f,0f))
            }
            drawCircle(Color(0xFFDBC786),u*.35f,end)
        }
    }
}
