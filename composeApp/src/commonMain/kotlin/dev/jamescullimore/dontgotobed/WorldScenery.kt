package dev.jamescullimore.dontgotobed

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import kotlin.math.ceil

/** A few distant silhouettes keep clear travel lanes scenic, with no collision geometry. */
fun DrawScope.drawDistantMountains(cameraX: Float, unit: Float, axis: WorldAxis, darkness: Float) {
    val period = 48 * unit // Quarter-speed parallax repeats exactly across every supported map size.
    val offset = ((cameraX * 0.25f) % period + period) % period
    val horizon = size.height - 3 * unit
    val color = lerp(if (axis == WorldAxis.Latitude) Color(0xFF7DA9BA) else Color(0xFFD18C62),
        if (axis == WorldAxis.Latitude) Color(0xFF221C40) else Color(0xFF431923), darkness)
    for (copy in -1..ceil(size.width / period).toInt()) {
        val x = copy * period - offset
        val ridge = Path().apply {
            // Fill behind the ground as well, so digging reveals the same background.
            moveTo(x, size.height)
            lineTo(x, horizon)
            lineTo(x + unit * 8, horizon - unit * 7)
            lineTo(x + unit * 14, horizon - unit * 4)
            lineTo(x + unit * 24, horizon - unit * 12)
            lineTo(x + unit * 32, horizon - unit * 5)
            lineTo(x + unit * 38, horizon - unit * 8)
            lineTo(x + period, horizon)
            lineTo(x + period, size.height)
            close()
        }
        drawPath(ridge, color)
    }
}
