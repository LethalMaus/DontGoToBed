package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class MinimapMarker(val latitude: Float, val longitude: Float, val color: Color)

/** Fixed orientation: latitude increases right, longitude increases down. */
internal fun minimapPosition(along: Float, other: Float, axis: WorldAxis, span: Float): Offset {
    fun wrap(value: Float) = ((value % span + span) % span) / span
    return if (axis == WorldAxis.Latitude) Offset(wrap(along), wrap(other))
        else Offset(wrap(other), wrap(along))
}

@Composable
internal fun WorldMinimap(markers: List<MinimapMarker>, player: Offset, axis: WorldAxis, modifier: Modifier = Modifier) {
    Column(modifier.background(Color(0xEE10243A)).padding(8.dp)
        .semantics { contentDescription = "Live world minimap. Latitude left to right, longitude top to bottom." }) {
        Text("Whole world · Lat → / Long ↓", color = Color.White, fontSize = 10.sp, lineHeight = 12.sp)
        Canvas(Modifier.size(120.dp).padding(5.dp)) {
            fun point(p: Offset) = Offset(p.x * size.width, p.y * size.height)
            for (i in 0..4) {
                val fraction = i / 4f
                drawLine(Color.White.copy(alpha = 0.16f), Offset(size.width * fraction, 0f), Offset(size.width * fraction, size.height))
                drawLine(Color.White.copy(alpha = 0.16f), Offset(0f, size.height * fraction), Offset(size.width, size.height * fraction))
            }
            val at = point(player)
            if (axis == WorldAxis.Latitude) drawLine(Color.Cyan.copy(alpha = 0.35f), Offset(0f, at.y), Offset(size.width, at.y))
            else drawLine(Color.Cyan.copy(alpha = 0.35f), Offset(at.x, 0f), Offset(at.x, size.height))
            markers.forEach { marker ->
                drawCircle(marker.color, 2.5.dp.toPx(), point(Offset(marker.latitude, marker.longitude)))
            }
            drawCircle(Color.Black, 4.dp.toPx(), at)
            drawCircle(Color.Cyan, 3.dp.toPx(), at)
            drawCircle(Color.White, 4.dp.toPx(), at, style = Stroke(1.dp.toPx()))
        }
        Text(buildAnnotatedString {
            withStyle(SpanStyle(color = Color.Cyan)) { append("● You   ") }
            withStyle(SpanStyle(color = Color(0xFF608CFF))) { append("● Players\n") }
            withStyle(SpanStyle(color = Color(0xFFFF80C0))) { append("● Mammy\n") }
            withStyle(SpanStyle(color = Color.Green)) { append("● Zombies   ") }
            withStyle(SpanStyle(color = Color(0xFFFFA040))) { append("● Skeletons") }
        }, fontSize = 9.sp, lineHeight = 12.sp)
    }
}
