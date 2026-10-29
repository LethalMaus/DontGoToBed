package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import dontgotobed.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import kotlin.math.PI

internal data class VineArtFrame(val resource:DrawableResource,val frame:OutfitFrame,val gripX:Float,val gripY:Float)

/** The grip is measured in source pixels, so every costume grips the same point along a continuing vine. */
internal fun vineArtFrame(player:Player,outfit:PlayerOutfit):VineArtFrame {
    val refreshed = refreshedCharacterArt(player, outfit)
    if (refreshed != null) {
        val frame = refreshed.frames[5]
        return VineArtFrame(refreshed.resource, frame, refreshed.gripX - frame.x, refreshed.gripY - frame.y)
    }
    val index=when(validOutfit(player,outfit)) { PlayerOutfit.Original->0;PlayerOutfit.Adventure->1;PlayerOutfit.Rugby->2;else->3 }
    val frames=when(player) {
        Player.Leo->listOf(OutfitFrame(255,5,274,610),OutfitFrame(810,5,274,610),OutfitFrame(254,650,282,598),OutfitFrame(805,650,296,595))
        Player.Ian->listOf(OutfitFrame(188,70,318,572),OutfitFrame(793,80,311,562),OutfitFrame(190,672,322,575),OutfitFrame(754,664,372,565))
        Player.Papa->listOf(OutfitFrame(250,30,254,575),OutfitFrame(791,33,260,578),OutfitFrame(253,632,260,576),OutfitFrame(790,645,272,565))
    }
    val grips=when(player) {
        Player.Leo->listOf(493f to 137f,1049f to 137f,502f to 783f,1056f to 780f)
        Player.Ian->listOf(471f to 191f,1071f to 211f,477f to 809f,1087f to 814f)
        Player.Papa->listOf(458f to 122f,1000f to 128f,460f to 730f,1002f to 743f)
    }
    val frame=frames[index];val grip=grips[index]
    return VineArtFrame(when(player) { Player.Leo->Res.drawable.leo_vine;Player.Ian->Res.drawable.ian_vine;Player.Papa->Res.drawable.papa_vine },frame,grip.first-frame.x,grip.second-frame.y)
}

@Composable internal fun VineCharacterArt(player:Player,outfit:PlayerOutfit,width:Dp,height:Dp,facingRight:Boolean,angle:Float=0f,description:String?=null) {
    val art=androidx.compose.runtime.remember(player,outfit) { vineArtFrame(player,outfit) }
    val spriteWidth=height*(art.frame.width.toFloat()/(art.frame.height-art.gripY))
    Image(atlasPainter(art.resource,art.frame),description,
        Modifier.wrapContentSize(Alignment.TopStart,unbounded=true).offset(x=width/2-height*(art.gripX/(art.frame.height-art.gripY)),y=-height*(art.gripY/(art.frame.height-art.gripY)))
            .requiredSize(spriteWidth,height*(art.frame.height/(art.frame.height-art.gripY))).graphicsLayer {
                transformOrigin=TransformOrigin(art.gripX/art.frame.width,art.gripY/art.frame.height)
                scaleX=if(facingRight) 1f else -1f
                rotationZ=-angle*180f/PI.toFloat()
            },contentScale=ContentScale.FillBounds)
}
