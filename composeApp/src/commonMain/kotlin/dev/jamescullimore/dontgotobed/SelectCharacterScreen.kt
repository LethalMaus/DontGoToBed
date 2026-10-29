package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dontgotobed.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val MenuInk = Color(0xFF10243A)
private val MenuPanel = Color(0xFF203C52)
private val MenuCream = Color(0xFFFFF1CC)
private val MenuGold = Color(0xFFFFCE66)
private enum class MenuPanelChoice { Characters, Mode, Multiplayer }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SelectCharacterScreen(
    localIp: String,
    mp: MultiplayerManager?,
    setMp: (MultiplayerManager?) -> Unit,
    worldConfig: WorldConfig,
    state: StartMenuState,
    onPlayerChange: (Player) -> Unit,
    onOutfitChange: (Player, PlayerOutfit) -> Unit,
    onOpenWorlds: () -> Unit,
    onModeChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onHelp: () -> Unit,
    animate: Boolean,
    foreground: Boolean,
    onSelect: (Player, Boolean, String?, WorldConfig) -> Unit
) {
    var panel by remember { mutableStateOf<MenuPanelChoice?>(null) }
    val scope = rememberCoroutineScope()
    val currentManager by rememberUpdatedState(mp)
    val focus = LocalFocusManager.current
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var nearbyGames by remember { mutableStateOf(emptyList<NearbyGame>()) }
    var discoveryFailed by remember { mutableStateOf(false) }
    var scanAttempt by remember { mutableStateOf(0) }
    var manualAddress by remember { mutableStateOf(false) }
    val browsing = foreground && panel == MenuPanelChoice.Multiplayer && state.session == SessionChoice.Join && !state.connected && !state.connecting
    DisposableEffect(browsing, scanAttempt) {
        nearbyGames = emptyList()
        discoveryFailed = false
        var active = true
        val discovery = if (browsing) LanDiscovery().also { discovery ->
            discovery.browse({ games -> scope.launch { if (active) nearbyGames = games } },
                { scope.launch { if (active) discoveryFailed = true } })
        } else null
        onDispose { active = false; discovery?.stop() }
    }
    fun connectToHost(port: Int = MultiplayerManager.DEFAULT_PORT) {
        if(state.connecting || state.connected || state.joinIp.isBlank()) return
        focus.clearFocus();keyboard?.hide();state.connecting=true;state.error=null
        val manager=MultiplayerManager(false,state.joinIp.trim(),port=port,initialListener=object : MultiplayerListenerAdapter() {})
        setMp(manager);manager.start()
    }
    DisposableEffect(mp) {
        mp?.updateListener(object : MultiplayerListenerAdapter() {
            override fun onConnectionChanged(connected: Boolean) { scope.launch {
                if (mp !== currentManager) return@launch
                state.connected = connected
                if (connected) { state.connecting = false; state.error = null }
                if (!connected) state.peer = null
            } }
            override fun onPeerSelectedPlayer(id: Int, name: String) { scope.launch {
                if (mp !== currentManager) return@launch
                if (id != mp.getSelfId()) state.peer = name
            } }
        })
        // GameScreen replaces this listener on launch. The app owns session disposal.
        onDispose { }
    }
    LaunchedEffect(mp, state.connecting) {
        if (mp != null && state.connecting) {
            delay(12_000)
            if (!state.connected) {
                setMp(null)
                state.connecting = false
                state.error = tr("Couldn't connect. Check the host address and that both devices are on the same Wi-Fi, then try again.")
            }
        }
    }
    fun chooseSession(choice: SessionChoice) {
        setMp(null)
        state.resetConnection()
        state.session = choice
        manualAddress = false
        if (choice == SessionChoice.Host) {
            val manager = MultiplayerManager(true, MultiplayerManager.DEFAULT_HOST_IP,
                initialListener = object : MultiplayerListenerAdapter() {})
            setMp(manager)
            manager.start()
        }
    }
    val sessionLabel = when (state.session) {
        SessionChoice.Solo -> tr("Solo adventure")
        SessionChoice.Host -> if (state.connected) tr("Friends connected") else tr("Hosting on Wi-Fi")
        SessionChoice.Join -> if (state.connected) tr("Connected to host") else tr("Join a friend")
    }
    GameBackHandler(panel != null) { panel = null }
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF152F4A), MenuInk)))) {
        val landscape = maxWidth >= 600.dp && maxWidth > maxHeight
        val compact = landscape && maxHeight < 500.dp
        MenuLandscape(Modifier.fillMaxSize())
        val safe = WindowInsets.safeDrawing.union(WindowInsets.displayCutout).asPaddingValues()
        val direction = LocalLayoutDirection.current
        val side = maxOf(safe.calculateLeftPadding(direction), safe.calculateRightPadding(direction)) + 24.dp
        val vertical = maxOf(safe.calculateTopPadding(), safe.calculateBottomPadding()) + 16.dp
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = side, vertical = vertical), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 900.dp).fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp, Alignment.CenterVertically)) {
            Row(Modifier.widthIn(max = 1000.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("DON'T GO TO BED", color = MenuGold, fontWeight = FontWeight.Black,
                        fontSize = if (compact) 26.sp else 34.sp, lineHeight = if (compact) 28.sp else 36.sp)
                    if (!compact) Text(tr("One more adventure together."), color = MenuCream, fontSize = 14.sp)
                }
                IconButton(onClick = onHelp, modifier = Modifier.size(48.dp).background(MenuPanel, RoundedCornerShape(16.dp))) {
                    GameIcon(GameIconKind.Help, tr("How to play"), tint = MenuCream)
                }
            }
            val hero: @Composable () -> Unit = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(if (compact) 132.dp else 218.dp), contentAlignment = Alignment.BottomCenter) {
                        CharacterPreview(state.player, animate && panel == null, Modifier.width(if (compact) 125.dp else 170.dp).fillMaxHeight(), outfit = state.outfitFor(state.player))
                        Image(painterResource(Res.drawable.cat_walk_right), null,
                            modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp).size(if (compact) 48.dp else 70.dp))
                    }
                    Text(state.player.displayName, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MenuCream)
                    Text(tr("Ready for an adventure"), fontSize = 12.sp, color = Color(0xFFABC4D7))
                }
            }
            val controls: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MenuTile(tr("Characters"), state.player.displayName, GameIconKind.Character, Modifier.weight(1f), compact) { panel = MenuPanelChoice.Characters }
                        MenuTile(tr("Worlds"), if (state.session == SessionChoice.Join) tr("Host chooses") else if (worldConfig.exploration) tr("Portal worlds · Preview") else WorldLayouts.name(worldConfig.horizontalSize), GameIconKind.Worlds, Modifier.weight(1f), compact, onOpenWorlds)
                        MenuTile(tr("Mode"), tr(if(worldConfig.unlimited) "Unlimited exploration" else "Find Mammy"), GameIconKind.Flag, Modifier.weight(1f), compact) { panel = MenuPanelChoice.Mode }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MenuTile(tr("Multiplayer"), sessionLabel, GameIconKind.Friends, Modifier.weight(1f), compact) { panel = MenuPanelChoice.Multiplayer }
                        MenuTile(tr("Settings"), tr("Sound & controls"), GameIconKind.Settings, Modifier.weight(1f), compact, onOpenSettings)
                    }
                    Button(onClick = {
                        mp?.sendSelectedPlayer(state.player.name)
                        onSelect(state.player, state.session != SessionChoice.Join,
                            state.joinIp.trim().takeIf { state.session == SessionChoice.Join }, worldConfig)
                    }, enabled = canStartSession(state.session, mp != null, state.connected),
                        colors = ButtonDefaults.buttonColors(containerColor = MenuGold, contentColor = MenuInk),
                        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        GameIcon(GameIconKind.Play, null)
                        Spacer(Modifier.width(10.dp))
                        Text(when (state.session) { SessionChoice.Solo -> tr("LET'S PLAY"); SessionChoice.Host -> tr("START HOSTED GAME"); SessionChoice.Join -> tr("JOIN ADVENTURE") }, fontWeight = FontWeight.Black, fontSize = 17.sp)
                    }
                    if (!compact) Text(tr(if(worldConfig.exploration) "Exploration lets you continue a saved world or start a new one." else "A fresh world each time. Your choices are remembered."), color = Color(0xFFABC4D7), fontSize = 12.sp)
                }
            }
            if (landscape) Row(Modifier.widthIn(max = 1000.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(.75f)) { hero() }
                Box(Modifier.weight(1.3f)) { controls() }
            } else {
                hero()
                Box(Modifier.widthIn(max = 560.dp).fillMaxWidth()) { controls() }
            }
        }
        }
    }
    when (panel) {
        MenuPanelChoice.Characters -> MenuDialog(tr("Choose your adventurer"), { panel = null }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Player.entries.forEach { player ->
                    val chosen = state.player == player
                    Surface(onClick = { onPlayerChange(player); mp?.sendSelectedPlayer(player.name) },
                        shape = RoundedCornerShape(16.dp), color = if (chosen) Color(0xFF385A61) else MenuInk,
                        border = BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) MenuGold else Color(0xFF496478)),
                        modifier = Modifier.weight(1f).semantics { selected = chosen; contentDescription = tr("Choose {0}", player.displayName) }) {
                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CharacterPreview(player, animate, Modifier.fillMaxWidth().height(80.dp), outfit = state.outfitFor(player))
                            Text(player.displayName, color = MenuCream, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameIcon(GameIconKind.Outfit, null, tint = MenuGold)
                Text(tr("Outfit"), color = MenuCream, fontWeight = FontWeight.Bold)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                outfitsFor(state.player).forEach { outfit ->
                    FilterChip(colors = FilterChipDefaults.filterChipColors(containerColor = MenuInk, labelColor = MenuCream, selectedContainerColor = MenuGold, selectedLabelColor = MenuInk), selected = state.outfitFor(state.player) == outfit,
                        onClick = { onOutfitChange(state.player, outfit) },
                        label = { Text(outfitLabel(state.player, outfit)) })
                }
            }
            Text(tr("Just a new look. Same abilities. Saved for this character."), color = MenuCream, fontSize = 12.sp)
        }
        MenuPanelChoice.Mode -> MenuDialog(tr("Game mode"), { panel = null }) {
            for(unlimited in listOf(false,true)) {
                FilterChip(selected=worldConfig.unlimited==unlimited,onClick={onModeChange(unlimited)},
                    enabled=state.session!=SessionChoice.Join,
                    label={Text(tr(if(unlimited) "Unlimited exploration" else "Find Mammy"))})
            }
            Text(tr(if(worldConfig.unlimited) "Explore, build and fight without a time limit. Mammy is optional." else "Find Mammy before nightfall. Finding her earns five peaceful minutes before the next search."),color=MenuCream)
            if(state.session==SessionChoice.Join) Text(tr("Your host chooses the world. Join their game to explore it together."),color=MenuCream)
            Text(tr("Play solo or with friends on the same Wi-Fi. Exploration worlds use the host’s save slots."),color=MenuCream)
        }
        MenuPanelChoice.Multiplayer -> MenuDialog(tr("Play together"), { panel = null },
            actionLabel=if(state.session==SessionChoice.Join && !state.connected && manualAddress) if(state.connecting) tr("Connecting…") else tr("Connect") else tr("Done"),
            actionEnabled=!manualAddress || state.session!=SessionChoice.Join || state.connected || (!state.connecting && state.joinIp.isNotBlank()),
            onAction={if(state.session==SessionChoice.Join && !state.connected && manualAddress) connectToHost() else panel=null}) {
            Text(tr("Use the same Wi-Fi. One player hosts; the others join that device."), color = MenuCream)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SessionChoice.entries.forEach { choice ->
                    FilterChip(colors = FilterChipDefaults.filterChipColors(containerColor = MenuInk, labelColor = MenuCream, selectedContainerColor = MenuGold, selectedLabelColor = MenuInk), selected = state.session == choice, onClick = { if (state.session != choice) chooseSession(choice) }, label = { Text(tr(choice.name)) })
                }
            }
            when (state.session) {
                SessionChoice.Solo -> Text(tr("Just you and a new adventure. Your multiplayer connection is closed."), color = MenuCream)
                SessionChoice.Host -> {
                    Text(tr("Nearby game: {0}", mp?.discoveryName.orEmpty()), color = MenuGold, fontWeight = FontWeight.Bold)
                    Text(tr("Friends can find this game under Join on the same Wi-Fi."), color = MenuCream)
                    Text(tr("Your host address"), color = MenuCream)
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(localIp, color = MenuGold, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(if (state.connected) tr("A friend is connected. Start when you're ready.") else tr("Waiting for friends. You can start now and they can join later."), color = MenuCream,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                SessionChoice.Join -> {
                    if (!state.connected && !state.connecting) {
                        Text(tr("Nearby games"), color = MenuGold, fontWeight = FontWeight.Bold)
                        Text(tr(if (discoveryFailed) "Discovery unavailable. Allow local network access and try again, or enter the host address."
                            else if (nearbyGames.isEmpty()) "Looking for games… Ask a friend to choose Host on the same Wi-Fi."
                            else "Tap a game to connect."), color = MenuCream,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        nearbyGames.forEach { game ->
                            OutlinedButton(onClick = { state.joinIp = game.address; connectToHost(game.port) }, modifier = Modifier.fillMaxWidth()) {
                                Text(game.name, color = MenuGold)
                            }
                        }
                        FlowRow {
                            TextButton(onClick = { scanAttempt++ }) { Text(tr("Search again"), color = MenuGold) }
                            TextButton(onClick = { manualAddress = !manualAddress }) { Text(tr("Enter address manually"), color = MenuGold) }
                        }
                    }
                    if (manualAddress) OutlinedTextField(value = state.joinIp, onValueChange = { state.joinIp = it; state.error = null },
                        enabled = !state.connected && !state.connecting, singleLine = true, label = { Text(tr("Host address")) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MenuCream, unfocusedTextColor = MenuCream, focusedLabelColor = MenuGold, unfocusedLabelColor = MenuCream),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus();keyboard?.hide() }))
                    if (state.connecting || state.connected) Text(if (state.connected) tr("Connected. Close this panel and join the adventure.") else tr("Looking for your friend…"), color = MenuCream,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    if (mp != null) TextButton(onClick = { setMp(null); state.connected = false; state.connecting = false; state.peer = null }) { Text(tr("Disconnect"), color = MenuGold) }
                }
            }
            state.peer?.let { Text(tr("Friend selected: {0}", Player.entries.firstOrNull { player -> player.name == it }?.displayName ?: it), color = MenuGold) }
            state.error?.let { Text(it, color = Color(0xFFFFBCAD), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        }
        null -> Unit
    }
}

@Composable
private fun MenuTile(title: String, detail: String, icon: GameIconKind, modifier: Modifier, compact: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MenuPanel, shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF3C5B70)), modifier = modifier.heightIn(min = if (compact) 68.dp else 86.dp)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = if (compact) 7.dp else 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            GameIcon(icon, null, tint = MenuGold)
            Text(title, color = MenuCream, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (!compact) Text(detail, color = Color(0xFFABC4D7), fontSize = 11.sp, lineHeight = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun MenuDialog(title: String, onClose: () -> Unit, actionLabel: String = tr("Done"), actionEnabled: Boolean = true, onAction: () -> Unit = onClose, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp), contentAlignment = Alignment.Center) {
            Surface(color = MenuPanel, shape = RoundedCornerShape(24.dp), modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, color = MenuGold, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        content()
                    }
                    Button(onClick = onAction, enabled=actionEnabled, modifier = Modifier.align(Alignment.End), colors = ButtonDefaults.buttonColors(containerColor = MenuGold, contentColor = MenuInk)) { Text(actionLabel) }
                }
            }
        }
    }
}

@Composable
private fun MenuLandscape(modifier: Modifier) {
    Canvas(modifier) {
        val unit = 24.dp.toPx()
        drawCircle(Color(0xFFFFE6A5).copy(alpha = .13f), unit * 2.2f, Offset(size.width * .83f, size.height * .17f))
        repeat(15) { i ->
            val x = ((i * 137 + 41) % 997) / 997f * size.width
            val y = ((i * 79 + 19) % 431) / 431f * size.height * .64f
            drawRect(Color(0xFFFFE6A5).copy(alpha = .25f), Offset(x, y), Size(3.dp.toPx(), 3.dp.toPx()))
        }
        var x = 0f
        var i = 0
        while (x < size.width) {
            val h = unit * (1 + (i * 7 % 3))
            drawRect(Color(0xFF204445), Offset(x, size.height - h), Size(unit, h))
            drawRect(Color(0xFF3B685C), Offset(x, size.height - h), Size(unit, 5.dp.toPx()))
            x += unit; i++
        }
    }
}
