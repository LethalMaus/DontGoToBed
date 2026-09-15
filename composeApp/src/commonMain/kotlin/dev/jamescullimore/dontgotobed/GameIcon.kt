package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Small, shared vector drawings: no platform font or emoji dependencies. */
enum class GameIconKind { Settings, Menu, Rotate, Heart, Potion, Arrow, Map, Bag }

@Composable
fun GameIcon(kind: GameIconKind, description: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current, filled: Boolean = true) {
    Canvas(modifier.size(24.dp).semantics { if (description != null) contentDescription = description }) {
        val w = size.width; val h = size.height; val stroke = size.minDimension * .085f
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(tint, Offset(w*x,h*y), Offset(w*x2,h*y2), stroke, StrokeCap.Round)
        fun box(x: Float, y: Float, width: Float, height: Float) = drawRect(tint, Offset(w*x,h*y), Size(w*width,h*height), style = Stroke(stroke))
        when (kind) {
            GameIconKind.Menu -> { line(.2f,.25f,.8f,.25f); line(.2f,.5f,.8f,.5f); line(.2f,.75f,.8f,.75f) }
            GameIconKind.Settings -> { line(.15f,.25f,.85f,.25f); line(.15f,.5f,.85f,.5f); line(.15f,.75f,.85f,.75f); line(.35f,.15f,.35f,.35f); line(.65f,.4f,.65f,.6f); line(.4f,.65f,.4f,.85f) }
            GameIconKind.Rotate -> { line(.15f,.35f,.85f,.35f); line(.85f,.35f,.65f,.15f); line(.85f,.65f,.15f,.65f); line(.15f,.65f,.35f,.85f) }
            GameIconKind.Arrow -> { line(.2f,.8f,.8f,.2f); line(.5f,.2f,.8f,.2f); line(.8f,.2f,.8f,.5f); line(.2f,.6f,.4f,.8f) }
            GameIconKind.Potion -> { box(.35f,.12f,.3f,.15f); line(.4f,.28f,.4f,.42f); line(.6f,.28f,.6f,.42f); drawCircle(tint, w*.28f, Offset(w*.5f,h*.65f), style = Stroke(stroke)); line(.35f,.65f,.65f,.65f) }
            GameIconKind.Map -> { val p=Path().apply { moveTo(w*.1f,h*.2f); lineTo(w*.35f,h*.1f); lineTo(w*.65f,h*.2f); lineTo(w*.9f,h*.1f); lineTo(w*.9f,h*.8f); lineTo(w*.65f,h*.9f); lineTo(w*.35f,h*.8f); lineTo(w*.1f,h*.9f); close() }; drawPath(p,tint,style=Stroke(stroke)); line(.35f,.1f,.35f,.8f); line(.65f,.2f,.65f,.9f) }
            GameIconKind.Bag -> { box(.2f,.3f,.6f,.55f); box(.35f,.1f,.3f,.2f); line(.2f,.55f,.8f,.55f) }
            GameIconKind.Heart -> { val p=Path().apply { moveTo(w*.5f,h*.85f); cubicTo(0f,h*.5f,0f,h*.05f,w*.3f,h*.15f); cubicTo(w*.4f,h*.15f,w*.5f,h*.3f,w*.5f,h*.3f); cubicTo(w*.7f,-h*.05f,w*1.2f,h*.25f,w*.5f,h*.85f); close() }; if(filled) drawPath(p,tint) else drawPath(p,tint,style=Stroke(stroke)) }
        }
    }
}

@Composable
fun BlockShapeIcon(shape: BlockShape) {
    Canvas(Modifier.size(22.dp).semantics { contentDescription = shape.name }) {
        val w=size.width; val h=size.height
        when(shape) {
            BlockShape.Platform -> drawRect(Color.White, Offset(0f,h*.35f), Size(w,h*.3f))
            BlockShape.Pillar -> drawRect(Color.White, Offset(w*.35f,0f), Size(w*.3f,h))
            BlockShape.RampLeft, BlockShape.RampRight -> drawPath(Path().apply { moveTo(0f,h); lineTo(w,h); if(shape==BlockShape.RampLeft) lineTo(0f,0f) else lineTo(w,0f); close() }, Color.White)
            else -> { val scale=if(shape==BlockShape.Single) .5f else 1f; drawRect(Color.White, Offset(w*(1-scale)/2,h*(1-scale)/2), Size(w*scale,h*scale)) }
        }
    }
}
