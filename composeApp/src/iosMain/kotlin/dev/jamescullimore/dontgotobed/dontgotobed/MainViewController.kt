package dev.jamescullimore.dontgotobed

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController { App() }

/** Explicit opt-in capture surface; called only by the Swift DEBUG launch bridge. */
fun CaptureViewController(scenario: Int, playerName: String, outfitName: String, bed: Int, languageId: Int) = ComposeUIViewController {
    AppLocale.language = AppLanguage.entries.firstOrNull { it.id == languageId } ?: AppLanguage.English
    val player = Player.entries.firstOrNull { it.name == playerName } ?: Player.Leo
    GameScreen(player, initialIsHostSelected = true, mp = null,
        worldConfig = WorldConfig(horizontalSize = if (scenario > 0) ExplorationWorld.SIZE else 192,
            initialZombies = 0, initialSkeletons = 0, exploration = scenario > 0,
            unlimited = scenario > 0 && scenario != 12, portalSeed = 42),
        debugBed = bed.coerceIn(0, 4), debugAdventure = scenario.coerceIn(0, 14),
        outfit = decodePlayerOutfit(outfitName), accessibility = AccessibilityOptions(showInfoText = false))
}
