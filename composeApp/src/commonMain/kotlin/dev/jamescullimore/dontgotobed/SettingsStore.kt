package dev.jamescullimore.dontgotobed

import androidx.compose.runtime.Composable

interface SettingsStore {
    fun readInt(key: String, default: Int): Int
    fun writeInt(key: String, value: Int)
}

@Composable
expect fun rememberSettingsStore(): SettingsStore

@Composable
expect fun rememberPurchaseRecoveryStore(): SettingsStore

fun SettingsStore.loadWorldConfig() = WorldConfig(
    horizontalSize = readInt("worldSize", 192).takeIf { it in listOf(192, 384, 576) } ?: 192,
    initialZombies = readInt("zombies", 5).coerceIn(0, 12),
    initialSkeletons = readInt("skeletons", 3).coerceIn(0, 12),
    nearbyEnemyRows = readInt("enemyRows", 1).coerceIn(0, 2),
    nearbyTerrainRows = readInt("terrainRows", 1).coerceIn(0, 2)
)

fun SettingsStore.save(config: WorldConfig) {
    writeInt("worldSize", config.horizontalSize)
    writeInt("zombies", config.initialZombies)
    writeInt("skeletons", config.initialSkeletons)
    writeInt("enemyRows", config.nearbyEnemyRows)
    writeInt("terrainRows", config.nearbyTerrainRows)
}

expect fun revenueCatPublicKey(): String
