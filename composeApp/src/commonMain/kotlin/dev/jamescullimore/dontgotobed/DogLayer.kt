package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.*
import dontgotobed.composeapp.generated.resources.Res
import dontgotobed.composeapp.generated.resources.coonhound_sheet
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.imageResource
import kotlin.math.floor

@Composable
internal fun BoxScope.DogLayer(game:DogGame,map:TileMap,player:RopePoint,axis:WorldAxis,unit:Dp,centerX:Dp,paused:Boolean,reduceMotion:Boolean) {
    val dog=game.dog ?: return
    if(map.exploration && ExplorationWorld.biome(player)!=ExplorationWorld.biome(dog.point)) return
    if(floor(dog.point.other(axis)).toInt().mod(map.width)!=map.activeSlice) return
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(dog.walking,paused,reduceMotion) {
        frame=0
        while(dog.walking && !paused && !reduceMotion) { frame=if(frame==1) 2 else 1;delay(160) }
    }
    val bitmap=imageResource(Res.drawable.coonhound_sheet)
    val width=bitmap.width/3
    val painter=remember(bitmap,frame) { BitmapPainter(bitmap,IntOffset(frame*width,100),IntSize(width,558),FilterQuality.None) }
    val facing=if(axis==WorldAxis.Latitude) dog.facingLatitude else dog.facingLongitude
    Image(painter,tr("Black coonhound — tap to follow you"),contentScale=ContentScale.Fit,alignment=Alignment.BottomCenter,
        modifier=Modifier.align(Alignment.BottomStart).offset(x=centerX+unit*wrappedDelta(player.along(axis),dog.point.along(axis),map.width),y=-unit*dog.point.row)
            .size(unit*2.5f,unit*2.5f).graphicsLayer { scaleX=if(facing) 1f else -1f }.clickable(enabled=!paused) { game.call() })
}
