package dev.jamescullimore.dontgotobed

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Recessed back walls make the two-axis cutaway interiors legible; only voxel walls collide. */
internal fun DrawScope.drawWorldScenery(map:TileMap,player:RopePoint,axis:WorldAxis,u:Float,centerX:Float) {
    if(!map.exploration)return
        val biome=ExplorationWorld.biome(player)
        val origin=ExplorationWorld.origin(biome)
        for(band in 0..2) for(index in 0..1) {
            val lat=origin.latitude+if(index==0) 18 else 40
            val lon=origin.longitude+band*96+if(index==0) 56 else 68
            val fixed=if(axis==WorldAxis.Latitude) lon else lat
            if(map.activeSlice !in fixed..fixed+12) continue
            val along=if(axis==WorldAxis.Latitude) lat else lon
            val x=centerX+(along-player.along(axis))*u
            if(x>size.width||x+13*u<0) continue
            val base=3+if(biome==ExplorationBiome.Jungle) if(index==0) 4 else 3 else 0
            // Mining out a building also removes its scenery once its floor is gone.
            if((along until along+13).none { map.get(it,base) }) continue
            val y=size.height-(base+7)*u
            val wall=when(biome) {
                ExplorationBiome.Meadow->Color(0xFFB19065);ExplorationBiome.Forest->Color(0xFF6F7655)
                ExplorationBiome.Frost->Color(0xFF97B4C2);ExplorationBiome.Lava->Color(0xFF75606C)
                ExplorationBiome.Jungle->Color(0xFF718C53);ExplorationBiome.Desert->Color(0xFFC5A575)
            }
            drawRect(wall.copy(alpha=.62f),Offset(x,y),Size(13*u,6*u))
            for(i in 1..5) drawLine(wall.copy(alpha=.8f),Offset(x,y+i*u),Offset(x+13*u,y+i*u),u*.06f)
            drawRect(Color(0xFF334F54),Offset(x+8*u,y+u),Size(3*u,3*u))
            drawRect(Color(0xFFF0CF86).copy(alpha=.65f),Offset(x+8.25f*u,y+1.25f*u),Size(2.5f*u,2.5f*u))
            drawLine(Color(0xFF6A5949),Offset(x+9.5f*u,y+u),Offset(x+9.5f*u,y+4*u),u*.15f)
            drawLine(Color(0xFF6A5949),Offset(x+8*u,y+2.5f*u),Offset(x+11*u,y+2.5f*u),u*.15f)
            drawRect(Color(0xFF6F4C36),Offset(x+2*u,y+4*u),Size(3*u,u*.3f))
            for(i in 0..2) drawRect(listOf(Color(0xFF9AB45D),Color(0xFFD7A957),Color(0xFFAD6C62))[i],Offset(x+(2.2f+i*.8f)*u,y+3*u),Size(u*.5f,u))
        }
}
