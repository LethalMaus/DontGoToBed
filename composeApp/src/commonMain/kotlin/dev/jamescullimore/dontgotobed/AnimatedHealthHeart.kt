package dev.jamescullimore.dontgotobed

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

@Composable
internal fun AnimatedHealthHeart(filled: Boolean, reduceMotion: Boolean) {
    val scale = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(filled) }
    LaunchedEffect(filled, reduceMotion) {
        if (filled != previous && !reduceMotion) {
            scale.snapTo(if (filled) 1.45f else 0.65f)
            scale.animateTo(1f, tween(320))
        } else scale.snapTo(1f)
        previous = filled
    }
    GameIcon(GameIconKind.Heart, if (filled) tr("Health") else tr("Empty heart"),
        modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        tint = if (filled) Color.Red else Color.Gray, filled = filled)
}
