package dev.jamescullimore.dontgotobed

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import dontgotobed.composeapp.generated.resources.Res
import dontgotobed.composeapp.generated.resources.mammy_celebration
import org.jetbrains.compose.resources.painterResource
import kotlin.math.sin

@Composable
internal fun MammyCelebration(remainingMs: Long, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val time = (3200L - remainingMs).coerceAtLeast(0)
    val fade = (remainingMs / 650f).coerceIn(0f, 1f)
    Box(modifier.graphicsLayer { alpha = fade; translationY = if (reduceMotion) 0f else -(1f - fade) * 36f }) {
        Image(if (reduceMotion || time / 350 % 2 == 0L) painterResource(Res.drawable.mammy_celebration) else mammyPainter(),
            tr("Mammy"), Modifier.fillMaxSize().graphicsLayer {
                if (!reduceMotion) translationY = -kotlin.math.abs(sin(time / 180f)) * 6f
            }, contentScale = ContentScale.Fit, alignment = Alignment.BottomCenter)
        if (!reduceMotion) Canvas(Modifier.fillMaxSize()) {
            val colours = listOf(Color(0xFFFFD35A), Color(0xFFFA7298), Color(0xFF74DAE4), Color(0xFF95DA68))
            repeat(24) { i ->
                val phase = ((time + i * 89) % 1300) / 1300f
                val x = size.width * ((i * 37 % 101) / 100f) + sin(time / 180f + i) * 7f
                drawRect(colours[i % colours.size].copy(alpha = fade), Offset(x, phase * size.height), Size(4.dp.toPx(), 3.dp.toPx()))
            }
        }
    }
}

/** Approved empty-hand pose with separate, half-size, mirrored 45-degree consumable. */
@Composable
internal fun ConsumingCharacter(player: Player, outfit: PlayerOutfit, facingRight: Boolean, reduceMotion: Boolean) {
    ActionCharacter(player, outfit, facingRight, consuming = true, reduceMotion = reduceMotion)
}
