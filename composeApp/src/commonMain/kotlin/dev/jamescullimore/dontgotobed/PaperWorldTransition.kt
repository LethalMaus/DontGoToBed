package dev.jamescullimore.dontgotobed

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Presentation only. The world exchanges axes once, at the closed midpoint. */
data class PaperTurnFrame(val progress: Float) {
    private val p = progress.coerceIn(0f, 1f)
    val fold = sin(p * PI).toFloat().coerceAtLeast(0f)
    val widthScale = abs(cos(p * PI)).toFloat().coerceAtLeast(0.035f)
    val columnScale = (1f - fold * 0.78f)
    val frontPose = ((fold - 0.25f) / 0.55f).coerceIn(0f, 1f)
    val opening = p >= 0.5f
}

fun skyForAxis(axis: WorldAxis, darkness: Float = 0f) = androidx.compose.ui.graphics.lerp(
    if (axis == WorldAxis.Latitude) Color(0xFF87CEEB) else Color(0xFFFF9F43),
    if (axis == WorldAxis.Latitude) Color(0xFF180D35) else Color(0xFF35090F),
    darkness.coerceIn(0f, 1f)
)

/** Four-corner paper projection: compress toward a hinge and tilt the outer edge. */
fun DrawScope.paperRect(color: Color, left: Float, top: Float, right: Float, bottom: Float,
                        hinge: Float, scale: Float, fold: Float, direction: Float) {
    if (right <= left || bottom <= top) return
    fun point(x: Float, y: Float): Offset {
        val distance = (x - hinge) / size.width
        return Offset(hinge + (x - hinge) * scale,
            y + distance * fold * direction * size.height * 0.12f)
    }
    val a = point(left, top)
    val b = point(right, top)
    val c = point(right, bottom)
    val d = point(left, bottom)
    drawPath(Path().apply {
        moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
    }, color)
}

fun DrawScope.drawPaperSky(frame: PaperTurnFrame, from: WorldAxis, direction: Float, hinge: Float, darkness: Float = 0f) {
    val old = skyForAxis(from, darkness)
    val next = skyForAxis(if (from == WorldAxis.Latitude) WorldAxis.Longitude else WorldAxis.Latitude, darkness)
    drawRect(old)
    // Directional reveal beneath the two hinged sheets, reversed on the return turn.
    val reveal = (frame.progress * 2f).coerceIn(0f, 1f) * size.width
    drawRect(next, topLeft = Offset(if (direction > 0f) 0f else size.width - reveal, 0f),
        size = androidx.compose.ui.geometry.Size(reveal, size.height))
    val surface = if (frame.opening) next else old
    val skew = if (frame.opening) -direction else direction
    for (side in listOf(-1, 1)) {
        val left = if (side < 0) 0f else hinge
        val right = if (side < 0) hinge else size.width
        paperRect(surface, left, -size.height * 0.1f, right, size.height * 1.1f,
            hinge, frame.widthScale, frame.fold, skew)
        paperRect(Color.Black.copy(alpha = frame.fold * if (side * direction > 0) 0.2f else 0.08f),
            left, -size.height * 0.1f, right, size.height * 1.1f,
            hinge, frame.widthScale, frame.fold, skew)
    }
    val shadowWidth = size.width * 0.055f * frame.fold + 1f
    drawRect(Brush.horizontalGradient(listOf(Color.Transparent,
        Color.Black.copy(alpha = frame.fold * 0.28f), Color.White.copy(alpha = frame.fold * 0.18f),
        Color.Transparent), startX = hinge - shadowWidth, endX = hinge + shadowWidth))
}
