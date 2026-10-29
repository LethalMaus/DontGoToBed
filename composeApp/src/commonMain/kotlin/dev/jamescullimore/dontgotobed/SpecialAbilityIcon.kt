package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import dontgotobed.composeapp.generated.resources.Res
import dontgotobed.composeapp.generated.resources.special_drums
import dontgotobed.composeapp.generated.resources.special_hound
import dontgotobed.composeapp.generated.resources.special_fist
import org.jetbrains.compose.resources.imageResource

/** Illustrated inventory-style ability art; the parent button owns accessibility. */
@Composable
internal fun SpecialAbilityIcon(kind: SpecialKind, cooldownFraction: Float, modifier: Modifier = Modifier) {
    val art=when(kind) {
        SpecialKind.Drums -> Res.drawable.special_drums
        SpecialKind.Dog -> Res.drawable.special_hound
        SpecialKind.Fists -> Res.drawable.special_fist
    }
    Box(modifier) {
        Image(imageResource(art),null,Modifier.fillMaxSize(),filterQuality=FilterQuality.Low)
        if(cooldownFraction>0f) Canvas(Modifier.fillMaxSize()) {
            val u=size.minDimension/32f
            drawArc(Color.White.copy(alpha=.9f),-90f,360f*(1f-cooldownFraction.coerceIn(0f,1f)),false,
                Offset(u,u),Size(30*u,30*u),style=Stroke(1.5f*u))
        }
    }
}
