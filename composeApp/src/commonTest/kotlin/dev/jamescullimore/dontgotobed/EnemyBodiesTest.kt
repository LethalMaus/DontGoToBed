package dev.jamescullimore.dontgotobed
import kotlin.test.*

class EnemyBodiesTest {
    private fun move(from:Float,to:Float,obstacle:Float,other:Float=0f,height:Float=3f) =
        EnemyBodies.limit(from,to,0f,3f,obstacle,other,height,2f,1f,3f,100f,true,true,false)
    @Test fun followingAndOpposingEnemiesStopAtBodiesWithoutTunnelling() {
        assertEquals(8f,move(4f,20f,10f))
        assertEquals(12f,move(16f,0f,10f))
        assertEquals(8f,move(8f,8.3f,10f))
        assertEquals(7f,move(8f,7f,10f))
    }
    @Test fun worldSeamAndDepthRowsDoNotBreakCollision() {
        assertEquals(98f,move(96f,4f,0f))
        assertEquals(2f,move(4f,96f,0f))
        assertEquals(20f,move(4f,20f,10f,other=2f))
        assertEquals(20f,move(4f,20f,10f,height=7f))
        assertEquals(9f,EnemyBodies.limit(5f,14f,0f,3f,10f,0f,3f,1f,2f,3f,100f,true,true,false))
    }
    @Test fun fallingEnemiesLandOnBodiesAndExistingOverlapCanSeparate() {
        assertEquals(6f,EnemyBodies.limit(12f,0f,4f,0f,3f,4f,0f,3f,2f,1f,100f,false,true,true))
        assertEquals(9f,move(9f,11f,10f))
        assertEquals(8f,move(9f,8f,10f))
    }
    @Test fun skeletonKeepsPursuingWhenTargetLeavesBowSight() {
        val memory=PursuitMemory()
        assertFalse(memory.pursuing(0,false,false))
        assertTrue(memory.pursuing(100,true,false))
        assertTrue(memory.pursuing(4000,false,false))
        assertFalse(memory.pursuing(5200,false,false))
        assertTrue(memory.pursuing(6000,false,true))
        val map=TileMap(48,12).also { it.seedGroundBand() }
        var p=Triple(4f,10f,3f)
        repeat(100) { p=NightPursuit.advance(p.first,p.second,p.third,map,1f,.1f,20f,10f) }
        assertEquals(18f,p.first,.01f) // Continues through bow range to body contact.
    }
}
