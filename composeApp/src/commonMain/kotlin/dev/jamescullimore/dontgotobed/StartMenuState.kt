package dev.jamescullimore.dontgotobed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal enum class SessionChoice { Solo, Host, Join }

/** Lives above menu navigation, so opening settings never loses a pending LAN session. */
internal class StartMenuState(initialPlayer: Player, initialOutfits: Map<Player, PlayerOutfit> = emptyMap()) {
    var player by mutableStateOf(initialPlayer)
    val outfits = mutableStateMapOf<Player, PlayerOutfit>().apply { putAll(initialOutfits) }
    fun outfitFor(player: Player) = outfits[player] ?: PlayerOutfit.Original
    var session by mutableStateOf(SessionChoice.Solo)
    var joinIp by mutableStateOf("")
    var connected by mutableStateOf(false)
    var connecting by mutableStateOf(false)
    var peer by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)

    fun resetConnection() {
        session = SessionChoice.Solo
        connected = false
        connecting = false
        peer = null
        error = null
    }
}

internal fun SettingsStore.loadSelectedPlayer(): Player =
    Player.entries.getOrNull(readInt("selectedPlayer", 0)) ?: Player.Leo

internal fun SettingsStore.saveSelectedPlayer(player: Player) = writeInt("selectedPlayer", player.ordinal)

internal fun canStartSession(choice: SessionChoice, hasManager: Boolean, connected: Boolean): Boolean = when (choice) {
    SessionChoice.Solo -> !hasManager
    SessionChoice.Host -> hasManager
    SessionChoice.Join -> hasManager && connected
}
