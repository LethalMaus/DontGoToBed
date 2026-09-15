package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Platform shells supply their LAN address; navigation and sessions behave identically. */
@Composable
fun GameApp(localIp: String) {
    val store = rememberSettingsStore()
    var audioOptions by remember { mutableStateOf(store.loadAudioOptions()) }
    fun updateAudio(options: AudioOptions) { audioOptions = options; store.save(options) }
    var config by remember { mutableStateOf(store.loadWorldConfig()) }
    var accessibility by remember { mutableStateOf(store.loadAccessibilityOptions()) }
    fun updateAccessibility(options: AccessibilityOptions) { accessibility = options; store.save(options) }
    var player by remember { mutableStateOf<Player?>(null) }
    var host by remember { mutableStateOf(true) }
    var ip by remember { mutableStateOf(MultiplayerManager.DEFAULT_HOST_IP) }
    var multiplayer by remember { mutableStateOf<MultiplayerManager?>(null) }
    var page by remember { mutableStateOf("play") }
    var privacyReturn by remember { mutableStateOf("play") }
    var menu by remember { mutableStateOf(false) }
    var foreground by remember { mutableStateOf(true) }
    var sessionNotice by remember { mutableStateOf<String?>(null) }
    val audio = rememberGameAudio(audioOptions, foreground)
    SideEffect { if (player == null) audio.scene = MusicScene.Menu }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentMultiplayer by rememberUpdatedState(multiplayer)
    val currentPlayer by rememberUpdatedState(player)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    foreground = false
                    if (currentMultiplayer != null) {
                        currentMultiplayer?.stop()
                        multiplayer = null
                        player = null
                        menu = false
                        sessionNotice = "Multiplayer ended when the app went into the background. Host or join again to start a new session."
                    } else if (currentPlayer != null) menu = true
                }
                Lifecycle.Event.ON_START -> foreground = true
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); currentMultiplayer?.stop() }
    }
    GameBackHandler(player != null || page != "play") {
        if (page == "privacy") page = privacyReturn else if (page != "play") page = "play" else menu = true
    }
    CompositionLocalProvider(LocalGameAudio provides audio) {
    Box(Modifier.fillMaxSize()) {
        when {
            page == "privacy" -> PrivacyScreen(onBack = { page = privacyReturn })
            page == "support" -> SupportScreen(onBack = { page = "play" }, onPrivacy = { privacyReturn = "support"; page = "privacy" })
            page == "settings" -> WorldSettingsScreen(config, onChange = { config = it; store.save(it) }, onBack = { page = "play" }, accessibility = accessibility, onAccessibility = ::updateAccessibility, audioOptions = audioOptions, onAudio = ::updateAudio)
            player == null -> SelectCharacterScreen(localIp, multiplayer, { next ->
                if (multiplayer !== next) multiplayer?.stop()
                multiplayer = next
            }, config, onOpenSettings = { page = "settings" }, onSupport = { page = "support" }, onHelp = { page = "help" }, onPrivacy = { privacyReturn = "play"; page = "privacy" }, onSelect = { selected, isHost, joinIp, world ->
                player = selected; host = isHost; ip = joinIp ?: MultiplayerManager.DEFAULT_HOST_IP; config = world
            })
            else -> {
                GameScreen(player!!, host, ip, config, multiplayer, paused = !foreground || ((menu || page == "help") && multiplayer == null), accessibility = accessibility)
                Button(onClick = { menu = true }, modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(8.dp)) {
                    GameIcon(GameIconKind.Menu, "Game menu")
                }
            }
        }
    }
    if (menu) AlertDialog(
        onDismissRequest = { menu = false }, title = { Text(if (multiplayer == null) "Paused" else "Game menu") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(if (multiplayer == null) "Take your time. Your game is paused." else "Multiplayer continues while this menu is open.")
            Text("Leaving starts a fresh world next time. Your settings are saved.")
            TextButton(onClick = { menu = false; page = "help" }) { Text("How to play") }
            AudioSettings(audioOptions, ::updateAudio)
            AccessibilitySettings(accessibility, ::updateAccessibility)
        } },
        confirmButton = { TextButton(onClick = { menu = false }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { multiplayer?.stop(); multiplayer = null; player = null; menu = false }) { Text("Leave game") } }
    )
    if (page == "help") AlertDialog(onDismissRequest = { page = "play" }, title = { Text("Find Mammy before nightfall") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HelpRow(GameIconKind.Rotate, "Turn the world to explore both directions.")
            HelpRow(GameIconKind.Arrow, "Use the left stick to move. Jump and aim with the right controls.")
            Text("Button controls and reduced motion are available in Settings and the pause menu. Screen-reader actions on the joystick can also step left or right and change aim.")
            HelpRow(GameIconKind.Potion, "Break blocks, collect supplies, then choose an item from your bag to use or place.")
            HelpRow(GameIconKind.Map, "Break the dangerous bed for a map. Find Mammy for five peaceful minutes!")
            Text("Each game is a new adventure. Worlds are not saved when you leave.")
        }
    }, confirmButton = { TextButton(onClick = { page = "play" }) { Text("Let's play") } })
    sessionNotice?.let { message -> AlertDialog(onDismissRequest = { sessionNotice = null }, title = { Text("Session ended") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { sessionNotice = null }) { Text("OK") } }) }
    }
}

@Composable
private fun HelpRow(icon: GameIconKind, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        GameIcon(icon, null)
        Text(text)
    }
}

@Composable
expect fun GameBackHandler(enabled: Boolean, onBack: () -> Unit)
