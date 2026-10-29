package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.*

/** Comic-book spectacle uses continuous motion, never alternating full-screen flashes. */
@Composable internal fun SpecialBackdrop(kind:SpecialKind,time:Float,reduceMotion:Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        val t=if(reduceMotion) 0f else time
        val w=size.width;val h=size.height;val m=size.minDimension
        val c=Offset(w*(if(kind==SpecialKind.Dog) .62f else .5f),h*.53f)
        val dark=when(kind) { SpecialKind.Drums->Color(0xFF251344);SpecialKind.Dog->Color(0xFF062E35);SpecialKind.Fists->Color(0xFF491333) }
        val bright=when(kind) { SpecialKind.Drums->Color(0xFFE59528);SpecialKind.Dog->Color(0xFF73AE28);SpecialKind.Fists->Color(0xFFE85124) }
        val accent=when(kind) { SpecialKind.Drums->Color(0xFFFFE37C);SpecialKind.Dog->Color(0xFFC7F77D);SpecialKind.Fists->Color(0xFFFFD36B) }
        drawRect(Brush.radialGradient(listOf(bright,dark),center=c,radius=size.maxDimension*.7f))
        // Oversized radial wedges turn slowly behind the character rather than blinking.
        for(i in 0 until 22) {
            val angle=i*2*PI.toFloat()/22+t*.12f
            fun at(a:Float,r:Float)=c+Offset(cos(a)*r,sin(a)*r)
            val p=Path().apply { val a=at(angle,m*.08f);moveTo(a.x,a.y);val b=at(angle-.035f,w*1.5f);lineTo(b.x,b.y);val d=at(angle+.08f,w*1.5f);lineTo(d.x,d.y);close() }
            drawPath(p,if(i%2==0) accent.copy(alpha=.28f) else dark.copy(alpha=.55f))
        }
        // Halftone dots and thick panel corners give the burst a comic-book finish.
        val step=m/24
        for(row in 0..24) for(col in 0..(w/step).toInt()) {
            val x=(col+.5f*(row%2))*step;val y=row*step
            if(abs(x-c.x)>m*.38f) drawCircle(dark.copy(alpha=.25f),step*.13f,Offset(x,y))
        }
        when(kind) {
            SpecialKind.Drums -> {
                for(side in listOf(-1f,1f)) {
                    val x=c.x+side*w*.3f
                    val p=Path().apply { moveTo(x,h*.08f);lineTo(x-side*w*.10f,h*.35f);lineTo(x+side*w*.02f,h*.34f);lineTo(x-side*w*.06f,h*.70f);lineTo(x+side*w*.15f,h*.28f);lineTo(x+side*w*.02f,h*.29f);close() }
                    drawPath(p,dark,style=Stroke(m*.025f));drawPath(p,accent)
                }
                for(i in 0..2) {
                    val p=if(reduceMotion) .3f else ((t*.55f+i/3f)%1f)
                    drawCircle(accent.copy(alpha=(1-p)*.55f),m*(.18f+p*.75f),c,style=Stroke(m*.009f))
                }
            }
            SpecialKind.Dog -> {
                // Three giant claw tears, fanning out as Grace powers up.
                val reveal=if(reduceMotion) 1f else ((t-.65f)/.8f).coerceIn(0f,1f)
                for(i in 0..2) {
                    val x=w*(.32f+i*.17f)
                    val p=Path().apply { moveTo(x+w*.2f,h*.13f);lineTo(x-w*.10f,h*.80f);lineTo(x-w*.015f,h*.44f);lineTo(x+w*.16f,h*.12f);close() }
                    drawPath(p,dark,style=Stroke(m*.025f),alpha=reveal)
                    drawPath(p,accent.copy(alpha=.68f*reveal))
                }
            }
            SpecialKind.Fists -> {
                for(i in 0..9) {
                    val a=i*PI.toFloat()/5+t*.1f
                    val r=m*(.43f+if(reduceMotion) 0f else sin(t*3+i)*.025f)
                    val at=c+Offset(cos(a)*r,sin(a)*r)
                    val p=Path().apply { moveTo(at.x,at.y-m*.08f);lineTo(at.x+m*.025f,at.y-m*.025f);lineTo(at.x+m*.07f,at.y);lineTo(at.x+m*.02f,at.y+m*.02f);lineTo(at.x,at.y+m*.08f);lineTo(at.x-m*.02f,at.y+m*.02f);lineTo(at.x-m*.07f,at.y);lineTo(at.x-m*.02f,at.y-m*.02f);close() }
                    drawPath(p,accent)
                }
            }
        }
        // Ink frame stays outside the action; it does not flash or hide the costume.
        drawRect(dark.copy(alpha=.85f),style=Stroke(m*.045f))
    }
}
