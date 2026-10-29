package dev.jamescullimore.dontgotobed

import androidx.compose.ui.graphics.Color

internal data class WorldLandmark(val name: String, val location: WorldLocation)

/** Authored connected districts live in the open spaces between the larger cave hills. */
internal object RichWorlds {
    val landmarks = ExplorationBiome.entries.flatMap { biome ->
        val o=ExplorationWorld.origin(biome)
        listOf(WorldLandmark(when(biome) {
            ExplorationBiome.Meadow->"Meadow village";ExplorationBiome.Forest->"Woodland lodge"
            ExplorationBiome.Frost->"Snow shelters";ExplorationBiome.Lava->"Basalt outpost"
            ExplorationBiome.Jungle->"Canopy camp";ExplorationBiome.Desert->"Desert settlement"
        },WorldLocation(o.latitude+27,o.longitude+62,3)),WorldLandmark(when(biome) {
            ExplorationBiome.Meadow->"Old watchtower";ExplorationBiome.Forest->"Ancient oak"
            ExplorationBiome.Frost->"Ice arch";ExplorationBiome.Lava->"Volcanic steps"
            ExplorationBiome.Jungle->"Vine crossing";ExplorationBiome.Desert->"Buried pyramid"
        },WorldLocation(o.latitude+120,o.longitude+165,3)))
    }
    fun populate(map:TileMap) {
        fun volume(x:Int,z:Int,y:Int,w:Int,d:Int,h:Int,material:BlockMaterial,color:Color=material.color) {
            if(w<=0 || d<=0 || h<=0) return
            for(slice in z until z+d) {
                if(ExplorationWorld.boundary(x,slice)||ExplorationWorld.boundary(x+w-1,slice)) continue
                map.setActiveSlice(WorldAxis.Latitude,x,slice)
                map.placeBlock(x,y,w,h,color,material.durability,material)
            }
        }
        for(biome in ExplorationBiome.entries) {
            val o=ExplorationWorld.origin(biome)
            fun v(x:Int,z:Int,y:Int,w:Int,d:Int,h:Int,m:BlockMaterial,c:Color=m.color)=volume(o.latitude+x,o.longitude+z,y,w,d,h,m,c)
            val stone=biome.rock
            val wall=if(biome in listOf(ExplorationBiome.Desert,ExplorationBiome.Frost,ExplorationBiome.Lava)) BlockMaterial.Stone else BlockMaterial.Wood
            fun house(x:Int,z:Int,raised:Int=0) {
                val base=3+raised
                if(raised>0) for(dx in listOf(0,12)) for(dz in listOf(0,12)) v(x+dx,z+dz,3,1,1,raised,BlockMaterial.Wood)
                v(x,z,base,13,13,1,wall,stone)
                // Four doorways provide access from both world axes; low window sills and lintels.
                for(dz in listOf(0,12)) {
                    v(x,z+dz,base+1,4,1,6,wall,stone);v(x+9,z+dz,base+1,4,1,6,wall,stone)
                    v(x+4,z+dz,base+6,5,1,1,wall,stone)
                }
                for(dx in listOf(0,12)) {
                    v(x+dx,z+1,base+1,1,3,6,wall,stone);v(x+dx,z+9,base+1,1,3,6,wall,stone)
                    v(x+dx,z+4,base+6,1,5,1,wall,stone)
                }
                for(level in 0..3) v(x-1+level,z-1,base+7+level,15-2*level,15,1,BlockMaterial.Wood,biome.ground)
                // Walkable steps, furniture and a chimney make the interior usable, not solid scenery.
                for(step in 0..raised) v(x+5,z-2-raised+step,3+step,3,1,1,wall,stone)
                v(x+2,z+5,base+1,3,2,1,BlockMaterial.Wood)
                v(x+2,z+5,base+2,3,2,1,BlockMaterial.Wood)
                v(x+10,z+8,base+8,2,2,5,BlockMaterial.Stone,stone)
            }
            for(band in 0..2) {
                val z=band*96+56
                house(18,z,if(biome==ExplorationBiome.Jungle) 4 else 0)
                house(40,z+12,if(biome==ExplorationBiome.Jungle) 3 else 0)
                // District paths connect the two homes and the next section without blocking the portal corridor.
                v(12,z-4,3,48,2,1,BlockMaterial.Stone,stone.copy(alpha=1f))
                for(step in 0..5) v(60+step*3,z+23,3+step,3,5,1,BlockMaterial.Stone,stone)
                v(78,z+23,8,12,5,1,BlockMaterial.Wood)
                for(step in 0..5) v(90+step*3,z+23,8-step,3,5,1,BlockMaterial.Stone,stone)
            }
            // Each region has a distinct central landmark and silhouette.
            when(biome) {
                ExplorationBiome.Meadow -> {
                    for(y in 3..24) {
                        v(116,162,y,2,12,1,BlockMaterial.Stone,stone);v(126,162,y,2,12,1,BlockMaterial.Stone,stone)
                    }
                    v(115,161,25,14,14,2,BlockMaterial.Wood)
                    for(i in 0..21) v(110+i%6,162+i/6*3,3+i,2,3,1,BlockMaterial.Stone,stone)
                    for(x in 114..128 step 3) v(x,160,27,2,2,2,BlockMaterial.Stone,stone)
                }
                ExplorationBiome.Forest -> {
                    v(116,160,3,5,5,20,BlockMaterial.Wood)
                    v(109,153,19,19,19,5,BlockMaterial.Grass,biome.ground)
                    v(112,156,24,13,13,4,BlockMaterial.Grass,biome.ground)
                    v(116,160,28,5,5,3,BlockMaterial.Grass,biome.ground)
                }
                ExplorationBiome.Frost -> {
                    for(i in 0..9) {
                        v(110+i,158,3+i,2,5,2,BlockMaterial.Stone,biome.ground)
                        v(136-i,158,3+i,2,5,2,BlockMaterial.Stone,biome.ground)
                    }
                    v(120,158,13,8,5,3,BlockMaterial.Stone,biome.ground)
                }
                ExplorationBiome.Lava -> for(i in 0..7) {
                    v(111+i*3,160,3,2,5,3+i*2,BlockMaterial.Stone,stone)
                    v(111+i*3,160,6+i*2,2,5,1,BlockMaterial.Stone,Color(0xFFBF6944))
                }
                ExplorationBiome.Jungle -> {
                    v(112,160,3,2,2,20,BlockMaterial.Wood);v(132,160,3,2,2,20,BlockMaterial.Wood)
                    v(108,156,23,10,10,2,BlockMaterial.Grass,biome.ground)
                    v(128,156,23,10,10,2,BlockMaterial.Grass,biome.ground)
                    v(113,160,19,20,2,1,BlockMaterial.Wood)
                }
                ExplorationBiome.Desert -> for(i in 0..10) {
                    // Hollow pyramid: entrance remains open at ground level on both axes.
                    val w=26-i*2;val x=110+i;val z=153+i
                    v(x,z,3+i,w,1,1,BlockMaterial.Stone,biome.ground)
                    v(x,z+w-1,3+i,w,1,1,BlockMaterial.Stone,biome.ground)
                    if(i>3) { v(x,z+1,3+i,1,w-2,1,BlockMaterial.Stone,biome.ground);v(x+w-1,z+1,3+i,1,w-2,1,BlockMaterial.Stone,biome.ground) }
                }
            }
        }
        // Hanging vines have a canopy anchor and clear swing space through the open district.
        SwingVines.all.filter { it.swingable }.forEach { v ->
            // Climbing follows the vine through its solid canopy anchor to stand on top.
            volume(v.latitude.toInt()-5,v.longitude.toInt()-2,27,12,5,2,BlockMaterial.Wood)
        }
    }
}
