package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Platform shells supply their LAN address; navigation and sessions behave identically. */
@Composable
fun GameApp(localIp: String) {
    val store = rememberSettingsStore()
    val files1=rememberWorldSaveFiles(1);val files2=rememberWorldSaveFiles(2);val files3=rememberWorldSaveFiles(3)
    val saveControllers=remember(files1,files2,files3) { listOf(files1,files2,files3).map(::WorldSaveController) }
    var saveSlot by remember { mutableIntStateOf(0) }
    val saves=saveControllers[saveSlot]
    var slotPicker by remember { mutableStateOf(false) }
    var occupiedSlots by remember { mutableStateOf(listOf(false,false,false)) }
    val scope = rememberCoroutineScope()
    var restoredWorld by remember { mutableStateOf<SavedWorld?>(null) }
    var pendingWorld by remember { mutableStateOf<WorldConfig?>(null) }
    var pendingPlayer by remember { mutableStateOf<Player?>(null) }
    var loadingWorld by remember { mutableStateOf(false) }
    var saveChoice by remember { mutableStateOf(false) }
    var replaceWorld by remember { mutableStateOf(false) }

    remember(store) { AppLocale.language = store.loadLanguage(); AppLocale.systemCode = systemLanguageCode(); true }
    val startMenu = remember { StartMenuState(store.loadSelectedPlayer(), Player.entries.associateWith { store.loadOutfit(it) }) }
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
                        startMenu.resetConnection()
                        player = null
                        menu = false
                        sessionNotice = tr("Multiplayer ended when the app went into the background. Host or join again to start a new session.")
                    } else if (currentPlayer != null) menu = true
                }
                Lifecycle.Event.ON_START -> { foreground = true; AppLocale.systemCode = systemLanguageCode() }
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
            page == "worlds" -> WorldSettingsScreen(config, onChange = { config = it; store.save(it) }, onBack = { page = "play" }, readOnly = startMenu.session == SessionChoice.Join)
            page == "settings" -> AppSettingsScreen(audioOptions, ::updateAudio, accessibility, ::updateAccessibility,
                onLanguage = { AppLocale.language = it; store.writeInt("language", it.id) },
                onBack = { page = "play" }, onHelp = { page = "help" }, onSupport = { page = "support" },
                onPrivacy = { privacyReturn = "settings"; page = "privacy" })
            player == null -> SelectCharacterScreen(localIp, multiplayer, { next ->
                if (multiplayer !== next) multiplayer?.stop()
                multiplayer = next
            }, config, state = startMenu,
                onPlayerChange = { startMenu.player = it; store.saveSelectedPlayer(it) },
                onOutfitChange = { character, outfit -> startMenu.outfits[character] = outfit; store.saveOutfit(character, outfit) },
                onModeChange = { config=config.copy(unlimited=it);store.save(config) },
                onOpenWorlds = { page = "worlds" }, onOpenSettings = { page = "settings" },
                onHelp = { page = "help" }, foreground = foreground, animate = foreground && page == "play" && !accessibility.reduceMotion && !rememberSystemReducedMotion(), onSelect = { selected, isHost, joinIp, world ->
                host = isHost; ip = joinIp ?: MultiplayerManager.DEFAULT_HOST_IP
                if(world.exploration && isHost) {
                    pendingWorld=world;pendingPlayer=selected;loadingWorld=true
                    scope.launch {
                        runCatching { saveControllers.map { it.exists() } }.onSuccess { occupiedSlots=it;slotPicker=true }.onFailure { sessionNotice=tr("Could not load world. Your save has been kept.") }
                        loadingWorld=false
                    }
                } else { restoredWorld=null;config=world;player=selected }
            })
            else -> {
                GameScreen(player!!, host, ip, config, multiplayer, paused = !foreground || ((menu || page == "help") && multiplayer == null), accessibility = accessibility, outfit = startMenu.outfitFor(player!!), restoredWorld = restoredWorld, saves = saves)
                Button(onClick = { menu = true }, modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(8.dp)) {
                    GameIcon(GameIconKind.Menu, tr("Game menu"))
                }
            }
        }
    }
    if(slotPicker) AlertDialog(onDismissRequest={slotPicker=false;multiplayer?.stop();multiplayer=null},title={Text(tr("Choose a save slot"))},
        text={Column { repeat(3) { index ->
            TextButton(onClick={
                saveSlot=index;slotPicker=false
                if(occupiedSlots[index]) saveChoice=true else { restoredWorld=null;config=pendingWorld!!.copy(portalSeed=0);player=pendingPlayer }
            }) { Text(tr("Slot {0}",index+1)+" · "+tr(if(occupiedSlots[index]) "Your saved world" else "Empty slot")) }
        } }},confirmButton={TextButton(onClick={slotPicker=false;multiplayer?.stop();multiplayer=null}) {Text(tr("Cancel"))}})
    if(loadingWorld) AlertDialog(onDismissRequest={},title={Text(tr("Loading world…"))},text={CircularProgressIndicator()},confirmButton={})
    if(saveChoice) AlertDialog(onDismissRequest={saveChoice=false;multiplayer?.stop();multiplayer=null},title={Text(tr("Slot {0}",saveSlot+1))},
        text={Text(tr("Continue your exploration or start a new world."))},
        confirmButton={TextButton(onClick={
            saveChoice=false;loadingWorld=true
            scope.launch {
                runCatching { saves.load() }.onSuccess { restoredWorld=it;config=it.config;player=pendingPlayer }
                    .onFailure { sessionNotice=tr("Could not load world. Your save has been kept.") }
                loadingWorld=false
            }
        }) {Text(tr("Continue"))}},
        dismissButton={TextButton(onClick={saveChoice=false;replaceWorld=true}) {Text(tr("New world"))}})
    if(replaceWorld) AlertDialog(onDismissRequest={replaceWorld=false;saveChoice=true},title={Text(tr("Replace saved world?"))},
        text={Text(tr("Starting a new world replaces your current exploration save."))},
        confirmButton={TextButton(onClick={replaceWorld=false;restoredWorld=null;config=pendingWorld!!.copy(portalSeed=0);player=pendingPlayer}) {Text(tr("New world"))}},
        dismissButton={TextButton(onClick={replaceWorld=false;saveChoice=true}) {Text(tr("Cancel"))}})
    if (menu) AlertDialog(
        onDismissRequest = { menu = false }, title = { Text(if (multiplayer == null) tr("Paused") else tr("Game menu")) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(if (multiplayer == null) tr("Take your time. Your game is paused.") else tr("Multiplayer continues while this menu is open."))
            Text(tr(if(config.exploration && host) "Your exploration world saves automatically on this device. The host keeps multiplayer worlds." else if(config.exploration) "Exploration worlds are saved on the host device. Other game modes start fresh." else "Leaving starts a fresh world next time. Your settings are saved."))
            if(config.exploration && host) {
                if(saves.status.isNotEmpty()) Text(tr(saves.status))
                TextButton(onClick={saves.saveNow()}) { Text(tr("Save world")) }
            }
            TextButton(onClick = { menu = false; page = "help" }) { Text(tr("How to play")) }
            AudioSettings(audioOptions, ::updateAudio)
            AccessibilitySettings(accessibility, ::updateAccessibility)
        } },
        confirmButton = { TextButton(onClick = { menu = false }) { Text(tr("Continue")) } },
        dismissButton = { TextButton(onClick = { scope.launch {
            if(config.exploration && host) {
                saves.saveNow()?.join()
                if(saves.status!="World saved")return@launch
            }
            multiplayer?.stop();multiplayer=null;startMenu.resetConnection();player=null;menu=false
        } }) { Text(tr("Leave game")) } }
    )
    if (page == "help") AlertDialog(onDismissRequest = { page = "play" }, title = { Text(tr("Find Mammy before nightfall")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HelpRow(GameIconKind.Rotate, tr("Turn the world to explore both directions."))
            HelpRow(GameIconKind.Rotate, tr("Use the left stick to move. Jump and aim with the right controls."))
            Text(tr("Button controls and reduced motion are available in Settings and the pause menu. Screen-reader actions on the joystick can also step left or right and change aim."))
            HelpRow(GameIconKind.Potion, tr("Break blocks, collect supplies, then choose an item from your bag to use or place."))
            HelpRow(GameIconKind.Map, tr("Break the dangerous bed for a map. Find Mammy for five peaceful minutes!"))
            Text(tr("Exploration worlds are saved on the host device. Other game modes start fresh."))
        }
    }, confirmButton = { TextButton(onClick = { page = "play" }) { Text(tr("Let's play")) } })
    sessionNotice?.let { message -> AlertDialog(onDismissRequest = { sessionNotice = null }, title = { Text(tr("Session ended")) }, text = { Text(message) }, confirmButton = { TextButton(onClick = { sessionNotice = null }) { Text(tr("OK")) } }) }
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
