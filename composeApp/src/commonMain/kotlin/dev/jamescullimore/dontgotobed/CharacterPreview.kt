package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import dontgotobed.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.sin

/** A local showcase, not gameplay state. No frame loop runs with reduced motion or in background. */
@Composable
internal fun CharacterPreview(player: Player, animate: Boolean, modifier: Modifier = Modifier, outfit: PlayerOutfit = PlayerOutfit.Original) {
    var elapsed by remember(player) { mutableFloatStateOf(0f) }
    LaunchedEffect(player, animate) {
        elapsed = 0f
        if (animate) {
            var start = 0L
            while (true) withFrameNanos { frame ->
                if (start == 0L) start = frame
                elapsed = ((frame - start) / 1_000_000_000f) % 8f
            }
        }
    }
    val attacking = elapsed in 7.5f..7.82f
    val walking = elapsed in 2f..4f || elapsed in 6f..7.5f
    val walkPose = (elapsed * 4).toInt() % 2 == 1
    val pose = if (!walking) CharacterPose.Front else if (walkPose) CharacterPose.Walk else CharacterPose.Side
    val jump = if (elapsed in 4.4f..5.2f) sin((elapsed - 4.4f) / .8f * PI).toFloat().coerceAtLeast(0f) else 0f
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxWidth(.6f).height(9.dp)) {
            drawOval(Color.Black.copy(alpha = .23f), Offset.Zero, Size(size.width, size.height))
        }
        Image(if(attacking) attackPlayerPainter(player,outfit) else characterPainter(player, outfit, pose), contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(bottom = 6.dp, top = 18.dp).graphicsLayer {
                translationY = -jump * 16.dp.toPx() - if (animate) (sin(elapsed * 4.0) * 1.5).toFloat().dp.toPx() else 0f
                rotationZ = if (walking && animate) (sin(elapsed * 12.0) * 1.3).toFloat() else 0f
                scaleX = if (walking) if (elapsed < 6f) -1f else 1f else 1f
            })
    }
}
