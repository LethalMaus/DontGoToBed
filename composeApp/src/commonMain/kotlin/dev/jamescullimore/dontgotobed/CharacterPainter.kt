package dev.jamescullimore.dontgotobed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dontgotobed.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.painterResource

internal enum class CharacterPose { Front, Side, Walk }

/** Source rectangles keep the original generated PNGs intact. Each atlas has a shared baseline. */
internal data class OutfitFrame(val x: Int, val y: Int, val width: Int, val height: Int)
internal data class OutfitSheet(val resource: DrawableResource, val frames: List<OutfitFrame>)

private fun sheet(resource: DrawableResource, top: Int, bottom: Int, vararg spans: Pair<Int, Int>) =
    OutfitSheet(resource, spans.map { (left, right) -> OutfitFrame(left, top, right - left, bottom - top) })

private val LeoRugby = sheet(Res.drawable.leo_rugby_sheet, 7, 712, 272 to 605, 901 to 1249, 1543 to 1899)
private val IanRugby = sheet(Res.drawable.ian_rugby_sheet, 55, 686, 308 to 642, 900 to 1246, 1539 to 1887)
private val PapaRugby = sheet(Res.drawable.papa_rugby_sheet, 25, 706, 247 to 597, 993 to 1212, 1569 to 1961)
private val PapaMma = sheet(Res.drawable.papa_mma_gloves_sheet, 6, 713, 227 to 601, 928 to 1154, 1501 to 1918)
private val LeoRock = sheet(Res.drawable.leo_rock_short_sheet, 16, 690, 122 to 456, 647 to 983, 1167 to 1514)
private val IanTrex = sheet(Res.drawable.ian_trex_sheet, 14, 708, 259 to 687, 863 to 1357, 1543 to 2023)

internal fun outfitSheet(player: Player, outfit: PlayerOutfit): OutfitSheet? = when (validOutfit(player, outfit)) {
    PlayerOutfit.Rugby -> when (player) {
        Player.Leo -> LeoRugby
        Player.Ian -> IanRugby
        Player.Papa -> PapaRugby
    }
    PlayerOutfit.Mma -> PapaMma
    PlayerOutfit.Rock -> LeoRock
    PlayerOutfit.Trex -> IanTrex
    else -> null
}

/** Menu, local player, turns and remote players all use this same pose mapping. */
@Composable
internal fun characterPainter(player: Player, outfit: PlayerOutfit, pose: CharacterPose): Painter {
    val refreshed = refreshedCharacterArt(player, outfit)
    if (refreshed != null) {
        // Leo retains his approved standalone portrait; Papa uses the current atlas in every pose.
        if (player == Player.Leo && pose == CharacterPose.Front && validOutfit(player, outfit) == PlayerOutfit.Original)
            return painterResource(characterFront(player, PlayerOutfit.Original))
        return atlasPainter(refreshed.resource, refreshed.frames[pose.ordinal])
    }
    val atlas = outfitSheet(player, outfit)
    if (atlas == null) return painterResource(
        if (pose == CharacterPose.Front) characterFront(player, outfit)
        else characterSide(player, pose == CharacterPose.Walk, outfit)
    )
    val bitmap = imageResource(atlas.resource)
    val frame = atlas.frames[pose.ordinal]
    return remember(bitmap, frame) {
        BitmapPainter(bitmap, IntOffset(frame.x, frame.y), IntSize(frame.width, frame.height), FilterQuality.None)
    }
}

/** Selected Mammy C; pose atlas keeps the reviewed identity in every direction. */
@Composable
internal fun mammyPainter(pose: CharacterPose = CharacterPose.Front): Painter {
    val bitmap = imageResource(Res.drawable.mammy_c_sheet)
    val frame = when (pose) {
        CharacterPose.Front -> OutfitFrame(104, 56, 391, 927)
        CharacterPose.Side -> OutfitFrame(592, 56, 360, 927)
        CharacterPose.Walk -> OutfitFrame(1008, 56, 475, 927)
    }
    return remember(bitmap, frame) {
        BitmapPainter(bitmap, IntOffset(frame.x, frame.y), IntSize(frame.width, frame.height), FilterQuality.None)
    }
}
