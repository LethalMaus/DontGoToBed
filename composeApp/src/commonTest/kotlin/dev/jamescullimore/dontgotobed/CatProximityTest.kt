package dev.jamescullimore.dontgotobed

import kotlin.test.*

class CatProximityTest {
    @Test fun sevenFullBlocksUsesThreeDimensionalDistanceAndWrapsAtWorldEdges() {
        val map=TileMap(96,54);val p=RopePoint(30f,30f,3f)
        fun gain(x:Float=30f,z:Float=30f,y:Float=3f)=CatProximity.gain(p,GuideCat(RopePoint(x,z,y)),map)
        assertEquals(1f,gain())
        assertTrue(gain(x=51f)>0);assertTrue(gain(z=51f)>0);assertTrue(gain(y=24f)>0)
        assertEquals(0f,gain(x=51.1f));assertEquals(0f,gain(x=48f,z=48f))
        assertTrue(CatProximity.gain(RopePoint(1f,10f,3f),GuideCat(RopePoint(94f,10f,3f)),map)>0)
    }
    @Test fun otherBiomesAndDepartedCatsStaySilent() {
        val map=TileMap(576,54,exploration=true);val p=RopePoint(50f,279f,3f)
        assertEquals(0f,CatProximity.gain(p,GuideCat(RopePoint(50f,296f,3f)),map))
        assertEquals(0f,CatProximity.gain(p,GuideCat(p,CatMood.Flee),map))
        assertEquals(0f,CatProximity.gain(p,null,map))
        assertTrue(CatProximity.gain(p,GuideCat(p,CatMood.Guide),map)>0)
    }
}
