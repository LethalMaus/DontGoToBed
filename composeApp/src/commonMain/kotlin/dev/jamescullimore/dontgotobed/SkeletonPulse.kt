package dev.jamescullimore.dontgotobed

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** Skia treats transparent SrcAtop as no filter; older Skiko rejects that null pointer. */
internal fun skeletonPulseColor(amount: Float): Color? {
    if (!amount.isFinite()) return null
    val color = Color.White.copy(alpha = amount.coerceIn(0f, 1f))
    // Check the actual 8-bit native alpha, not the positive animation float.
    return color.takeIf { (it.toArgb() ushr 24) != 0 }
}
