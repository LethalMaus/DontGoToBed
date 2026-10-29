package dev.jamescullimore.dontgotobed

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dontgotobed.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.imageResource
import kotlin.math.roundToInt

internal data class ActionFrame(val crop: OutfitFrame, val anchorX: Float, val handX: Float = 0f, val handY: Float = 0f, val bodyHeight: Float = crop.height.toFloat())
internal data class ActionArt(val resource: DrawableResource, val consume: ActionFrame, val draw: ActionFrame)

internal val actionArt = mapOf(
    (Player.Ian to PlayerOutfit.Original) to ActionArt(Res.drawable.ian_original_action_poses, ActionFrame(OutfitFrame(119, 21, 360, 682), 310.0f, 180.0f, 263.0f), ActionFrame(OutfitFrame(1031, 21, 544, 682), 1420.0f, bodyHeight = 682.0f)),
    (Player.Leo to PlayerOutfit.Original) to ActionArt(Res.drawable.leo_original_action_poses, ActionFrame(OutfitFrame(127, 48, 341, 628), 300.0f, 190.0f, 275.0f), ActionFrame(OutfitFrame(1071, 55, 559, 622), 1440.0f, bodyHeight = 622.0f)),
    (Player.Papa to PlayerOutfit.Original) to ActionArt(Res.drawable.papa_original_action_poses, ActionFrame(OutfitFrame(76, 72, 372, 597), 275.0f, 180.0f, 220.0f), ActionFrame(OutfitFrame(1030, 66, 552, 603), 1430.0f, bodyHeight = 534.0f)),
    (Player.Ian to PlayerOutfit.Adventure) to ActionArt(Res.drawable.ian_adventure_action_poses, ActionFrame(OutfitFrame(295, 45, 414, 788), 487.5f, 350.0f, 310.0f), ActionFrame(OutfitFrame(951, 48, 609, 785), 1367.5f, bodyHeight = 785.0f)),
    (Player.Ian to PlayerOutfit.Rugby) to ActionArt(Res.drawable.ian_rugby_action_poses, ActionFrame(OutfitFrame(276, 70, 454, 780), 482.0f, 340.0f, 350.0f), ActionFrame(OutfitFrame(905, 67, 663, 783), 1322.0f, bodyHeight = 783.0f)),
    (Player.Ian to PlayerOutfit.Trex) to ActionArt(Res.drawable.ian_trex_action_poses, ActionFrame(OutfitFrame(265, 65, 523, 772), 450.5f, 330.0f, 390.0f), ActionFrame(OutfitFrame(887, 68, 676, 770), 1278.5f, bodyHeight = 770.0f)),
    (Player.Leo to PlayerOutfit.Adventure) to ActionArt(Res.drawable.leo_adventure_action_poses, ActionFrame(OutfitFrame(272, 50, 429, 789), 469.0f, 330.0f, 340.0f), ActionFrame(OutfitFrame(874, 50, 672, 789), 1295.0f, bodyHeight = 789.0f)),
    (Player.Leo to PlayerOutfit.Rugby) to ActionArt(Res.drawable.leo_rugby_action_poses, ActionFrame(OutfitFrame(224, 60, 420, 777), 443.5f, 300.0f, 350.0f), ActionFrame(OutfitFrame(892, 78, 697, 758), 1307.0f, bodyHeight = 758.0f)),
    (Player.Leo to PlayerOutfit.Rock) to ActionArt(Res.drawable.leo_rock_action_poses, ActionFrame(OutfitFrame(269, 78, 468, 756), 488.5f, 350.0f, 390.0f), ActionFrame(OutfitFrame(863, 78, 738, 756), 1346.0f, bodyHeight = 756.0f)),
    (Player.Papa to PlayerOutfit.Adventure) to ActionArt(Res.drawable.papa_adventure_action_poses, ActionFrame(OutfitFrame(176, 77, 533, 791), 458.0f, 320.0f, 275.0f), ActionFrame(OutfitFrame(853, 61, 812, 807), 1385.5f, bodyHeight = 738.0f)),
    (Player.Papa to PlayerOutfit.Rugby) to ActionArt(Res.drawable.papa_rugby_action_poses, ActionFrame(OutfitFrame(230, 116, 511, 743), 503.5f, 355.0f, 285.0f), ActionFrame(OutfitFrame(873, 45, 768, 817), 1351.0f, bodyHeight = 732.0f)),
    (Player.Papa to PlayerOutfit.Mma) to ActionArt(Res.drawable.papa_mma_action_poses, ActionFrame(OutfitFrame(234, 85, 495, 747), 490.5f, 370.0f, 270.0f), ActionFrame(OutfitFrame(902, 49, 742, 788), 1376.0f, bodyHeight = 707.0f)),
)

/** One draw sprite per outfit; idle/walk are the existing sprites. No ready/release atlas frames. */
@Composable
internal fun ActionCharacter(player: Player, outfit: PlayerOutfit, facingRight: Boolean, consuming: Boolean, reduceMotion: Boolean) {
    val art = actionArt.getValue(player to validOutfit(player, outfit))
    val bitmap = imageResource(art.resource)
    val potion = imageResource(Res.drawable.health_potion)
    val pose = if (consuming) art.consume else art.draw
    val frame = pose.crop
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(if (reduceMotion) 0 else 180)) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Canvas(Modifier.align(Alignment.BottomCenter).requiredWidth(maxHeight * 1.8f).fillMaxHeight()
            .semantics { contentDescription = tr("Player") }) {
            val pixelsPerSource = size.height / pose.bodyHeight
            val left = size.width / 2f + (frame.x - pose.anchorX) * pixelsPerSource
            val recoilPx = 1.3.dp.toPx()
            withTransform({
                if (facingRight) scale(-1f, 1f, center)
                if (!consuming && !reduceMotion) translate(left = (1f - enter.value) * recoilPx)
            }) {
                drawImage(bitmap, IntOffset(frame.x, frame.y), IntSize(frame.width, frame.height),
                    IntOffset(left.roundToInt(), (size.height - frame.height * pixelsPerSource).roundToInt()), IntSize((frame.width * pixelsPerSource).roundToInt(), (frame.height * pixelsPerSource).roundToInt()),
                    filterQuality = FilterQuality.None)
                if (consuming) {
                    val itemSize = 11.dp.toPx()
                    val hand = Offset(size.width / 2f + (pose.handX - pose.anchorX) * pixelsPerSource,
                        (pose.handY - frame.y) * pixelsPerSource + if (reduceMotion) 0f else (1f - enter.value) * 3.dp.toPx())
                    withTransform({ rotate(45f, hand) }) {
                        drawImage(potion, dstOffset = IntOffset((hand.x - itemSize * .5f).roundToInt(), (hand.y - itemSize * .18f).roundToInt()),
                            dstSize = IntSize(itemSize.roundToInt(), itemSize.roundToInt()), filterQuality = FilterQuality.None)
                    }
                }
            }
        }
    }
}
