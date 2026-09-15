package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import dontgotobed.composeapp.generated.resources.*
import kotlin.math.floor

/** NPC animation invalidates this layer, never the terrain/HUD/session composition. */
@Composable
internal fun BoxScope.EnemyLayer(
    vm: GameViewModel, tileMap: TileMap, worldConfig: WorldConfig,
    playerWorldXDp: Dp, playerOtherAxisDp: Dp, centerX: Dp, screenWidth: Dp,
    unit: Dp, playerWidth: Dp, playerHeight: Dp, paused: Boolean, reduceMotion: Boolean
) {
    val density = LocalDensity.current
    fun xDpToPx(value: Dp) = with(density) { value.toPx() }
    fun pxToDp(value: Float) = with(density) { value.toDp() }
    val unitPx = xDpToPx(unit)
    val zombies by vm.zombies.collectAsState()
    val skeletons by vm.skeletons.collectAsState()
    val arrows by vm.arrows.collectAsState()
    // Decode once as the session opens, before an enemy first reaches the viewport.
    val zombiePainter = painterResource(Res.drawable.zombie_walk)
    val skeletonPainter = painterResource(Res.drawable.skeleton_archer)
    var frameMillis by remember { mutableLongStateOf(currentTimeMillis()) }
    val animated = zombies.isNotEmpty() || skeletons.isNotEmpty() || arrows.isNotEmpty()
    LaunchedEffect(animated, paused) {
        while (animated && !paused) {
            withFrameNanos { frameMillis = currentTimeMillis() }
        }
    }
    // Observe even when no sprite is visible, so enemies entering the viewport
    // are discovered without waiting for a player move or a new network packet.
    val now = frameMillis
    // Render Zombies if present
    if (zombies.isNotEmpty()) {
        val worldWidthDp = pxToDp(tileMap.width * unitPx)
        zombies.forEach { z ->
            val rowAlpha = WorldRows.previewAlpha(z.otherAxisPx, xDpToPx(playerOtherAxisDp),
                unitPx, tileMap.width, worldConfig.nearbyEnemyRows)
            if (rowAlpha == 0f) return@forEach
            val zXDp = pxToDp(z.worldXPx)
            val baseX = centerX + (zXDp - playerWorldXDp)
            val candidates = listOf(baseX, baseX - worldWidthDp, baseX + worldWidthDp)
            val zHeightDp = pxToDp(z.bottomPx)
            candidates.forEach { candX ->
                if (candX > -playerWidth && candX < screenWidth + playerWidth) {
                    Image(
                        painter = zombiePainter,
                        contentDescription = if (rowAlpha == 1f) "Zombie" else "Nearby-row zombie",
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = candX, y = -zHeightDp)
                            .height(playerHeight)
                            .graphicsLayer {
                                alpha = rowAlpha
                                // The source zombie artwork faces right.
                                scaleX = if (z.facingRight) 1f else -1f },
                        colorFilter = if (!reduceMotion && now <= z.flashUntil) ColorFilter.tint(Color.Red) else null
                    )
                }
            }
        }
    }

    // Render Skeletons if present
    if (skeletons.isNotEmpty()) {
        val worldWidthDp = pxToDp(tileMap.width * unitPx)
        skeletons.forEach { s ->
            val rowAlpha = WorldRows.previewAlpha(s.otherAxisPx, xDpToPx(playerOtherAxisDp),
                unitPx, tileMap.width, worldConfig.nearbyEnemyRows)
            if (rowAlpha == 0f) return@forEach
            val sXDp = pxToDp(s.worldXPx)
            val baseX = centerX + (sXDp - playerWorldXDp)
            val candidates = listOf(baseX, baseX - worldWidthDp, baseX + worldWidthDp)
            val sHeightDp = pxToDp(s.bottomPx)
            candidates.forEach { candX ->
                if (candX > -playerWidth && candX < screenWidth + playerWidth) {
                    Image(
                        painter = skeletonPainter,
                        contentDescription = if (rowAlpha == 1f) "Skeleton Archer" else "Nearby-row skeleton",
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = candX, y = -sHeightDp)
                            .height(playerHeight)
                            .graphicsLayer {
                                alpha = rowAlpha
                                scaleX = if (s.facingRight) 1f else -1f
                            },
                        colorFilter = if (!reduceMotion && now <= s.flashUntil) {
                            ColorFilter.tint(Color.Red)
                        } else if (!reduceMotion) {
                            skeletonPulseColor(s.pulseAmount)?.let {
                                ColorFilter.tint(it, BlendMode.SrcAtop)
                            }
                        } else null
                    )
                }
            }
        }
    }

    // Render Arrows if present
    if (arrows.isNotEmpty()) {
        val worldWidthDp = pxToDp(tileMap.width * unitPx)
        arrows.forEach { arrow ->
            if (tileMap.wrap(floor(arrow.otherAxisPx / unitPx).toInt()) != tileMap.activeSlice) return@forEach
            val aXDp = pxToDp(arrow.worldXPx)
            val baseX = centerX + (aXDp - playerWorldXDp)
            val candidates = listOf(baseX, baseX - worldWidthDp, baseX + worldWidthDp)
            val aHeightDp = pxToDp(arrow.bottomPx)
            candidates.forEach { candX ->
                if (candX > -unit && candX < screenWidth + unit) {
                    Box(
                        modifier = Modifier
                            .semantics { contentDescription = "Arrow" }
                            .align(Alignment.BottomStart)
                            .offset(x = candX, y = -aHeightDp)
                            .height(2.dp)
                            .width(unit)
                            .background(Color.DarkGray)
                    )
                }
            }
        }
    }

}
