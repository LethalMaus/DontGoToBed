package dev.jamescullimore.dontgotobed

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsStoreTest {
    private class MemoryStore(val values: MutableMap<String, Int> = mutableMapOf()) : SettingsStore {
        override fun readInt(key: String, default: Int) = values[key] ?: default
        override fun writeInt(key: String, value: Int) { values[key] = value }
    }
    @Test fun choicesSurviveANewScreenWithoutSavingAWorld() {
        val first = MemoryStore()
        val chosen = WorldConfig(horizontalSize = 384, initialZombies = 0, initialSkeletons = 12, nearbyEnemyRows = 2, nearbyTerrainRows = 0)
        first.save(chosen)
        assertEquals(chosen, MemoryStore(first.values).loadWorldConfig())
        assertEquals(setOf("worldSize", "zombies", "skeletons", "enemyRows", "terrainRows"), first.values.keys)
    }
    @Test fun obsoleteOrCorruptPreferencesCannotAllocateAnUnboundedWorld() {
        val store = MemoryStore(mutableMapOf("worldSize" to Int.MAX_VALUE, "zombies" to -9, "skeletons" to 500, "enemyRows" to -1, "terrainRows" to 99))
        assertEquals(WorldConfig(horizontalSize = 192, initialZombies = 0, initialSkeletons = 12, nearbyEnemyRows = 0, nearbyTerrainRows = 2), store.loadWorldConfig())
    }
}
