package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalDensity
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

/** Map coordinates are global; exploration displays only the player's current region. */
internal data class MinimapViewport(val left: Float, val top: Float, val width: Float, val height: Float) {
    fun contains(point: Offset) = point.x >= left && point.x < left + width && point.y >= top && point.y < top + height
    fun project(point: Offset) = Offset((point.x - left) / width, (point.y - top) / height)
}

internal fun minimapViewport(player: Offset, exploration: Boolean): MinimapViewport {
    if (!exploration) return MinimapViewport(0f, 0f, 1f, 1f)
    val column = (player.x * 3).toInt().coerceIn(0, 2)
    val row = (player.y * 2).toInt().coerceIn(0, 1)
    return MinimapViewport(column / 3f, row / 2f, 1f / 3f, 1f / 2f)
}

@Composable
internal fun WorldMinimap(markers: List<MinimapMarker>, player: Offset, axis: WorldAxis, modifier: Modifier = Modifier, showPortals: Boolean = false) {
    val viewport = minimapViewport(player, showPortals)
    BoxWithConstraints(modifier.background(Color(0xFF10243A).copy(alpha = 0.5f)).padding(6.dp)
        .semantics { contentDescription = tr("Live world minimap. Latitude left to right, longitude top to bottom.") }) {
        // A compact square overview normalises each axis within the current region.
        // Large accessibility text can scroll within the panel rather than cover the controls.
        val fontScale = LocalDensity.current.fontScale
        val mapSide = (maxHeight - 24.dp * fontScale).coerceIn(48.dp, 112.dp)
        Column(Modifier.verticalScroll(rememberScrollState())) {
        if(showPortals) Text(tr(ExplorationWorld.biome((player.x*ExplorationWorld.SIZE).toInt(),(player.y*ExplorationWorld.SIZE).toInt()).title),
            color=Color.White,fontSize=12.sp)
        if (!showPortals) Text(tr("Whole world · Lat → / Long ↓"), color = Color.White, fontSize = 10.sp, lineHeight = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(mapSide).padding(4.dp)) {
            fun point(p: Offset) = Offset(p.x * size.width, p.y * size.height)
            for (i in 0..4) {
                val fraction = i / 4f
                drawLine(Color.White.copy(alpha = 0.16f), Offset(size.width * fraction, 0f), Offset(size.width * fraction, size.height))
                drawLine(Color.White.copy(alpha = 0.16f), Offset(0f, size.height * fraction), Offset(size.width, size.height * fraction))
            }
            val at = point(viewport.project(player))
            if (axis == WorldAxis.Latitude) drawLine(Color.Cyan.copy(alpha = 0.35f), Offset(0f, at.y), Offset(size.width, at.y))
            else drawLine(Color.Cyan.copy(alpha = 0.35f), Offset(at.x, 0f), Offset(at.x, size.height))
            markers.forEach { marker ->
                val location = Offset(marker.latitude, marker.longitude)
                if (viewport.contains(location)) drawCircle(marker.color, 2.5.dp.toPx(), point(viewport.project(location)))
            }
            drawCircle(Color.Black, 4.dp.toPx(), at)
            drawCircle(Color.Cyan, 3.dp.toPx(), at)
            drawCircle(Color.White, 4.dp.toPx(), at, style = Stroke(1.dp.toPx()))
        }
        Text(buildAnnotatedString {
            withStyle(SpanStyle(color = Color.Cyan)) { append("● " + tr("You") + "\n") }
            withStyle(SpanStyle(color = Color(0xFF608CFF))) { append("● " + tr("Players") + "\n") }
            withStyle(SpanStyle(color = Color(0xFFFF80C0))) { append("● " + tr("Mammy") + "\n") }
            if (showPortals) withStyle(SpanStyle(color = Color(0xFFD694FF))) { append("● " + tr("Portals") + "\n") }
            withStyle(SpanStyle(color = Color.Green)) { append("● " + tr("Zombies") + "\n") }
            withStyle(SpanStyle(color = Color(0xFFFFA040))) { append("● " + tr("Skeletons") + "") }
        }, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}
}
