package dev.jamescullimore.dontgotobed

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.painter.*
import androidx.compose.ui.unit.*
import org.jetbrains.compose.resources.*
import dontgotobed.composeapp.generated.resources.*

internal fun shockTint(clock: Long,reduceMotion: Boolean): ColorFilter? =
    if(reduceMotion || clock%600<300) ColorFilter.tint(Color(0xFFFFDF35),BlendMode.SrcAtop) else null

@Composable
internal fun shockedPlayerPainter(player: Player,outfit: PlayerOutfit): Painter {
    val refreshed = refreshedCharacterArt(player, outfit)
    if (refreshed != null) return atlasPainter(refreshed.resource, refreshed.frames[4])
    if(player==Player.Leo && validOutfit(player,outfit)==PlayerOutfit.Rock)
        return atlasPainter(Res.drawable.leo_rock_short_sheet,OutfitFrame(1641,16,471,674))
    val frames=when(player) {
        Player.Leo -> listOf(OutfitFrame(90,6,509,591),OutfitFrame(712,7,434,594),OutfitFrame(106,631,427,601),OutfitFrame(728,630,424,600))
        Player.Papa -> listOf(OutfitFrame(95,36,471,577),OutfitFrame(678,34,477,577),OutfitFrame(90,632,490,596),OutfitFrame(669,634,504,586))
        Player.Ian -> listOf(OutfitFrame(108,34,437,566),OutfitFrame(724,40,430,560),OutfitFrame(110,655,438,557),OutfitFrame(702,641,456,564))
    }
    val index=when(validOutfit(player,outfit)) { PlayerOutfit.Original->0;PlayerOutfit.Adventure->1;PlayerOutfit.Rugby->2;else->3 }
    val resource=when(player) { Player.Leo->Res.drawable.leo_shocked;Player.Ian->Res.drawable.ian_shocked;Player.Papa->Res.drawable.papa_shocked }
    return atlasPainter(resource,frames[index])
}

@Composable
internal fun shockedEnemyPainter(skeleton: Boolean): Painter = atlasPainter(Res.drawable.enemies_shocked,
    if(skeleton) OutfitFrame(939,53,747,806) else OutfitFrame(143,10,703,849))

@Composable
internal fun atlasPainter(resource: DrawableResource,frame: OutfitFrame): Painter {
    val bitmap=imageResource(resource)
    return remember(bitmap,frame) { BitmapPainter(bitmap,IntOffset(frame.x,frame.y),IntSize(frame.width,frame.height),FilterQuality.None) }
}
