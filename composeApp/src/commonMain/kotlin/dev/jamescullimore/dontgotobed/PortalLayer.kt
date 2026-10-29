package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import dontgotobed.composeapp.generated.resources.*

internal val ExplorationBiome.portalColor:Color get()=when(this) {
    ExplorationBiome.Meadow->Color(0xFF9BEA42)
    ExplorationBiome.Forest->Color(0xFF26CBB4)
    ExplorationBiome.Frost->Color(0xFF95DEFF)
    ExplorationBiome.Lava->Color(0xFFFF493A)
    ExplorationBiome.Jungle->Color(0xFF217A3D)
    ExplorationBiome.Desert->Color(0xFFFFA32C)
}

@Composable internal fun portalPainter(destination:ExplorationBiome)=atlasPainter(Res.drawable.biome_portals,
    listOf(OutfitFrame(25,8,470,483),OutfitFrame(535,8,465,483),OutfitFrame(1045,8,470,483),
        OutfitFrame(15,512,485,483),OutfitFrame(525,512,489,483),OutfitFrame(1035,512,485,483))[destination.ordinal])

@Composable
internal fun BoxScope.PortalLayer(map: TileMap, axis: WorldAxis, playerX: Dp,
                                 unit: Dp, center: Dp, biome: ExplorationBiome) {
    map.portals.filter { ExplorationWorld.biome(it.location.latitude, it.location.longitude) == biome &&
        it.location.intersects(axis, map.activeSlice, map.width, 3) }.forEach { portal ->
        val x = center + unit * portal.location.along(axis) - playerX
        // The illustrated stone rim extends outside the 3 × 5 hit-activation volume.
        Image(portalPainter(portal.destination),null,
            Modifier.align(Alignment.BottomStart).offset(x=x-unit*.9f,y=-unit*portal.location.row)
                .size(unit*4.8f,unit*5.5f),contentScale=ContentScale.FillBounds)
    }
}
