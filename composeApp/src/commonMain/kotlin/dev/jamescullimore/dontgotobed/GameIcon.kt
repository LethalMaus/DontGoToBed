package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.FilterQuality
import dontgotobed.composeapp.generated.resources.Res
import dontgotobed.composeapp.generated.resources.inventory_backpack
import dontgotobed.composeapp.generated.resources.health_potion
import dontgotobed.composeapp.generated.resources.inventory_map
import dontgotobed.composeapp.generated.resources.inventory_arrow
import dontgotobed.composeapp.generated.resources.inventory_electric_arrow
import org.jetbrains.compose.resources.imageResource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Shared sprites and vector drawings: no platform font or emoji dependencies. */
enum class GameIconKind { Settings, Menu, Rotate, Heart, Potion, Arrow, ElectricArrow, Map, Worlds, Bag, Character, Friends, Flag, Play, Help, Outfit }

@Composable
fun GameIcon(kind: GameIconKind, description: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current, filled: Boolean = true) {
    val sprite = when (kind) {
        GameIconKind.Bag -> Res.drawable.inventory_backpack
        GameIconKind.Potion -> Res.drawable.health_potion
        GameIconKind.Map -> Res.drawable.inventory_map
        GameIconKind.Arrow -> Res.drawable.inventory_arrow
        GameIconKind.ElectricArrow -> Res.drawable.inventory_electric_arrow
        else -> null
    }
    if (sprite != null) {
        // Share the full-colour artwork across the HUD, inventory and world pickups.
        Image(
            bitmap = imageResource(sprite),
            contentDescription = description,
            modifier = modifier.size(24.dp),
            filterQuality = FilterQuality.None
        )
        return
    }
    Canvas(modifier.size(24.dp).semantics { if (description != null) contentDescription = description }) {
        val w = size.width; val h = size.height; val stroke = size.minDimension * .085f
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(tint, Offset(w*x,h*y), Offset(w*x2,h*y2), stroke, StrokeCap.Round)
        when (kind) {
            GameIconKind.Play -> { val p = Path().apply { moveTo(w*.3f,h*.15f); lineTo(w*.85f,h*.5f); lineTo(w*.3f,h*.85f); close() }; drawPath(p,tint) }
            GameIconKind.Character -> { drawCircle(tint,w*.16f,Offset(w*.5f,h*.27f),style=Stroke(stroke)); line(.25f,.85f,.25f,.67f); line(.25f,.67f,.4f,.52f); line(.4f,.52f,.6f,.52f); line(.6f,.52f,.75f,.67f); line(.75f,.67f,.75f,.85f); line(.25f,.85f,.75f,.85f) }
            GameIconKind.Friends -> { drawCircle(tint,w*.13f,Offset(w*.32f,h*.28f),style=Stroke(stroke)); drawCircle(tint,w*.13f,Offset(w*.72f,h*.38f),style=Stroke(stroke)); line(.12f,.78f,.15f,.58f); line(.15f,.58f,.48f,.58f); line(.48f,.58f,.52f,.78f); line(.57f,.7f,.87f,.7f); line(.87f,.7f,.9f,.86f) }
            GameIconKind.Flag -> { line(.25f,.12f,.25f,.9f); val p=Path().apply { moveTo(w*.25f,h*.15f); lineTo(w*.85f,h*.15f); lineTo(w*.67f,h*.36f); lineTo(w*.85f,h*.55f); lineTo(w*.25f,h*.55f); close() }; drawPath(p,tint,style=Stroke(stroke)) }
            GameIconKind.Help -> { drawCircle(tint,w*.4f,center,style=Stroke(stroke)); val p=Path().apply { moveTo(w*.38f,h*.35f); cubicTo(w*.38f,h*.17f,w*.78f,h*.28f,w*.55f,h*.49f); lineTo(w*.5f,h*.58f) }; drawPath(p,tint,style=Stroke(stroke)); drawCircle(tint,stroke*.6f,Offset(w*.5f,h*.74f)) }
            GameIconKind.Outfit -> { val p=Path().apply { moveTo(w*.33f,h*.15f); lineTo(w*.15f,h*.24f); lineTo(w*.05f,h*.48f); lineTo(w*.25f,h*.55f); lineTo(w*.28f,h*.85f); lineTo(w*.72f,h*.85f); lineTo(w*.75f,h*.55f); lineTo(w*.95f,h*.48f); lineTo(w*.85f,h*.24f); lineTo(w*.67f,h*.15f); lineTo(w*.58f,h*.28f); lineTo(w*.42f,h*.28f); close() }; drawPath(p,tint,style=Stroke(stroke)) }
            GameIconKind.Menu -> { line(.2f,.25f,.8f,.25f); line(.2f,.5f,.8f,.5f); line(.2f,.75f,.8f,.75f) }
            GameIconKind.Settings -> { line(.15f,.25f,.85f,.25f); line(.15f,.5f,.85f,.5f); line(.15f,.75f,.85f,.75f); line(.35f,.15f,.35f,.35f); line(.65f,.4f,.65f,.6f); line(.4f,.65f,.4f,.85f) }
            GameIconKind.Rotate -> { line(.15f,.35f,.85f,.35f); line(.85f,.35f,.65f,.15f); line(.85f,.65f,.15f,.65f); line(.15f,.65f,.35f,.85f) }
            GameIconKind.Worlds -> { val p=Path().apply { moveTo(w*.1f,h*.2f); lineTo(w*.35f,h*.1f); lineTo(w*.65f,h*.2f); lineTo(w*.9f,h*.1f); lineTo(w*.9f,h*.8f); lineTo(w*.65f,h*.9f); lineTo(w*.35f,h*.8f); lineTo(w*.1f,h*.9f); close() }; drawPath(p,tint,style=Stroke(stroke)); line(.35f,.1f,.35f,.8f); line(.65f,.2f,.65f,.9f) }
            GameIconKind.Bag, GameIconKind.Potion, GameIconKind.Arrow, GameIconKind.ElectricArrow, GameIconKind.Map -> Unit // Sprites rendered above.
            GameIconKind.Heart -> { val p=Path().apply { moveTo(w*.5f,h*.85f); cubicTo(0f,h*.5f,0f,h*.05f,w*.3f,h*.15f); cubicTo(w*.4f,h*.15f,w*.5f,h*.3f,w*.5f,h*.3f); cubicTo(w*.7f,-h*.05f,w*1.2f,h*.25f,w*.5f,h*.85f); close() }; if(filled) drawPath(p,tint) else drawPath(p,tint,style=Stroke(stroke)) }
        }
    }
}

@Composable
fun BlockShapeIcon(shape: BlockShape) {
    Canvas(Modifier.size(22.dp).semantics { contentDescription = tr(shape.name) }) {
        val w=size.width; val h=size.height
        when(shape) {
            BlockShape.Platform -> drawRect(Color.White, Offset(0f,h*.35f), Size(w,h*.3f))
            BlockShape.Pillar -> drawRect(Color.White, Offset(w*.35f,0f), Size(w*.3f,h))
            BlockShape.RampLeft, BlockShape.RampRight -> drawPath(Path().apply { moveTo(0f,h); lineTo(w,h); if(shape==BlockShape.RampLeft) lineTo(0f,0f) else lineTo(w,0f); close() }, Color.White)
            else -> { val scale=if(shape==BlockShape.Single) .5f else 1f; drawRect(Color.White, Offset(w*(1-scale)/2,h*(1-scale)/2), Size(w*scale,h*scale)) }
        }
    }
}

/** One material illustration shared by the backpack and the selected-item HUD. */
@Composable
internal fun BlockMaterialIcon(material: BlockMaterial, modifier: Modifier = Modifier) {
    Canvas(modifier.size(32.dp)) {
        val w=size.width; val h=size.height; val ink=Color(0xFF10243A)
        fun face(colour: Color, vararg points: Pair<Float,Float>) {
            val path=Path().apply {
                points.forEachIndexed { i,(x,y) -> if(i==0) moveTo(w*x,h*y) else lineTo(w*x,h*y) }; close()
            }
            drawPath(path,colour); drawPath(path,ink,style=Stroke(size.minDimension*.035f))
        }
        val base=material.color
        face(base,.12f to .29f,.5f to .09f,.88f to .29f,.5f to .49f)
        face(lerp(base,Color.Black,.13f),.12f to .29f,.5f to .49f,.5f to .91f,.12f to .71f)
        face(lerp(base,Color.Black,.33f),.5f to .49f,.88f to .29f,.88f to .71f,.5f to .91f)
        fun detail(x:Float,y:Float,x2:Float,y2:Float,colour:Color=ink.copy(alpha=.65f)) =
            drawLine(colour,Offset(w*x,h*y),Offset(w*x2,h*y2),size.minDimension*.035f,StrokeCap.Round)
        when(material) {
            BlockMaterial.Grass -> {
                face(Color(0xFF98643D),.12f to .43f,.5f to .63f,.5f to .91f,.12f to .71f)
                face(Color(0xFF70482E),.5f to .63f,.88f to .43f,.88f to .71f,.5f to .91f)
                detail(.25f,.3f,.4f,.23f,Color(0xFF70C578)); detail(.53f,.34f,.7f,.26f,Color(0xFF70C578))
                detail(.23f,.59f,.29f,.62f); detail(.62f,.74f,.69f,.7f)
            }
            BlockMaterial.Wood -> {
                detail(.25f,.44f,.25f,.7f); detail(.37f,.51f,.37f,.77f)
                detail(.63f,.51f,.63f,.77f); detail(.76f,.44f,.76f,.7f)
                face(Color(0xFFBB8A50),.31f to .29f,.5f to .19f,.69f to .29f,.5f to .39f)
            }
            BlockMaterial.Stone -> {
                detail(.15f,.49f,.48f,.66f); detail(.52f,.66f,.86f,.48f)
                detail(.29f,.38f,.29f,.56f); detail(.7f,.57f,.7f,.79f)
                detail(.4f,.16f,.67f,.3f,Color(0xFFA8B3C3))
            }
        }
    }
}
