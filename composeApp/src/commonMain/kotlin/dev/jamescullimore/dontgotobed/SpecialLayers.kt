package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import dontgotobed.composeapp.generated.resources.*
import kotlin.math.*

/** Costume selection is independent of timelines, backgrounds and impact animation. */
@Composable internal fun SpecialCharacterArt(actor:SpecialActor,attack:Boolean,modifier:Modifier) {
    Image(if(attack) attackPlayerPainter(actor.player,actor.outfit) else characterPainter(actor.player,actor.outfit,CharacterPose.Front),
        tr(actor.player.name),modifier,contentScale=ContentScale.Fit)
}

/** Normal and powered-up Grace deliberately share her markings and two-ear silhouette. */
@Composable internal fun graceSpecialPainter(frame:Int) = atlasPainter(Res.drawable.grace_super,
    listOf(OutfitFrame(90,145,520,455),OutfitFrame(795,145,570,455),OutfitFrame(1515,145,570,455))[frame.coerceIn(0,2)])

@Composable internal fun SpecialIntro(event:SpecialEvent?,reduceMotion:Boolean,modifier:Modifier=Modifier) {
    val e=event?.takeIf { it.intro } ?: return
    val t=e.elapsed/1000f
    val accent=when(e.kind) { SpecialKind.Drums->Color(0xFFFFE37C);SpecialKind.Dog->Color(0xFFC7F77D);SpecialKind.Fists->Color(0xFFFFD36B) }
    BoxWithConstraints(modifier.background(Color(0xFF10243A))) {
        SpecialBackdrop(e.kind,t,reduceMotion)
        val enter=(t/.45f).coerceIn(0f,1f)
        if(e.kind==SpecialKind.Dog) {
            // Ian cheers beside Grace; she visibly grows into the attacker during the intro.
            val grow=if(reduceMotion) 1f else ((t-.55f)/1.15f).coerceIn(0f,1f).let { it*it*(3-2*it) }
            SpecialCharacterArt(e.character,true,Modifier.align(Alignment.BottomStart)
                .padding(start=maxWidth*.09f,bottom=maxHeight*.12f).width(maxWidth*.24f).height(maxHeight*.47f)
                .graphicsLayer { rotationZ=if(reduceMotion) 0f else sin(t*5)*5f })
            Image(if(grow<.18f) atlasPainter(Res.drawable.coonhound_sheet,OutfitFrame(0,90,724,575)) else graceSpecialPainter(0),"Grace",
                Modifier.align(Alignment.BottomEnd).padding(end=maxWidth*.06f,bottom=maxHeight*.11f)
                    .width(maxWidth*.64f).height(maxHeight*.71f).graphicsLayer {
                        transformOrigin=TransformOrigin(.6f,1f)
                        scaleX=.26f+grow*.78f;scaleY=scaleX
                        rotationZ=if(reduceMotion) 0f else sin(t*4)*2.5f*grow
                    },contentScale=ContentScale.Fit)
        } else if(e.kind==SpecialKind.Fists) {
            PapaPunchFlurry(e.character,e.elapsed,145,20,350,reduceMotion,
                Modifier.align(Alignment.Center).width(maxWidth*.66f).height(maxHeight*.76f))
        } else {
            val motion=if(reduceMotion) 0f else if(e.kind==SpecialKind.Drums) sin(t*11f)*6f else sin(t*5f)*4f
            SpecialCharacterArt(e.character,t>.7f && (reduceMotion || e.kind!=SpecialKind.Drums || (e.elapsed/220)%2==0),
                Modifier.align(Alignment.Center).fillMaxHeight(if(e.kind==SpecialKind.Drums) .78f else .74f).width(maxWidth*.60f).graphicsLayer {
                    translationX=if(reduceMotion) 0f else (1-enter)*-220
                    translationY=motion
                    rotationZ=motion*.35f
                    scaleX=if(reduceMotion) 1f else .85f+enter*.15f
                    scaleY=scaleX
                })
            if(e.kind==SpecialKind.Drums) DrumKit(Modifier.align(Alignment.BottomCenter).padding(bottom=maxHeight*.10f)
                .width(maxWidth*.55f).height(maxHeight*.48f),t,reduceMotion)
        }
        Column(Modifier.align(Alignment.TopCenter).padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text(tr(e.character.player.name),color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold)
            Text(tr(e.kind.title),color=accent,fontSize=36.sp,fontWeight=FontWeight.Black,
                style=androidx.compose.ui.text.TextStyle(shadow=Shadow(Color.Black,Offset(3f,4f),3f)))
        }
        Text(if(e.kind==SpecialKind.Dog) "GRACE!" else tr("Get ready!"),color=Color.White,modifier=Modifier.align(Alignment.BottomCenter).padding(18.dp),fontSize=20.sp,fontWeight=FontWeight.Bold)
    }
}

/** Alternating jabs, rising punches and a final driving punch; costume art stays swappable. */
@Composable private fun PapaPunchFlurry(actor:SpecialActor,elapsed:Int,beatMs:Int,punches:Int,leadMs:Int,reduceMotion:Boolean,modifier:Modifier) {
    val clock=(elapsed-leadMs).coerceAtLeast(0)
    val beat=(clock/beatMs).coerceAtMost(punches-1)
    val phase=if(clock>=beatMs*punches) 1f else (clock%beatMs).toFloat()/beatMs
    val direction=listOf(1f,-1f,1f,-1f,-1f,1f)[beat%6]
    val tilt=listOf(-5f,5f,-24f,24f,10f,-10f)[beat%6]
    val strike=if(reduceMotion) 0f else sin(((phase-.12f)/.76f).coerceIn(0f,1f)*PI.toFloat()).coerceAtLeast(0f)
    val attacking=reduceMotion || phase in .18f.. .85f
    Box(modifier) {
        Image(if(attacking) attackPlayerPainter(actor.player,actor.outfit) else characterPainter(actor.player,actor.outfit,CharacterPose.Front),
            tr(actor.player.name),Modifier.fillMaxSize().graphicsLayer {
                transformOrigin=TransformOrigin(.5f,.9f)
                scaleX=if(reduceMotion) 1f else -direction
                translationX=if(reduceMotion) 0f else size.width*.055f*strike*direction
                translationY=if(reduceMotion) 0f else -size.height*.025f*strike
                rotationZ=if(reduceMotion) 0f else tilt*strike
            },contentScale=ContentScale.Fit)
        if(!reduceMotion && phase in .25f.. .75f) Canvas(Modifier.fillMaxSize()) {
            val fist=Offset(size.width*(.5f+direction*(.26f+.06f*strike)),size.height*(.43f+tilt/180f))
            val power=if(beat==punches-1) 1.35f else 1f
            for(ray in 0..7) {
                val angle=ray*PI.toFloat()/4
                val vector=Offset(cos(angle),sin(angle))
                drawLine(Color(0xFFFFD36B).copy(alpha=strike),fist+vector*(size.height*.045f),
                    fist+vector*(size.height*.11f*power),size.height*.012f,StrokeCap.Round)
            }
            for(line in -1..1) {
                val end=fist+Offset(-direction*size.width*.10f,line*size.height*.035f)
                drawLine(Color(0xFFFFEDC2).copy(alpha=strike*.8f),end-Offset(direction*size.width*.15f,0f),end,size.height*.008f,StrokeCap.Round)
            }
        }
    }
}

@Composable private fun DrumKit(modifier:Modifier,t:Float,reduceMotion:Boolean) {
    Box(modifier) {
        Image(org.jetbrains.compose.resources.painterResource(Res.drawable.thunder_drumkit),null,
            Modifier.fillMaxSize().graphicsLayer {
                scaleY=if(reduceMotion) 1f else 1f+abs(sin(t*11))*.025f
                rotationZ=if(reduceMotion) 0f else sin(t*11)*.7f
            },contentScale=ContentScale.Fit)
        Canvas(Modifier.fillMaxSize()) {
            // Sticks stay animated separately from the painted instrument and costume.
            val beat=if(reduceMotion) 0f else sin(t*11)*size.height*.07f
            for(side in listOf(-1f,1f)) {
                val a=Offset(size.width*(.5f+side*.12f),size.height*(.06f)+beat*side)
                val b=Offset(size.width*(.5f+side*.08f),size.height*.36f)
                drawLine(Color(0xFF4C291A),a,b,size.height*.018f,StrokeCap.Round)
                drawLine(Color(0xFFECC58A),a,b,size.height*.010f,StrokeCap.Round)
            }
        }
    }
}

@Composable internal fun BoxScope.SpecialAttackLayer(event:SpecialEvent?,map:TileMap,player:RopePoint,axis:WorldAxis,unit:Dp,centerX:Dp,reduceMotion:Boolean) {
    val e=event?.takeIf { !it.intro } ?: return
    if(map.exploration && ExplorationWorld.biome(e.character.point)!=ExplorationWorld.biome(player)) return
    val t=e.effectTime
    fun x(p:RopePoint)=centerX+unit*wrappedDelta(player.along(axis),p.along(axis),map.width)
    if(abs(e.character.point.other(axis)-map.activeSlice)<2) {
        // Match the normal three-cell player height; the cinematic has its own scale.
        val drums = e.kind == SpecialKind.Drums
        if(e.kind==SpecialKind.Fists) PapaPunchFlurry(e.character,t,145,12,0,reduceMotion,
            Modifier.align(Alignment.BottomStart).offset(x=x(e.character.point)-unit*.8f,y=-unit*e.character.point.row)
                .size(unit*3.6f,unit*3f))
        else SpecialCharacterArt(e.character,true,Modifier.align(Alignment.BottomStart)
            .offset(x=x(e.character.point)-unit*.65f,y=-unit*e.character.point.row)
            .size(unit*3.3f,unit*3f)
            .graphicsLayer { rotationZ=if(reduceMotion) 0f else sin(t/90f)*4f })
        if(drums) DrumKit(Modifier.align(Alignment.BottomStart)
            .offset(x=x(e.character.point)-unit*.6f,y=-unit*e.character.point.row)
            .size(unit*3.2f,unit*1.8f),t/1000f,reduceMotion)
    }
    val visible=e.targets.filter { abs(it.point.other(axis)-map.activeSlice)<2 }
    val active=DogAttackSchedule.active(e.targets.size,t)
    e.targets.forEachIndexed { index,target ->
        if(target !in visible) return@forEachIndexed
        val show=e.kind!=SpecialKind.Dog || index==active || (index>=6 && t<300)
        if(!show) return@forEachIndexed
        if(e.kind!=SpecialKind.Drums && target.kind in listOf("Z","S")) Image(shockedEnemyPainter(target.kind=="S"),null,
            Modifier.align(Alignment.BottomStart).offset(x=x(target.point),y=-unit*target.point.row).size(unit*3,unit*3).graphicsLayer {
                alpha=if(e.kind==SpecialKind.Drums) .65f else .8f*(1-t/2000f)
                rotationZ=if(reduceMotion) 0f else sin(t/60f)*8f
            },contentScale=ContentScale.Fit)
        Text(if(e.kind==SpecialKind.Drums) "★" else if(e.kind==SpecialKind.Dog) "−3" else "−4",color=Color(0xFFFFDA72),fontWeight=FontWeight.Bold,
            modifier=Modifier.align(Alignment.BottomStart).offset(x=x(target.point),y=-unit*(target.point.row+4)),fontSize=22.sp)
        if(e.kind==SpecialKind.Dog && index==active) {
            val phase=(t-DogAttackSchedule.starts(e.targets.size)[active])/300f
            val frame=if(reduceMotion) 1 else (phase*3).toInt().coerceIn(0,2)
            Image(graceSpecialPainter(frame),null,
                Modifier.align(Alignment.BottomStart).offset(x=x(target.point)-unit*(if(reduceMotion) 1.5f else 3f-3f*phase),y=-unit*(target.point.row+if(reduceMotion) 0f else sin(phase*PI.toFloat())*.8f))
                    .size(unit*5,unit*3.5f),contentScale=ContentScale.Fit)
        }
    }
    Canvas(Modifier.matchParentSize()) {
        val u=unit.toPx()
        if(e.kind!=SpecialKind.Dog) visible.forEach { target ->
            val c=Offset(x(target.point).toPx()+u,size.height-(target.point.row+1.5f)*u)
            drawCircle(if(e.kind==SpecialKind.Drums) Color(0xFFFFCE66) else Color(0xFFEF975D),u*(if(reduceMotion) 2f else 1+t/900f),c,style=Stroke(u*.12f))
        }
    }
}
