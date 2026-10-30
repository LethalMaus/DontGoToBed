package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.layout.PaddingValues

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.semantics.semantics
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.DrawableResource
import dontgotobed.composeapp.generated.resources.*
import dontgotobed.composeapp.generated.resources.Res
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jamescullimore.dontgotobed.Direction.Companion.isFacingRight
import dev.jamescullimore.dontgotobed.ui.theme.DontGoToBedTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

private enum class ActionIconKind { Jump, Hit, Place, Use }

@Composable
private fun ActionIcon(kind: ActionIconKind, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(34.dp)) {
        val stroke = Stroke(width = size.minDimension * 0.11f, cap = StrokeCap.Round)
        val white = Color.White
        when (kind) {
            ActionIconKind.Jump -> {
                drawLine(white, Offset(size.width * 0.5f, size.height * 0.82f), Offset(size.width * 0.5f, size.height * 0.18f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(white, Offset(size.width * 0.5f, size.height * 0.18f), Offset(size.width * 0.25f, size.height * 0.42f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(white, Offset(size.width * 0.5f, size.height * 0.18f), Offset(size.width * 0.75f, size.height * 0.42f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(white.copy(alpha = 0.7f), Offset(size.width * 0.25f, size.height * 0.86f), Offset(size.width * 0.75f, size.height * 0.86f), strokeWidth = stroke.width * 0.8f, cap = StrokeCap.Round)
            }
            ActionIconKind.Hit -> {
                drawLine(white, Offset(size.width * 0.72f, size.height * 0.18f), Offset(size.width * 0.25f, size.height * 0.78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(white, Offset(size.width * 0.48f, size.height * 0.58f), Offset(size.width * 0.72f, size.height * 0.82f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(white.copy(alpha = 0.75f), Offset(size.width * 0.61f, size.height * 0.38f), Offset(size.width * 0.83f, size.height * 0.16f), strokeWidth = stroke.width * 0.8f, cap = StrokeCap.Round)
            }
            ActionIconKind.Place -> {
                val gap = size.minDimension * 0.06f
                val cell = (size.minDimension - gap * 4f) / 3f
                for (row in 0..2) {
                    for (col in 0..2) {
                        drawRect(
                            color = white,
                            topLeft = Offset(gap + col * (cell + gap), gap + row * (cell + gap)),
                            size = Size(cell, cell),
                            style = Stroke(width = stroke.width * 0.55f)
                        )
                    }
                }
            }
            ActionIconKind.Use -> {
                drawCircle(white, radius = size.minDimension * 0.34f, center = center, style = Stroke(width = stroke.width))
                drawLine(white, Offset(size.width * 0.5f, size.height * 0.28f), Offset(size.width * 0.5f, size.height * 0.72f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(white, Offset(size.width * 0.28f, size.height * 0.5f), Offset(size.width * 0.72f, size.height * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
internal fun GameScreen(
    player: Player,
    initialIsHostSelected: Boolean? = null,
    initialHostIp: String = MultiplayerManager.DEFAULT_HOST_IP,
    worldConfig: WorldConfig = WorldConfig(),
    mp: MultiplayerManager?,
    // Test launch only; Android entry point rejects this option in non-debuggable builds.
    debugDropOnMammy: Boolean = false,
    debugBed: Int = 0,
    debugAdventure: Int = 0,
    paused: Boolean = false,
    accessibility: AccessibilityOptions = AccessibilityOptions(),
    outfit: PlayerOutfit = PlayerOutfit.Original,
    restoredWorld: SavedWorld? = null,
    saves: WorldSaveController? = null
) {
    var runId by remember { mutableIntStateOf(0) }
    var sessionConfig by remember(worldConfig) { mutableStateOf(if (worldConfig.exploration) worldConfig.copy(horizontalSize = ExplorationWorld.SIZE, portalSeed = if (worldConfig.portalSeed == 0 && restoredWorld == null && initialIsHostSelected != false && debugAdventure == 0) kotlin.random.Random.nextInt(1, Int.MAX_VALUE) else worldConfig.portalSeed) else worldConfig) }
    androidx.compose.runtime.key(runId, sessionConfig) {
        GameSession(player, initialIsHostSelected, initialHostIp, sessionConfig, mp,
            debugDropOnMammy && runId == 0, if (runId == 0) debugBed else 0, debugAdventure, paused, onDeath = { runId++ }, onWorldConfig = { sessionConfig = it }, accessibility = accessibility, outfit = outfit, restoredWorld = restoredWorld, saves = saves)
    }
}

@Composable
private fun GameSession(
    player: Player, initialIsHostSelected: Boolean?, initialHostIp: String,
    worldConfig: WorldConfig, mp: MultiplayerManager?, debugDropOnMammy: Boolean, debugBed: Int, debugAdventure: Int,
    paused: Boolean, onDeath: () -> Unit, onWorldConfig: (WorldConfig) -> Unit, accessibility: AccessibilityOptions, outfit: PlayerOutfit, restoredWorld: SavedWorld?, saves: WorldSaveController?
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val systemReducedMotion = rememberSystemReducedMotion()
        val reduceMotion = accessibility.reduceMotion || systemReducedMotion
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        // Lightweight state holder moved out of the composable to simplify this file
        val audio = LocalGameAudio.current
        val vm = remember { GameViewModel() }
        val equipment = remember { PlayerEquipment() }
        val shocks = remember { ShockGame() }
        val combatMotion = remember { CombatMotion() }
        val peerBlocking = remember { androidx.compose.runtime.mutableStateMapOf<Int,Boolean>() }
        val peerWorldAxes = remember { androidx.compose.runtime.mutableStateMapOf<Int, WorldAxis>() }
        val peerEquipment = remember { androidx.compose.runtime.mutableStateMapOf<Int, EquipmentLoadout>() }
        androidx.compose.runtime.DisposableEffect(vm) { onDispose { vm.clear() } }

        var isHostSelected by remember { mutableStateOf<Boolean?>(initialIsHostSelected) }
        androidx.compose.runtime.DisposableEffect(vm, audio, mp, isHostSelected) {
            vm.onSound = { effect ->
                audio?.play(effect)
                if (isHostSelected == true) mp?.sendRoundEvent("SOUND;${effect.name}")
            }
            onDispose { vm.onSound = {} }
        }
        var connectionStatus by remember { mutableStateOf(false) }
        var hostIp by remember { mutableStateOf(initialHostIp) }

        val viewportHeight = maxHeight
        val hudSafeHeight = maxHeight - with(density) {
            (WindowInsets.safeDrawing.getTop(this) + WindowInsets.safeDrawing.getBottom(this)).toDp()
        }
        // The map panel starts below the top utility controls.
        val mapTop = 84.dp
        val unit = 15.dp
        val foundationRows = WorldViewport.foundationRows(maxWidth.value, maxHeight.value)
        val foundationHeight = unit * (WorldViewport.BLOCK_CELLS * foundationRows)
        val step = 16.dp
        val playerWidth = unit * 2
        val playerHeight = unit * 3
        var cameraZoom by remember { mutableFloatStateOf(1f) }
        val screenWidth = maxWidth / cameraZoom

        var joystickX by remember { mutableFloatStateOf(0f) }
        var joystickY by remember { mutableFloatStateOf(0f) }
        var showWalkingPose by remember { mutableStateOf(false) }
        var previewSelectedAimCol by remember { mutableStateOf<Int?>(null) }
        var previewSelectedAimRow by remember { mutableStateOf<Int?>(null) }
        var previewSelectedFacing by remember { mutableStateOf<Direction?>(null) }

        val unitPx = with(density) { unit.toPx() }
        val playerWidthPx = with(density) { playerWidth.toPx() }
        val playerHeightPx = with(density) { playerHeight.toPx() }

        // Inform the VM about the player's horizontal speed (tiles/sec) so zombies can be slightly slower
        LaunchedEffect(unitPx) {
            val maxSpeedPxPerSec = with(density) { 420.dp.toPx() }
            val playerTilesPerSec = if (unitPx > 0f) maxSpeedPxPerSec / unitPx else 1f
            vm.setPlayerSpeedTilesPerSec(playerTilesPerSec)
        }

        // (moved below state declarations where helpers/vars are available)

        var bowSequence by remember { mutableIntStateOf(0) }
        var drawingBow by remember { mutableStateOf(false) }
        var bowFacingRight by remember { mutableStateOf(false) }
        LaunchedEffect(bowSequence) {
            if (bowSequence > 0) { drawingBow = true; delay(220); drawingBow = false }
        }
        var consumeSequence by remember { mutableIntStateOf(0) }
        var consuming by remember { mutableStateOf(false) }
        LaunchedEffect(consumeSequence) {
            if (consumeSequence > 0) { consuming = true; delay(850); consuming = false }
        }
        var playerHp by remember { mutableIntStateOf(5) }
        var previousHp by remember { mutableIntStateOf(5) }
        LaunchedEffect(playerHp) {
            if (playerHp < previousHp) audio?.play(SoundEffect.Hurt)
            previousHp = playerHp
        }
        var playerFlashUntil by remember { mutableLongStateOf(0L) }

        val inventorySlots = vm.inventorySlots
        val inventoryStackLimit = vm.inventoryStackLimit
        val inventory = vm.inventory
        var isInventoryOpen by remember { mutableStateOf(false) }
        var isCraftingOpen by remember { mutableStateOf(false) }
        var craftingPending by remember { mutableStateOf(false) }
        var isShapePickerOpen by remember { mutableStateOf(false) }

        fun playerSpriteResource(character: Player, walking: Boolean, appearance: PlayerOutfit = outfit): DrawableResource =
            characterSide(character, walking, appearance)

        fun playerSpriteScaleX(character: Player, facingRight: Boolean): Float {
            return when (character) {
                Player.Leo -> if (facingRight) -1f else 1f
                Player.Ian, Player.Papa -> if (facingRight) -1f else 1f
            }
        }

        val zombies by vm.zombies.collectAsState()
        val skeletons by vm.skeletons.collectAsState()
        val potions by vm.potions.collectAsState()

        // Listen for zombie attack damage events from ViewModel
        LaunchedEffect(Unit) {
            vm.playerDamage.collect { dmg ->
                if (dmg > 0) {
                    playerHp = (playerHp - equipment.damage(dmg)).coerceAtLeast(0)
                    playerFlashUntil = currentTimeMillis() + 150L
                }
            }
        }

        // (moved below after tileMap and movement vars are available)

        // Build sample map with 3x3 blocks on micro-grid (scale prior grid by 3)
        val tileMap by remember(worldConfig) {
            mutableStateOf(
                run {
                    val w = worldConfig.horizontalSize
                    val h = worldConfig.height
                    val map = TileMap(w, h, exploration = worldConfig.exploration, portalSeed = worldConfig.portalSeed)
                    // Ground exists through every longitude, so switching movement axis is safe.
                    if (restoredWorld != null && initialIsHostSelected != false) map.applyNetworkBlocks(restoredWorld.blocks.map { it.id to it },reset=true)
                    else if (worldConfig.exploration) ExplorationWorld.populate(map) else WorldLayouts.populate(map)
                    map
                }
            )
        }

        // moved below: requires playerWorldXDp/heightPx/isJumping which are declared later

        // Helpers: convert world x dp to pixel and back (must be declared before any usage)
        fun xDpToPx(x: Dp): Float = with(density) { x.toPx() }
        fun pxToDp(px: Float): Dp = with(density) { px.toDp() }

        // Player state
        var playerWorldXDp by remember { mutableStateOf(if (worldConfig.exploration) unit * 50 else 0.dp) } // position along the currently active horizontal axis
        var playerOtherAxisDp by remember { mutableStateOf(unit * 10) } // Start beside the first district.
        var activeWorldAxis by remember { mutableStateOf(WorldAxis.Latitude) }
        val playerLatitude by rememberUpdatedState(floor(xDpToPx(if (activeWorldAxis == WorldAxis.Latitude) playerWorldXDp else playerOtherAxisDp) / unitPx).toInt())
        val playerLongitude by rememberUpdatedState(floor(xDpToPx(if (activeWorldAxis == WorldAxis.Longitude) playerWorldXDp else playerOtherAxisDp) / unitPx).toInt())
        val currentBiome by rememberUpdatedState(if (worldConfig.exploration) ExplorationWorld.biome(playerLatitude, playerLongitude) else null)
        val centerX = if (currentBiome != null) {
            val origin = ExplorationWorld.origin(currentBiome!!).along(activeWorldAxis)
            val length = if (activeWorldAxis == WorldAxis.Latitude) ExplorationWorld.REGION else ExplorationWorld.REGION_DEPTH
            val left = unit * (origin + ExplorationWorld.BORDER)
            val right = unit * (origin + length - ExplorationWorld.BORDER)
            playerWorldXDp - (playerWorldXDp - (screenWidth - playerWidth) / 2).coerceIn(left, maxOf(left, right - screenWidth))
        } else (screenWidth - playerWidth) / 2
        val specials = remember(tileMap) { SpecialGame(tileMap) }
        val bedRound = remember(tileMap) { BedRoundGame(tileMap, vm.bag, send = { mp?.sendRoundEvent(it) }, unlimited = worldConfig.unlimited) }
        var cycle by bedRound::cycle
        var mammyLocation by bedRound::mammy
        androidx.compose.runtime.SideEffect { audio?.scene = if (cycle.hunting) MusicScene.Panic else MusicScene.Search }
        val hasFoundMammy = cycle.safe
        val mammyGoalCol by rememberUpdatedState(mammyLocation.along(activeWorldAxis))
        val mammyGoalBottomRow = mammyLocation.row
        val mammyVisible by rememberUpdatedState(!cycle.safe && (!worldConfig.exploration ||
            ExplorationWorld.biome(mammyLocation.latitude, mammyLocation.longitude) == currentBiome) && mammyLocation.intersects(
            activeWorldAxis,
            ((floor(xDpToPx(playerOtherAxisDp) / unitPx).toInt() % tileMap.width) + tileMap.width) % tileMap.width,
            tileMap.width, 2
        ))
        fun mammyOverlapsColumns(cols: IntRange): Boolean = mammyVisible && cols.any {
            val wrapped = ((it % tileMap.width) + tileMap.width) % tileMap.width
            wrapped == mammyGoalCol || wrapped == (mammyGoalCol + 1) % tileMap.width
        }
        var isAxisTransitioning by remember { mutableStateOf(false) }
        val paperProgress = remember { Animatable(1f) }
        var turnFromAxis by remember { mutableStateOf(WorldAxis.Latitude) }
        val turnDirection = if (turnFromAxis == WorldAxis.Latitude) 1f else -1f
        val paperFrame = PaperTurnFrame(if (isAxisTransitioning && !reduceMotion) paperProgress.value else 1f)
        var selectedShape by remember { mutableStateOf(BlockShape.Square) }
        val skyColor = currentBiome?.let { androidx.compose.ui.graphics.lerp(it.sky, Color(0xFF101624), cycle.darkness * 0.8f) } ?: skyForAxis(activeWorldAxis, cycle.darkness)
        var heightPx by remember { mutableStateOf(0f) } // vertical bottom height in px above ground
        var isJumping by remember { mutableStateOf(false) }
        val playableHeight = maxHeight-foundationHeight
        val cameraBottomPx by rememberUpdatedState(WorldCamera.bottom(heightPx,
            with(density) { (maxHeight - foundationHeight).toPx() },
            worldConfig.height * unitPx, cameraZoom))

        // Select the plane before rendering/physics observe this composition.
        run {
            val activeCell = floor(xDpToPx(playerWorldXDp) / unitPx).toInt()
            val fixedCell = floor(xDpToPx(playerOtherAxisDp) / unitPx).toInt()
            if (activeWorldAxis == WorldAxis.Latitude) tileMap.setActiveSlice(activeWorldAxis, activeCell, fixedCell)
            else tileMap.setActiveSlice(activeWorldAxis, fixedCell, activeCell)
        }

        var lastDirection by remember { mutableStateOf(Direction.Right) }
        var syncingWorld by remember { mutableStateOf(initialIsHostSelected == false) }
        val isPaused by rememberUpdatedState(paused || syncingWorld || specials.event?.intro == true || ((isInventoryOpen || isCraftingOpen) && mp == null))
        val movementClock = remember { PlayerPhysicsClock() }
        val verticalClock = remember { PlayerPhysicsClock() }
        val isParalyzed = shocks.active("P",(mp?.getSelfId() ?: 0).toLong())
        val controlsBlocked by rememberUpdatedState(isPaused || isCraftingOpen || isParalyzed || specials.event?.actor == (mp?.getSelfId() ?: 0))
        LaunchedEffect(isPaused) {
            while(!isPaused) { delay(16);combatMotion.tick(16) }
        }
        LaunchedEffect(isParalyzed) { if(isParalyzed) { joystickX=0f;joystickY=0f;equipment.blocking=false } }
        androidx.compose.runtime.DisposableEffect(isPaused, isAxisTransitioning) {
            movementClock.reset()
            verticalClock.reset()
            onDispose { }
        }
        androidx.compose.runtime.SideEffect { vm.paused = isPaused }
        LaunchedEffect(isPaused) { if (isPaused) { joystickX = 0f; joystickY = 0f; previewSelectedAimCol = null; previewSelectedAimRow = null; previewSelectedFacing = null } }
        var isMinimapOpen by remember { mutableStateOf(false) }
        val ownsMap = inventory.any { it.type == ItemType.Map && it.count > 0 }
        LaunchedEffect(ownsMap) { if (!ownsMap) isMinimapOpen = false }

        LaunchedEffect(connectionStatus, mp) {
            while (connectionStatus && isHostSelected == false) {
                delay(3000L)
                // The client may choose a character before the host attaches its gameplay listener.
                if (syncingWorld) mp?.sendRoundEvent("WORLD_READY;${tileMap.width};${tileMap.height};${worldConfig.exploration};${worldConfig.unlimited};${worldConfig.portalSeed}")
            }
        }

        // Remote peers map (id -> state)
        val peersState = vm.peersState

        // Lightweight notifications banner (join/leave/connection)
        var hintRequest by remember { mutableIntStateOf(0) }
        var showTemporaryHint by remember { mutableStateOf(false) }
        LaunchedEffect(hintRequest) {
            if (hintRequest > 0) { showTemporaryHint = true; delay(3500); showTemporaryHint = false }
        }
        val notices = vm.notices
        fun pushNotice(text: String) { vm.pushNotice(text) }

        bedRound.isHost = isHostSelected != false
        bedRound.selfId = if (isHostSelected == false) mp?.getSelfId() ?: -1 else 0
        bedRound.clearEnemies = { vm.clearEnemies() }
        bedRound.notify = { pushNotice(it) }
        bedRound.position = { id ->
            if (id == bedRound.selfId) WorldLocation(
                WorldRows.index(xDpToPx(if (activeWorldAxis == WorldAxis.Latitude) playerWorldXDp else playerOtherAxisDp), unitPx, tileMap.width),
                WorldRows.index(xDpToPx(if (activeWorldAxis == WorldAxis.Longitude) playerWorldXDp else playerOtherAxisDp), unitPx, tileMap.width),
                floor(heightPx / unitPx).toInt())
            else peersState[id]?.let { peer ->
                val lat = peer.latitude
                val lon = peer.longitude
                if (lat == null || lon == null) null else WorldLocation(lat, lon, floor(peer.targetHeightPx / unitPx).toInt())
            }
        }
        val alien = remember(tileMap) { AlienGame(tileMap) }
        val companion = remember(tileMap) { CompanionGame(tileMap) }
        val dog = remember(tileMap) { DogGame(tileMap) }
        val hazardDamage = remember(tileMap) { HazardDamageClock() }
        fun localRopePoint() = RopePoint(
            xDpToPx(if(activeWorldAxis==WorldAxis.Latitude) playerWorldXDp else playerOtherAxisDp)/unitPx,
            xDpToPx(if(activeWorldAxis==WorldAxis.Longitude) playerWorldXDp else playerOtherAxisDp)/unitPx,
            heightPx/unitPx)
        val hazardContacts by rememberUpdatedState(BiomeHazards.contacts(tileMap, localRopePoint()))
        var vineSwing by remember { mutableStateOf<VineSwing?>(null) }
        var vineStand by remember { mutableStateOf<SwingVine?>(null) }
        var vineRegrabAfter by remember { mutableLongStateOf(0L) }
        val peerSwings = remember { androidx.compose.runtime.mutableStateMapOf<Int,VineSwing>() }
        dog.isHost = isHostSelected != false
        dog.selfId = bedRound.selfId
        dog.players = { companion.players() }
        dog.send = { mp?.sendRoundEvent(it) }
        dog.found = { actor, point ->
            if(actor==bedRound.selfId || (!worldConfig.exploration || ExplorationWorld.biome(point)==currentBiome) &&
                kotlin.math.abs(wrappedDelta(localRopePoint().lat,point.lat,tileMap.width))<21 && kotlin.math.abs(wrappedDelta(localRopePoint().lon,point.lon,tileMap.depth))<21) {
                audio?.play(SoundEffect.Howl)
                pushNotice(tr("You found the coonhound! A new friend follows you."))
            }
        }
        companion.isHost = isHostSelected != false
        companion.selfId = bedRound.selfId
        companion.players = {
            buildMap {
                if(playerHp>0) put(bedRound.selfId,localRopePoint())
                peersState.forEach { (id,p) -> if(p.hp>0 && p.latitudeMilli!=null && p.longitudeMilli!=null)
                    put(id,RopePoint(p.latitudeMilli!!/1000f,p.longitudeMilli!!/1000f,p.targetHeightPx/unitPx)) }
            }
        }
        alien.isHost = isHostSelected != false
        alien.players = {
            companion.players().mapValues { (id,p) -> AlienPlayer(p,
                if(id==bedRound.selfId) activeWorldAxis else peerWorldAxes[id] ?: WorldAxis.Latitude,
                if(id==bedRound.selfId) lastDirection.isFacingRight() else peersState[id]?.facingRight ?: true) }
        }
        alien.send = { mp?.sendRoundEvent(it) }
        alien.notice = { pushNotice(it) }
        shocks.isHost=isHostSelected!=false
        shocks.send={ mp?.sendRoundEvent(it) }
        vm.isShocked={kind,id->shocks.active(kind,id)}
        alien.isShocked={shocks.active("E",0)}
        fun electricHit(id: Int,raw: Int=1) {
            val blocked=if(id==bedRound.selfId) equipment.loadout.shield && equipment.blocking
                else peerEquipment[id]?.shield==true && peerBlocking[id]==true
            if(blocked) return
            shocks.apply("P",id.toLong())
            if(id==bedRound.selfId) playerHp=(playerHp-equipment.damage(raw)).coerceAtLeast(0)
            else mp?.sendRoundEvent("DAMAGE;${nanoTime()};$id;$raw")
        }
        alien.damage = { id,raw -> electricHit(id,raw) }
        vm.electricPlayerHit={ electricHit(it) }
        vm.electricEnemyHit={kind,id->shocks.apply(kind,id)}
        vm.alienArrowHit={lat,lon,row,electric->
            val hit=alien.arrowHit(lat,lon,row,electric)
            if(hit && electric && alien.state?.hp!=0) shocks.apply("E",0)
            hit
        }
        specials.isHost=isHostSelected!=false
        specials.selfId=bedRound.selfId
        specials.send={mp?.sendRoundEvent(it)}
        specials.actors={companion.players().mapNotNull { (id,p) ->
            val selected=if(id==bedRound.selfId) player else peersState[id]?.selected
            selected?.let { id to SpecialActor(it,if(id==bedRound.selfId) outfit else peersState[id]?.outfit ?: PlayerOutfit.Original,p,
                if(id==bedRound.selfId) activeWorldAxis else peerWorldAxes[id] ?: WorldAxis.Latitude) }
        }.toMap()}
        specials.enemies={vm.worldEntities(activeWorldAxis,unitPx).filter { it.hp>0 && it.kind in listOf("Z","S") }.map {
            SpecialTarget(it.kind,it.id,RopePoint(it.latitude/1000f,it.longitude/1000f,it.height/1000f))
        } + listOfNotNull(alien.targetPoint?.let { SpecialTarget("E",0,it) })}
        specials.impact={kind,targets -> targets.forEach { target ->
            if(kind==SpecialKind.Drums) shocks.apply(target.kind,target.id,5000)
            else when(target.kind) {
                "Z"->vm.damageZombie(target.id,unitPx,if(kind==SpecialKind.Dog) 3 else 4)
                "S"->vm.damageSkeleton(target.id,unitPx,if(kind==SpecialKind.Dog) 3 else 4)
                "E"->alien.specialDamage(if(kind==SpecialKind.Dog) 3 else 4)
            }
        } }
        LaunchedEffect(specials) {
            while(true) { delay(16);if(!paused && !syncingWorld && !((isInventoryOpen || isCraftingOpen) && mp==null)) specials.tick(16) }
        }
        companion.hasRope = { id -> (if(id==bedRound.selfId) equipment.loadout else peerEquipment[id])?.hand==ItemType.Rope }
        companion.targetPoint = { t -> if(t.kind=="A") alien.targetPoint else vm.worldEntities(activeWorldAxis,unitPx).firstOrNull { it.kind==t.kind && it.id==t.id && it.hp>0 }
            ?.let { RopePoint(it.latitude/1000f,it.longitude/1000f,it.height/1000f) } }
        companion.send = { mp?.sendRoundEvent(it) }
        companion.notice = { pushNotice(it) }
        companion.prepareGuideBed = { bedRound.ensureGuideBed(it) }
        companion.moveTarget = { target,p ->
            if(target.kind=="P") {
                if(target.id.toInt()==bedRound.selfId) {
                    playerWorldXDp=unit*p.along(activeWorldAxis);playerOtherAxisDp=unit*p.other(activeWorldAxis)
                } else {
                    mp?.sendRoundEvent("ROPE_TUG;${nanoTime()};${target.id};${p.encode()}")
                    peersState[target.id.toInt()]?.let { peer -> peer.latitudeMilli=(p.lat*1000).toInt();peer.longitudeMilli=(p.lon*1000).toInt() }
                }
            } else if(target.kind=="A") alien.tug(p) else vm.tugEntity(target,p,activeWorldAxis,unitPx,::pxToDp)
        }
        LaunchedEffect(tileMap) {
            if(!worldConfig.exploration)return@LaunchedEffect
            var lastTick=nanoTime()
            while(true) {
                delay(if(vineSwing==null && vineStand==null && joystickY<=.2f) 50 else 16)
                val now=nanoTime();val dt=((now-lastTick)/1_000_000_000f).coerceIn(0f,.05f);lastTick=now
                if(isPaused) continue
                val up=if(controlsBlocked) 0f else joystickY
                val across=if(controlsBlocked) 0f else joystickX
                vineStand?.let { top ->
                    if(abs(localRopePoint().lat-top.latitude)>1f || abs(localRopePoint().lon-top.longitude)>1f || isJumping) vineStand=null
                    else if(up<-.2f) { vineStand=null;vineSwing=VineSwing(top,reach=top.topReach) }
                }
                if(vineSwing==null && vineStand==null && up>.2f && !isJumping && currentTimeMillis()>vineRegrabAfter) {
                    SwingVines.grab(localRopePoint(),tileMap)?.takeIf { !it.vine.swingable || activeWorldAxis==WorldAxis.Latitude }?.let { vineSwing=it }
                }
                val swing=vineSwing ?: continue
                if(playerHp<=0 || (swing.vine.swingable && activeWorldAxis!=WorldAxis.Latitude)) { vineSwing=null;continue }
                val clear:(RopePoint)->Boolean = { p ->
                    p.row>=3 && (0..1).all { dx -> (0..1).all { dz -> (0..2).none { dy ->
                        tileMap.getVoxel(floor(p.lat).toInt()+dx,floor(p.lon).toInt()+dz,floor(p.row).toInt()+dy)
                    } } }
                }
                // Vines deliberately pass through terrain: vertical climbing follows the vine.
                // Lateral swinging still tests solid world cells.
                val next=swing.climb(dt,up) { it.row in 3f..(tileMap.height-3f) }.advance(dt,across,clear)
                if(!next.vine.swingable && abs(across)>.2f) lastDirection=if(across<0) Direction.Right else Direction.Left
                val point=next.point
                playerWorldXDp=unit*point.along(activeWorldAxis);playerOtherAxisDp=unit*point.other(activeWorldAxis);heightPx=unitPx*point.row
                if(next.atTop && up>.2f) {
                    vineStand=next.vine;vineSwing=null;vineRegrabAfter=currentTimeMillis()+500
                    playerWorldXDp=unit*(if(activeWorldAxis==WorldAxis.Latitude) next.vine.latitude else next.vine.longitude)
                    heightPx=unitPx*next.vine.topRow
                } else if(next.atBottom && up<-.2f) {
                    vineSwing=null;vineRegrabAfter=currentTimeMillis()+500
                } else vineSwing=next
            }
        }
        val seenTugs = remember { mutableSetOf<Long>() }
        LaunchedEffect(companion, isHostSelected) {
            while(true) {
                delay(100)
                if(!isPaused) {
                    shocks.tick(100)
                    dog.tick(100)
                    if (isHostSelected != false) hazardDamage.advance(100, tileMap, companion.players(), cycle.elapsedMs) { id ->
                        if (id == bedRound.selfId) playerHp = (playerHp - equipment.damage(1)).coerceAtLeast(0)
                        else mp?.sendRoundEvent("DAMAGE;${nanoTime()};$id;1")
                    }
                    if(playerHp > 0 && heightPx >= unitPx * 3) {
                        if(debugBed!=4) alien.tick(100,bedRound.roundId,cycle.hunting)
                        companion.tick(100,bedRound.bed,bedRound.roundId)
                    }
                }
            }
        }
        LaunchedEffect(companion, audio) {
            try {
                while(true) {
                    audio?.purrProximity = if(isPaused) 0f else CatProximity.gain(localRopePoint(), companion.cat, tileMap)
                    delay(50)
                }
            } finally { audio?.purrProximity = 0f }
        }
        LaunchedEffect(playerWorldXDp, playerOtherAxisDp, heightPx, activeWorldAxis, bedRound.bed) {
            val lat = xDpToPx(if (activeWorldAxis == WorldAxis.Latitude) playerWorldXDp else playerOtherAxisDp) / unitPx
            val lon = xDpToPx(if (activeWorldAxis == WorldAxis.Longitude) playerWorldXDp else playerOtherAxisDp) / unitPx
            if (bedRound.bed?.onTop(tileMap, lat, lon, heightPx / unitPx, activeWorldAxis) == true) playerHp = 0
        }
        LaunchedEffect(cycle.remainingSeconds, cycle.safe, bedRound.bed, bedRound.loot, mammyLocation, connectionStatus) {
            bedRound.publish()
        }

        // Increment to trigger recomposition when map content changes (e.g., tile destroyed)
        var mapVersion by remember { mutableStateOf(0) }
        LaunchedEffect(bedRound.revision) { mapVersion++ }

        data class PendingAction(val action: WorldAction, val cost: InventorySlot?)
        val pendingActions = remember { mutableMapOf<Long, PendingAction>() }
        val actionReceipts = remember { ActionReceipts() }
        fun requestWorldAction(verb: String, arguments: List<String>, spendSelected: Boolean = false): Boolean {
            if (syncingWorld || !connectionStatus) return false
            if (pendingActions.values.any { it.action.verb == verb && it.action.arguments == arguments }) return false
            val cost = if (spendSelected) inventory.getOrNull(vm.selectedSlot.intValue)?.copy(count = 1) else null
            if (spendSelected && (cost?.type == null || !vm.consumeSelectedItem())) return false
            val lat = xDpToPx(if (activeWorldAxis == WorldAxis.Latitude) playerWorldXDp else playerOtherAxisDp)
            val lon = xDpToPx(if (activeWorldAxis == WorldAxis.Longitude) playerWorldXDp else playerOtherAxisDp)
            val action = WorldAction(nanoTime(), verb, activeWorldAxis, (lat / unitPx * 1000).toInt(),
                (lon / unitPx * 1000).toInt(), (heightPx / unitPx * 1000).toInt(), arguments)
            pendingActions[action.id] = PendingAction(action, cost)
            mp?.sendRoundEvent(action.encode())
            return true
        }
        fun finishCraft(id: String) {
            if (playerHp <= 0 || syncingWorld || Crafting.nearby(tileMap, localRopePoint()) == null) {
                pushNotice(tr("Move closer to the workbench")); return
            }
            when (Crafting.make(vm.bag, id)) {
                CraftResult.Crafted -> {
                    val recipe = Crafting.recipes.first { it.id == id }
                    pushNotice(tr("Made {0} ×{1}", recipe.name, recipe.count))
                    audio?.play(SoundEffect.Place)
                }
                CraftResult.BagFull -> pushNotice(tr("Inventory full"))
                else -> pushNotice(tr("Need more materials"))
            }
        }
        LaunchedEffect(mp) {
            while (true) {
                delay(1000L)
                if (connectionStatus && !syncingWorld) pendingActions.values.toList().forEach { mp?.sendRoundEvent(it.action.encode()) }
            }
        }

        // One-time spawn: start above the map and let gravity bring the player down onto the next platform or ground

        // Helpers are already declared above

        var didInitialSpawn by remember { mutableStateOf(false) }

        // Continuously report player's current world position to the ViewModel for zombie chasing
        LaunchedEffect(playerWorldXDp, playerOtherAxisDp, heightPx, unitPx) {
            // Send immediately on any dependency change
            vm.setPlayerPositionPx(xDpToPx(playerWorldXDp), xDpToPx(playerOtherAxisDp), heightPx)
        }

        fun normalizeAngleDeg(a: Float): Float {
            var d = a % 360f
            if (d < 0f) d += 360f
            return d
        }
        fun angleToDirection(degRaw: Float): Direction {
            val d = normalizeAngleDeg(degRaw)
            return when (d) {
                !in 18f..<342f -> Direction.Right
                in 18f..<54f -> Direction.RightUp
                in 54f..<90f -> Direction.UpRight
                in 90f..<126f -> Direction.UpLeft
                in 126f..<162f -> Direction.LeftUp
                in 162f..<198f -> Direction.Left
                in 198f..<234f -> Direction.LeftDown
                in 234f..<270f -> Direction.DownLeft
                in 270f..<306f -> Direction.DownRight
                else -> Direction.RightDown // 306°..342°
            }
        }
        fun vectorToDirection(dx: Float, dy: Float): Direction {
            if (dx == 0f && dy == 0f) return lastDirection
            val angleRad = atan2(dy, dx)
            val deg = (angleRad * 180.0 / PI).toFloat()
            return angleToDirection(deg)
        }

        // World coordinates use positive Y upward, just like joystick aim.
        // Sectors are centered every 36°: Right=0°, RightUp=36°, UpRight=72°, UpLeft=108°,
        // LeftUp=144°, Left=180°, LeftDown=216°, DownLeft=252°, DownRight=288°, RightDown=324°.
        fun directionToVector(d: Direction): Pair<Float, Float> {
            val deg = when (d) {
                Direction.Right -> 0f
                Direction.RightUp -> 36f
                Direction.UpRight -> 72f
                Direction.UpLeft -> 108f
                Direction.LeftUp -> 144f
                Direction.Left -> 180f
                Direction.LeftDown -> 216f
                Direction.DownLeft -> 252f
                Direction.DownRight -> 288f
                Direction.RightDown -> 324f
            }
            val rad = (deg * PI / 180.0)
            val x = cos(rad).toFloat()
            val y = sin(rad).toFloat()
            return x to y
        }

        fun shortestWorldDx(fromPx: Float, toPx: Float): Float {
            val worldWidthPx = tileMap.width * unitPx
            val from = ((fromPx % worldWidthPx) + worldWidthPx) % worldWidthPx
            val to = ((toPx % worldWidthPx) + worldWidthPx) % worldWidthPx
            var dx = to - from
            if (abs(dx) > worldWidthPx / 2f) {
                dx += if (dx > 0f) -worldWidthPx else worldWidthPx
            }
            return dx
        }

        fun peerOnPlayerPlane(peer: RemotePeer): Boolean {
            val other = if (activeWorldAxis == WorldAxis.Latitude) peer.longitude else peer.latitude
            return peer.hasPos && other != null && tileMap.wrap(other) == tileMap.activeSlice &&
                (!worldConfig.exploration || peer.latitude?.let { ExplorationWorld.biome(it, peer.longitude ?: 10) == currentBiome } == true)
        }

        fun isOnPlayerPlane(otherAxisPx: Float): Boolean =
            WorldRows.same(otherAxisPx, xDpToPx(playerOtherAxisDp), unitPx, tileMap.width)

        fun wrapCol(col: Int): Int {
            return ((col % tileMap.width) + tileMap.width) % tileMap.width
        }

        fun sameWrappedCol(a: Int, b: Int): Boolean {
            return wrapCol(a) == wrapCol(b)
        }

        fun colInWrappedRange(col: Int, range: IntRange): Boolean {
            return range.any { sameWrappedCol(col, it) }
        }

        fun respawnPlayerRandom() {
            val rng = Random.Default
            repeat(200) {
                val col = if (worldConfig.exploration)
                    ExplorationWorld.origin(currentBiome!!).along(activeWorldAxis) + rng.nextInt(8, if (activeWorldAxis == WorldAxis.Latitude) 182 else 278)
                else rng.nextInt(tileMap.width)
                var topRow: Int? = null
                for (r in (tileMap.height - 1) downTo 0) {
                    if (tileMap.get(col, r)) { topRow = r; break }
                }
                if (topRow != null) {
                    val groundTop = (topRow + 1) * unitPx
                    playerWorldXDp = pxToDp(col * unitPx)
                    heightPx = groundTop
                    isJumping = false
                    return
                }
            }
            // Fallback
            playerWorldXDp = 0.dp
            heightPx = 0f
            isJumping = false
        }

        fun spawnZombieRandom() {
            vm.spawnZombieRandom(
                tileMap = tileMap,
                unitPx = unitPx,
                playerWidthPx = playerWidthPx,
                playerHeightPx = playerHeightPx,
                pxToDp = ::pxToDp
            )
        }

        fun spawnSkeletonRandom() {
            vm.spawnSkeletonRandom(
                tileMap = tileMap,
                unitPx = unitPx,
                playerWidthPx = playerWidthPx,
                playerHeightPx = playerHeightPx,
                pxToDp = ::pxToDp
            )
        }

        val maxZombies = worldConfig.initialZombies
        val maxSkeletons = worldConfig.initialSkeletons

        fun spawnZombiesUntilCap(targetCount: Int = maxZombies) {
            val missing = (targetCount - zombies.size).coerceAtLeast(0)
            repeat(missing) { spawnZombieRandom() }
        }

        fun spawnSkeletonsUntilCap(targetCount: Int = maxSkeletons) {
            val missing = (targetCount - skeletons.size).coerceAtLeast(0)
            repeat(missing) { spawnSkeletonRandom() }
        }

        LaunchedEffect(tileMap.width) {
            if (!didInitialSpawn) {
                if (debugBed > 0) {
                    bedRound.advance(MammyCycle.ROUND_MS, emptyList())
                    bedRound.bed?.let { b ->
                        // Debug fixture 4 isolates the cat quest from random trees and alien combat.
                        if(debugBed==4) for(lat in b.latitude-5 until b.latitude) for(lon in b.longitude-1..b.longitude+2)
                            for(row in b.row until tileMap.height) tileMap.damageVoxel(lat,lon,row,10000)
                        playerWorldXDp = pxToDp((if (debugBed == 2) b.latitude else tileMap.wrap(b.latitude - 3)) * unitPx)
                        playerOtherAxisDp = pxToDp(b.longitude * unitPx)
                        heightPx = (b.row + if (debugBed == 2) 8 else 0) * unitPx
                        if (debugBed == 3) {
                            vm.bag.consume(0, 12)
                            vm.bag.add(ItemType.Block, 140, BlockMaterial.Grass)
                        }
                    }
                }
                if (debugDropOnMammy) {
                    playerWorldXDp = pxToDp(mammyLocation.latitude * unitPx)
                    playerOtherAxisDp = pxToDp(mammyLocation.longitude * unitPx)
                    tileMap.setActiveSlice(WorldAxis.Latitude, mammyLocation.latitude, mammyLocation.longitude)
                    // Debug-only aiming fixtures: a small target followed by a second target.
                    // Three hits should remove only the nearer grass cell and award one item.
                    tileMap.placePiece(mammyLocation.latitude + 3, mammyLocation.row + 3, BlockMaterial.Grass, BlockShape.Single)
                    tileMap.placePiece(mammyLocation.latitude + 4, mammyLocation.row + 3, BlockMaterial.Wood, BlockShape.Single)
                    mapVersion++
                }
                // Position player high above the highest tiles so they "fall in" from the top
                heightPx = (tileMap.height * unitPx) + playerHeightPx * 2f
                // Keep isJumping false so the existing unsupported check triggers the falling coroutine
                isJumping = false
                didInitialSpawn = true

                if (isHostSelected != false) {
                    spawnZombiesUntilCap(targetCount = maxZombies)
                    spawnSkeletonsUntilCap(targetCount = maxSkeletons)
                }
                if(restoredWorld!=null && initialIsHostSelected!=false) {
                    activeWorldAxis=restoredWorld.axis
                    val p=restoredWorld.position
                    playerWorldXDp=unit*p.along(activeWorldAxis);playerOtherAxisDp=unit*p.other(activeWorldAxis)
                    heightPx=p.row*unitPx;playerHp=restoredWorld.hp.coerceAtLeast(1)
                    tileMap.setActiveSlice(activeWorldAxis,p.lat.toInt(),p.lon.toInt())
                    vm.bag.restore(restoredWorld.inventory);equipment.loadout=restoredWorld.equipment
                    dog.restore(restoredWorld.dog)
                }
                if(debugAdventure>0) {
                    val point=when(debugAdventure) {
                        6->tileMap.portals.first { ExplorationWorld.biome(it.location.latitude,it.location.longitude)==ExplorationBiome.Meadow }.location.let { RopePoint(it.latitude.toFloat(),it.longitude.toFloat(),it.row.toFloat()) }
                        13->RopePoint(34f,114f,3f)
                        14->RopePoint(30f,114f,3f)
                        in 8..12->RopePoint(30f,114f,3f)
                        1->RopePoint(24f,62f,4f)
                        2->SwingVines.all.first().point(0f)
                        4->BiomeHazards.all.first { it.kind==HazardKind.Vine }.let { RopePoint(it.latitude.toFloat(),it.longitude.toFloat(),9f) }
                        else->RopePoint(50f,10f,3f)
                    }
                    playerWorldXDp=unit*point.lat;playerOtherAxisDp=unit*point.lon;heightPx=unitPx*point.row
                    if(debugAdventure==5 || debugAdventure==6) vm.bag.add(ItemType.Map,1)
                    if(debugAdventure==7) {
                        vm.bag.add(ItemType.Block,8,BlockMaterial.Wood); vm.bag.add(ItemType.Block,3,BlockMaterial.Stone)
                    }
                    // Repeatable marketing/test starts. The Android entry point is debug-only.
                    // Keep real terrain, collisions, item use and damage; stage only starting state.
                    if(debugAdventure in 8..12) {
                        tileMap.setActiveSlice(WorldAxis.Latitude,30,114)
                        if(debugAdventure==8) vm.bag.add(ItemType.Block,8,BlockMaterial.Wood)
                        if(debugAdventure==9) {
                            vm.bag.add(ItemType.ElectricArrow,8)
                            vm.applyWorldEntities(listOf(
                                NetworkEntity("Z",910,42000,114000,3000,hp=3),
                                NetworkEntity("S",911,49000,114000,3000,hp=3)
                            ),activeWorldAxis,unitPx,::pxToDp)
                        }
                        if(debugAdventure==10) {
                            playerHp=2
                            vm.bag.add(ItemType.Potion,3)
                            vm.bag.add(ItemType.ElectricArrow,5)
                        }
                        if(debugAdventure==11) mammyLocation=WorldLocation(42,114,3)
                        if(debugAdventure==12) {
                            bedRound.advance(MammyCycle.ROUND_MS,emptyList())
                            vm.applyWorldEntities(listOf(
                                NetworkEntity("Z",920,35000,114000,3000,hp=3),
                                NetworkEntity("S",921,39000,114000,3000,hp=3),
                                NetworkEntity("S",922,43000,114000,3000,hp=3,item=ItemType.ElectricArrow)
                            ),activeWorldAxis,unitPx,::pxToDp)
                        }

                    }
                    if(debugAdventure==14) {
                        tileMap.setActiveSlice(WorldAxis.Latitude,30,114)
                        dog.restore(PetDog(RopePoint(42f,114f,3f),-1))
                    }
                    if(debugAdventure==13) {
                        // Example built from the same placeable blocks as player construction.
                        tileMap.setActiveSlice(WorldAxis.Latitude,46,114)
                        for(x in 37..52 step 3) for(row in 3..6 step 3) {
                            val doorway=x==46
                            val window=row==6 && (x==40 || x==49)
                            if(!doorway && !window) tileMap.placePiece(x,row,BlockMaterial.Wood,BlockShape.Square)
                        }
                        for(x in 37..52 step 3) tileMap.placePiece(x,9,BlockMaterial.Stone,BlockShape.Square)
                        for(x in 40..49 step 3) tileMap.placePiece(x,12,BlockMaterial.Stone,BlockShape.Square)
                        mapVersion++
                    }
                    if(debugAdventure==3) {
                        vm.applyWorldEntities(List(8) { index -> NetworkEntity(if(index%2==0) "Z" else "S",900L+index,
                            (56+index*3)*1000,10000,3000,hp=3) },activeWorldAxis,unitPx,::pxToDp)
                    }
                }
            }
        }

        // Respawn flow: when HP hits 0, clear inventory, restore hearts, and fall in from the top at the start
        // Copy mutable game data on Main; encoding and atomic disk replacement run in the writer.
        val savedTerrainRevision = remember(tileMap) { longArrayOf(-1) }
        val savedTerrain = remember(tileMap) { arrayOf(emptyList<Block>()) }
        fun captureWorld():SavedWorld? {
            if(!worldConfig.exploration || isHostSelected==false || !didInitialSpawn || debugAdventure>0) return null
            if(savedTerrainRevision[0]!=tileMap.saveRevision) {
                savedTerrain[0]=tileMap.captureSaveBlocks();savedTerrainRevision[0]=tileMap.saveRevision
            }
            return SavedWorld(worldConfig,localRopePoint(),activeWorldAxis,playerHp,vm.bag.slots.toList(),equipment.loadout,dog.dog,savedTerrain[0])
        }
        val captureLatest by rememberUpdatedState({ captureWorld() })
        LaunchedEffect(tileMap,saves) {
            if(saves==null)return@LaunchedEffect
            delay(1000)
            while(true) { captureLatest()?.let(saves::save);delay(30000) }
        }
        LaunchedEffect(paused) { if(paused) captureLatest()?.let { saves?.save(it) } }
        androidx.compose.runtime.DisposableEffect(tileMap,saves) {
            saves?.capture={captureLatest()}
            onDispose { captureLatest()?.let { saves?.save(it) };saves?.capture=null }
        }

        LaunchedEffect(playerHp) {
            if (playerHp <= 0) {
                if (mp == null && !worldConfig.exploration) onDeath() else {
                    // A death resets this player, never the host's shared world or round.
                    pendingActions.clear()
                    craftingPending=false;isCraftingOpen=false
                    ItemType.entries.forEach(vm.bag::removeAll)
                    vm.bag.add(ItemType.Block, 12, BlockMaterial.Grass)
                    vm.bag.addStarterEquipment()
                    playerHp = 5
                    respawnPlayerRandom()
                    heightPx = tileMap.height * unitPx + playerHeightPx * 2f
                    pushNotice(tr("Back in the game"))
                }
            }
        }


        LaunchedEffect(tileMap) {
            if (worldConfig.exploration) pushNotice(tr("Hit a portal to visit another biome"))
        }

        fun travelThroughPortal(portal: WorldPortal) {
            val destination = ExplorationWorld.safeArrival(tileMap, portal)
            if (destination == null) { pushNotice(tr("The portal exit is blocked")); return }
            companion.request("RELEASE")
            vineSwing = null; vineStand = null
            playerWorldXDp = unit * destination.along(activeWorldAxis)
            playerOtherAxisDp = unit * destination.across(activeWorldAxis)
            heightPx = destination.row * unitPx
            tileMap.setActiveSlice(activeWorldAxis, destination.latitude, destination.longitude)
            joystickX = 0f; joystickY = 0f
            previewSelectedAimCol = null; previewSelectedAimRow = null
            movementClock.reset(); verticalClock.reset()
        }

        // Simple helper to compute overlapping columns/rows for an arbitrary AABB bottom and left in px
        fun aabbOverlappingColumns(leftPx: Float): IntRange {
            val c0 = floor(leftPx / unitPx).toInt()
            val c1 = floor((leftPx + playerWidthPx - 0.001f) / unitPx).toInt()
            return c0..c1
        }
        fun aabbOverlappingRows(bottomPx: Float): IntRange {
            val r0 = floor((bottomPx - 0.001f) / unitPx).toInt()
            val r1 = floor(((bottomPx + playerHeightPx - 0.001f) / unitPx)).toInt()
            return r0..r1
        }

        fun playerOverlappingColumns(xPx: Float): IntRange {
            val right = xPx + playerWidthPx - 0.001f
            val c0 = floor(xPx / unitPx).toInt()
            val c1 = floor(right / unitPx).toInt()
            return c0..c1
        }
        fun playerOverlappingRows(bottomPx: Float): IntRange {
            val top = bottomPx + playerHeightPx - 0.001f
            val r0 = floor(bottomPx / unitPx).toInt()
            val r1 = floor(top / unitPx).toInt()
            return r0..r1
        }

        LaunchedEffect(bedRound, isHostSelected) {
            while (true) {
                delay(200L)
                if (isPaused) continue
                val p = bedRound.position(bedRound.selfId) ?: continue
                bedRound.loot.toList().forEach { item ->
                    val location = WorldLocation(item.latitude, item.longitude, item.row)
                    if (!location.intersects(activeWorldAxis, tileMap.activeSlice, tileMap.width)) return@forEach
                    val cols = playerOverlappingColumns(xDpToPx(playerWorldXDp))
                    if (cols.any { tileMap.wrap(it) == location.along(activeWorldAxis) } && item.row in p.row..p.row + 2) {
                        val room = vm.bag.roomFor(item.type)
                        if (room > 0) bedRound.collect(item.id, minOf(room, item.count))
                    }
                }
            }
        }

        // Potion collection logic
        LaunchedEffect(playerWorldXDp, playerOtherAxisDp, activeWorldAxis, heightPx, potions) {
            val pLeftPx = xDpToPx(playerWorldXDp)
            val pRows = playerOverlappingRows(heightPx)
            val pCols = playerOverlappingColumns(pLeftPx)
            if (isPaused) return@LaunchedEffect
            potions.forEach { potion ->
                if (tileMap.wrap(potion.otherCol) != tileMap.activeSlice) return@forEach
                // Check if potion col/row is within player's footprint (wrap-aware)
                val inCol = pCols.any { ((it % tileMap.width) + tileMap.width) % tileMap.width == potion.col }
                if (inCol && potion.row in pRows) {
                    if (isHostSelected == false) {
                        if (vm.bag.roomFor(potion.type) > 0) requestWorldAction("TAKE", listOf(potion.id.toString()))
                    } else if (vm.collectPotion(potion.id)) {
                        pushNotice(if (potion.type == ItemType.ElectricArrow) tr("Electric arrow +1") else if(potion.type == ItemType.Arrow) tr("Arrow +1") else tr("Potion +1"))
                    }
                }
            }
        }
        fun anySolidInColumnsRows(cols: IntRange, rows: IntRange): Boolean {
            for (c in cols) for (r in rows) if (tileMap.get(c, r)) return true
            return false
        }

        fun losClearSameRow(colFrom: Int, colTo: Int, row: Int): Boolean {
            val minC = min(colFrom, colTo)
            val maxC = max(colFrom, colTo)
            // Exclude the endpoints (occupied by actor AABBs); check strictly between
            if (maxC - minC <= 1) return true
            for (c in (minC + 1) until maxC) { if (tileMap.get(c, row)) return false }
            return true
        }

        fun npcApplyHorizontalCollision(prevPx: Float, nextPx: Float, bottomPx: Float): Float {
            var nxPx = nextPx
            val movingRight = nxPx > prevPx
            val trim = 2f
            val rows: IntRange = run {
                val bottom = (bottomPx + trim).coerceAtLeast(0f)
                val top = (bottomPx + playerHeightPx - trim).coerceAtLeast(bottom)
                val r0 = floor(bottom / unitPx).toInt()
                val r1 = floor(top / unitPx).toInt()
                r0..r1
            }
            if (movingRight) {
                val rightEdge = nxPx + playerWidthPx - 0.001f
                val col = floor(rightEdge / unitPx).toInt()
                if (anySolidInColumnsRows(col..col, rows)) {
                    val tileLeftPx = col * unitPx
                    nxPx = tileLeftPx - playerWidthPx
                }
            } else if (nxPx < prevPx) {
                val leftEdge = nxPx + 0.001f
                val col = floor(leftEdge / unitPx).toInt()
                if (anySolidInColumnsRows(col..col, rows)) {
                    val tileRightPx = (col + 1) * unitPx
                    nxPx = tileRightPx
                }
            }
            // Collide with local player AABB to avoid passing through
            val pLeft = xDpToPx(playerWorldXDp)
            val pRight = pLeft + playerWidthPx
            val pBottom = heightPx
            val pTop = pBottom + playerHeightPx
            val myTop = bottomPx + playerHeightPx
            val verticalOverlap = !(myTop <= pBottom || bottomPx >= pTop)
            if (verticalOverlap) {
                if (movingRight) {
                    val myRight = nxPx + playerWidthPx
                    if (myRight > pLeft && prevPx + playerWidthPx <= pLeft) nxPx = pLeft - playerWidthPx
                } else if (nxPx < prevPx) {
                    val myLeft = nxPx
                    if (myLeft < pRight && prevPx >= pRight) nxPx = pRight
                }
            }
            val worldWidthPx = tileMap.width * unitPx
            nxPx = ((nxPx % worldWidthPx) + worldWidthPx) % worldWidthPx
            return nxPx
        }

        // Horizontal collision using tile grid with wrap (Px version, no normalization)
        fun applyHorizontalCollisionPx(prevPx: Float, desiredNextPx: Float, bottomPx: Float): Float {
            var nxPx = desiredNextPx
            val movingRight = nxPx > prevPx
            // Use trimmed vertical span to allow threading small gaps while jumping.
            // Increase trim when airborne to avoid corner catches. Slightly higher to ensure 2-grid shafts are passable.
            val trim = max(2f, (unitPx * if (isJumping) 0.5f else 0.2f))
            val rows: IntRange = run {
                val bottom = (bottomPx + trim).coerceAtLeast(0f)
                val top = (bottomPx + playerHeightPx - trim).coerceAtLeast(bottom)
                val r0 = floor(bottom / unitPx).toInt()
                val r1 = floor(top / unitPx).toInt()
                r0..r1
            }
            if (movingRight) {
                val rightEdge = nxPx + playerWidthPx - 0.001f
                val col = floor(rightEdge / unitPx).toInt()
                // If the leading column has any solid in our rows, clamp to its left edge
                val cols = col..col
                if (anySolidInColumnsRows(cols, rows)) {
                    val tileLeftPx = col * unitPx
                    nxPx = tileLeftPx - playerWidthPx
                }
            } else if (nxPx < prevPx) {
                val leftEdge = nxPx + 0.001f // small epsilon to avoid false block when exactly aligned
                val col = floor(leftEdge / unitPx).toInt()
                val cols = col..col
                if (anySolidInColumnsRows(cols, rows)) {
                    val tileRightPx = (col + 1) * unitPx
                    nxPx = tileRightPx
                }
            }
            // Also collide with remote peers (no pass-through). Local player has priority.
            if (connectionStatus) {
                val myTop = bottomPx + playerHeightPx
                val epsV = 0.75f
                peersState.values.forEach { peer ->
                        if (!peerOnPlayerPlane(peer)) return@forEach
                    val px = peer.worldXPx
                    val pRight = px + playerWidthPx
                    val pBottom = peer.heightPx
                    val pTop = peer.heightPx + playerHeightPx
                    val verticalOverlap = !((myTop - epsV) <= pBottom || (bottomPx + epsV) >= pTop)
                    if (!verticalOverlap) return@forEach
                    if (movingRight) {
                        val myRight = nxPx + playerWidthPx
                        if (myRight > px && prevPx + playerWidthPx <= px) {
                            nxPx = px - playerWidthPx
                        }
                    } else if (nxPx < prevPx) {
                        val myLeft = nxPx
                        if (myLeft < pRight && prevPx >= pRight) {
                            nxPx = pRight
                        }
                    }
                }
            }
            // Use the same exact footprint for every visible character at fractional positions.
            fun collideCharacter(baseLeft: Float, npcBottom: Float, other: Float) {
                if (!isOnPlayerPlane(other)) return
                val eps = 0.75f
                if (bottomPx + playerHeightPx - eps <= npcBottom ||
                    bottomPx + eps >= npcBottom + playerHeightPx) return
                val span = tileMap.width * unitPx
                for (copy in -1..1) {
                    val left = baseLeft + copy * span
                    val right = left + playerWidthPx
                    if (movingRight && prevPx + playerWidthPx <= left && nxPx + playerWidthPx > left) {
                        nxPx = left - playerWidthPx
                    } else if (!movingRight && prevPx >= right && nxPx < right) {
                        nxPx = right
                    }
                }
            }
            zombies.forEach { collideCharacter(it.worldXPx, it.bottomPx, it.otherAxisPx) }
            skeletons.forEach { collideCharacter(it.worldXPx, it.bottomPx, it.otherAxisPx) }
            if (mammyVisible) collideCharacter(mammyGoalCol * unitPx, mammyGoalBottomRow * unitPx,
                xDpToPx(playerOtherAxisDp))
            return nxPx
        }

        // Backwards-compatible wrapper that normalizes and converts to Dp (kept for any other usages)
        fun applyHorizontalCollision(prevXDp: Dp, nextXDp: Dp, bottomPx: Float): Dp {
            val collidedPx = applyHorizontalCollisionPx(xDpToPx(prevXDp), xDpToPx(nextXDp), bottomPx)
            val worldWidthPx = tileMap.width * unitPx
            val norm = ((collidedPx % worldWidthPx) + worldWidthPx) % worldWidthPx
            return pxToDp(norm)
        }

        // --- Grid-quantized horizontal movement (2x3 cell footprint), smooth visuals ---
        // Player and zombies are 2 cells wide by 3 cells high. We keep pixel movement but
        // only allow crossing grid boundaries when the next cell band is unoccupied.
        fun colsRowsForFootprint(leftPx: Float, bottomPx: Float): Pair<IntRange, IntRange> {
            val leftCol = floor(leftPx / unitPx).toInt()
            val bottomRow = floor(bottomPx / unitPx).toInt()
            val cols = leftCol..(leftCol + 1)
            val rows = bottomRow..(bottomRow + 2)
            return cols to rows
        }

        fun isFootprintBlockedByTiles(leftCol: Int, bottomRow: Int): Boolean {
            val rows = bottomRow..(bottomRow + 2)
            val cols = leftCol..(leftCol + 1)
            for (c in cols) {
                val wc = ((c % tileMap.width) + tileMap.width) % tileMap.width
                for (r in rows) if (tileMap.get(wc, r)) return true
            }
            return false
        }

        // Determine if player is currently supported by tiles directly under feet
        fun isSupportedByTiles(leftPx: Float, bottomPx: Float): Boolean {
            val cols = run {
                val c0 = floor(leftPx / unitPx).toInt()
                val c1 = floor((leftPx + playerWidthPx - 0.001f) / unitPx).toInt()
                c0..c1
            }
            val rowUnder = floor((bottomPx - 0.001f) / unitPx).toInt()
            if (!tileMap.inY(rowUnder)) return false
            for (c in cols) if (tileMap.get(c, rowUnder)) return true
            return false
        }

        fun supportingPeerTop(leftPx: Float, bottomPx: Float): Float? =
            if (!connectionStatus) null else standingPeerTop(
                leftPx, bottomPx, playerWidthPx, playerHeightPx, unitPx,
                peersState.values, ::peerOnPlayerPlane
            )

        // Front-band blockers (single entering column) for grid-quantized horizontal movement
        fun isFrontBandBlockedByTiles(frontCol: Int, bottomRow: Int): Boolean {
            // Include the feet row. A one-cell rise is handled explicitly by step-up logic;
            // skipping it let the player pass through short blocks from the left.
            val rows = bottomRow..(bottomRow + 2)
            val wc = ((frontCol % tileMap.width) + tileMap.width) % tileMap.width
            for (r in rows) if (tileMap.get(wc, r)) return true
            return false
        }

        fun isFrontBandBlockedByMammy(frontCol: Int, bottomRow: Int): Boolean {
            if (!mammyVisible) return false
            val mammyRows = mammyGoalBottomRow..(mammyGoalBottomRow + 2)
            val playerRows = bottomRow..(bottomRow + 2)
            if (playerRows.last < mammyRows.first || playerRows.first > mammyRows.last) return false
            val target = ((frontCol % tileMap.width) + tileMap.width) % tileMap.width
            // Mammy occupies the same 2x3-cell footprint as a character sprite.
            val mammyCols = mammyGoalCol..(mammyGoalCol + 1)
            return mammyCols.any { ((it % tileMap.width) + tileMap.width) % tileMap.width == target }
        }

        fun isFootprintBlockedByMammy(leftCol: Int, bottomRow: Int): Boolean {
            if (!mammyVisible) return false
            val mammyRows = mammyGoalBottomRow..(mammyGoalBottomRow + 2)
            val playerRows = bottomRow..(bottomRow + 2)
            if (playerRows.last < mammyRows.first || playerRows.first > mammyRows.last) return false
            val playerCols = (leftCol..(leftCol + 1)).map {
                ((it % tileMap.width) + tileMap.width) % tileMap.width
            }
            val mammyCols = setOf(
                mammyGoalCol,
                (mammyGoalCol + 1) % tileMap.width
            )
            return playerCols.any { it in mammyCols }
        }

        fun isSupportedByMammy(leftPx: Float, bottomPx: Float): Boolean {
            return abs(bottomPx - (mammyGoalBottomRow + 3) * unitPx) < 0.5f &&
                mammyOverlapsColumns(playerOverlappingColumns(leftPx))
        }

        fun canStepUp(frontCol: Int, bottomRow: Int): Boolean {
            val wc = ((frontCol % tileMap.width) + tileMap.width) % tileMap.width
            // A step is exactly one tile high: solid at the feet, clear through the player's body.
            if (!tileMap.get(wc, bottomRow)) return false
            for (r in (bottomRow + 1)..(bottomRow + 2)) if (tileMap.get(wc, r)) return false
            return true
        }

        fun isFootprintBlockedByZombies(leftCol: Int, bottomRow: Int): Boolean {
            if (zombies.isEmpty()) return false
            val rows = bottomRow..(bottomRow + 2)
            val colsSet = hashSetOf<Int>()
            for (c in leftCol..(leftCol + 1)) colsSet.add(((c % tileMap.width) + tileMap.width) % tileMap.width)
            zombies.forEach { z ->
                if (!isOnPlayerPlane(z.otherAxisPx)) return@forEach
                val zLeftCol = floor(z.worldXPx / unitPx).toInt()
                val zBottomRow = floor(z.bottomPx / unitPx).toInt()
                val zCols = (((zLeftCol) % tileMap.width) + tileMap.width) % tileMap.width .. (((zLeftCol + 1) % tileMap.width) + tileMap.width) % tileMap.width
                val zRows = zBottomRow..(zBottomRow + 2)
                // rows intersect?
                val rowsOverlap = !(rows.last < zRows.first || rows.first > zRows.last)
                if (!rowsOverlap) return@forEach
                // columns intersect modulo wrap
                if (colsSet.contains((((zLeftCol) % tileMap.width) + tileMap.width) % tileMap.width) ||
                    colsSet.contains((((zLeftCol + 1) % tileMap.width) + tileMap.width) % tileMap.width)) {
                    return true
                }
            }
            return false
        }

        fun isFrontBandBlockedByZombies(frontCol: Int, bottomRow: Int): Boolean {
            if (zombies.isEmpty()) return false
            val rows = bottomRow..(bottomRow + 2)
            val fC = ((frontCol % tileMap.width) + tileMap.width) % tileMap.width
            zombies.forEach { z ->
                if (!isOnPlayerPlane(z.otherAxisPx)) return@forEach
                val zLeftCol = floor(z.worldXPx / unitPx).toInt()
                val zBottomRow = floor(z.bottomPx / unitPx).toInt()
                val zRows = zBottomRow..(zBottomRow + 2)
                val rowsOverlap = !(rows.last < zRows.first || rows.first > zRows.last)
                if (!rowsOverlap) return@forEach
                val zC0 = ((zLeftCol % tileMap.width) + tileMap.width) % tileMap.width
                val zC1 = (((zLeftCol + 1) % tileMap.width) + tileMap.width) % tileMap.width
                if (fC == zC0 || fC == zC1) return true
            }
            return false
        }

        fun isFootprintBlockedBySkeletons(leftCol: Int, bottomRow: Int): Boolean {
            if (skeletons.isEmpty()) return false
            val rows = bottomRow..(bottomRow + 2)
            val colsSet = hashSetOf<Int>()
            for (c in leftCol..(leftCol + 1)) colsSet.add(((c % tileMap.width) + tileMap.width) % tileMap.width)
            skeletons.forEach { s ->
                if (!isOnPlayerPlane(s.otherAxisPx)) return@forEach
                val sLeftCol = floor(s.worldXPx / unitPx).toInt()
                val sBottomRow = floor(s.bottomPx / unitPx).toInt()
                val sRows = sBottomRow..(sBottomRow + 2)
                val rowsOverlap = !(rows.last < sRows.first || rows.first > sRows.last)
                if (!rowsOverlap) return@forEach
                if (colsSet.contains((((sLeftCol) % tileMap.width) + tileMap.width) % tileMap.width) ||
                    colsSet.contains((((sLeftCol + 1) % tileMap.width) + tileMap.width) % tileMap.width)) {
                    return true
                }
            }
            return false
        }

        fun isFrontBandBlockedBySkeletons(frontCol: Int, bottomRow: Int): Boolean {
            if (skeletons.isEmpty()) return false
            val rows = bottomRow..(bottomRow + 2)
            val fC = ((frontCol % tileMap.width) + tileMap.width) % tileMap.width
            skeletons.forEach { s ->
                if (!isOnPlayerPlane(s.otherAxisPx)) return@forEach
                val sLeftCol = floor(s.worldXPx / unitPx).toInt()
                val sBottomRow = floor(s.bottomPx / unitPx).toInt()
                val sRows = sBottomRow..(sBottomRow + 2)
                val rowsOverlap = !(rows.last < sRows.first || rows.first > sRows.last)
                if (!rowsOverlap) return@forEach
                val sC0 = ((sLeftCol % tileMap.width) + tileMap.width) % tileMap.width
                val sC1 = (((sLeftCol + 1) % tileMap.width) + tileMap.width) % tileMap.width
                if (fC == sC0 || fC == sC1) return true
            }
            return false
        }

        fun isFootprintBlockedByPeers(leftCol: Int, bottomRow: Int): Boolean {
            if (!connectionStatus) return false
            val rows = bottomRow..(bottomRow + 2)
            val colsSet = hashSetOf<Int>()
            for (c in leftCol..(leftCol + 1)) colsSet.add(((c % tileMap.width) + tileMap.width) % tileMap.width)
            peersState.values.forEach { p ->
                if (!peerOnPlayerPlane(p)) return@forEach
                val pLeftCol = floor(p.worldXPx / unitPx).toInt()
                val pBottomRow = floor(p.heightPx / unitPx).toInt()
                val pRows = pBottomRow..(pBottomRow + 2)
                val rowsOverlap = !(rows.last < pRows.first || rows.first > pRows.last)
                if (!rowsOverlap) return@forEach
                val c0 = (((pLeftCol) % tileMap.width) + tileMap.width) % tileMap.width
                val c1 = (((pLeftCol + 1) % tileMap.width) + tileMap.width) % tileMap.width
                if (colsSet.contains(c0) || colsSet.contains(c1)) return true
            }
            return false
        }

        fun isFrontBandBlockedByPeers(frontCol: Int, bottomRow: Int): Boolean {
            if (!connectionStatus) return false
            val rows = bottomRow..(bottomRow + 2)
            val fC = ((frontCol % tileMap.width) + tileMap.width) % tileMap.width
            peersState.values.forEach { p ->
                if (!peerOnPlayerPlane(p)) return@forEach
                val pLeftCol = floor(p.worldXPx / unitPx).toInt()
                val pBottomRow = floor(p.heightPx / unitPx).toInt()
                val pRows = pBottomRow..(pBottomRow + 2)
                val rowsOverlap = !(rows.last < pRows.first || rows.first > pRows.last)
                if (!rowsOverlap) return@forEach
                val c0 = (((pLeftCol) % tileMap.width) + tileMap.width) % tileMap.width
                val c1 = (((pLeftCol + 1) % tileMap.width) + tileMap.width) % tileMap.width
                if (fC == c0 || fC == c1) return true
            }
            return false
        }

        fun isBlockedAt(leftCol: Int, bottomRow: Int): Boolean {
            return isFootprintBlockedByTiles(leftCol, bottomRow) ||
                    isFootprintBlockedByMammy(leftCol, bottomRow) ||
                    isFootprintBlockedByZombies(leftCol, bottomRow) ||
                    isFootprintBlockedBySkeletons(leftCol, bottomRow) ||
                    isFootprintBlockedByPeers(leftCol, bottomRow)
        }

        fun isFrontBandBlocked(frontCol: Int, bottomRow: Int): Boolean {
            return isFrontBandBlockedByTiles(frontCol, bottomRow) ||
                    isFrontBandBlockedByMammy(frontCol, bottomRow) ||
                    isFrontBandBlockedByZombies(frontCol, bottomRow) ||
                    isFrontBandBlockedBySkeletons(frontCol, bottomRow) ||
                    isFrontBandBlockedByPeers(frontCol, bottomRow)
        }

        fun moveHorizontalSubStepped(currentXDp: Dp, deltaPx: Float, bottomPx: Float): Dp {
            if (deltaPx == 0f) return currentXDp
            var left = xDpToPx(currentXDp)
            var bottom = bottomPx
            val count = kotlin.math.ceil(abs(deltaPx) / (unitPx * 0.25f)).toInt().coerceIn(1, 256)
            val step = deltaPx / count
            repeat(count) {
                val target = left + step
                var resolved = applyHorizontalCollisionPx(left, target, bottom)
                if (abs(resolved - target) > 0.01f && !isJumping) {
                    // Test the actual destination footprint, including fractional-tile movement.
                    // Waiting for a whole tile boundary made small joystick steps stick forever.
                    val raised = bottom + unitPx
                    val cols = aabbOverlappingColumns(target)
                    val rows = floor((raised + 0.01f) / unitPx).toInt()..
                        floor((raised + playerHeightPx - 0.01f) / unitPx).toInt()
                    if (cols.none { c -> rows.any { r -> tileMap.get(c, r) } }) {
                        val stepped = applyHorizontalCollisionPx(left, target, raised)
                        if (abs(stepped - target) < 0.01f && isSupportedByTiles(target, raised)) {
                            bottom = raised
                            resolved = stepped
                        }
                    }
                }
                left = resolved
                if (!isJumping && !isSupportedByTiles(left, bottom) &&
                    !isSupportedByMammy(left, bottom) && supportingPeerTop(left, bottom) == null && bottom >= unitPx &&
                    isSupportedByTiles(left, bottom - unitPx)) bottom -= unitPx
            }
            heightPx = bottom
            if (worldConfig.exploration) {
                val start = ExplorationWorld.origin(currentBiome!!).along(activeWorldAxis)
                val length = if (activeWorldAxis == WorldAxis.Latitude) ExplorationWorld.REGION else ExplorationWorld.REGION_DEPTH
                left = left.coerceIn((start + ExplorationWorld.BORDER) * unitPx,
                    (start + length - ExplorationWorld.BORDER - 2) * unitPx)
            }
            val span = tileMap.width * unitPx
            return pxToDp(((left % span) + span) % span)
        }

        fun cellContainsEnemy(cell: Cell): Boolean {
            val cellLeft = wrapCol(cell.c) * unitPx
            val cellRight = cellLeft + unitPx
            val cellBottom = cell.r * unitPx
            val cellTop = cellBottom + unitPx
            val worldWidthPx = tileMap.width * unitPx
            fun overlaps(x: Float, bottom: Float): Boolean {
                if (cellTop <= bottom || cellBottom >= bottom + playerHeightPx) return false
                return floatArrayOf(x, x - worldWidthPx, x + worldWidthPx).any { left ->
                    !(cellRight <= left || cellLeft >= left + playerWidthPx)
                }
            }
            if(alien.targetPoint?.let { floor(it.other(activeWorldAxis)).toInt().mod(tileMap.width)==tileMap.activeSlice }==true &&
                alien.contains(if(activeWorldAxis==WorldAxis.Latitude) wrapCol(cell.c) else tileMap.activeSlice,
                    if(activeWorldAxis==WorldAxis.Longitude) wrapCol(cell.c) else tileMap.activeSlice,cell.r)) return true
            return zombies.any { isOnPlayerPlane(it.otherAxisPx) && overlaps(it.worldXPx, it.bottomPx) } ||
                skeletons.any { isOnPlayerPlane(it.otherAxisPx) && overlaps(it.worldXPx, it.bottomPx) }
        }

        // Core action helpers (host-authoritative). They return true if world changed.
        fun performHitAt(xDp: Dp, bottomPx: Float, facing: Direction, aimCol: Int? = null, aimRow: Int? = null, remoteInput: Boolean = false): Boolean {
            val xPx = xDpToPx(xDp)
            val candidates = MeleeTargeting.candidates(
                floor(xPx / unitPx).toInt(),
                floor((xPx + playerWidthPx - 0.001f) / unitPx).toInt(),
                floor(bottomPx / unitPx).toInt(),
                floor((bottomPx + playerHeightPx - 0.001f) / unitPx).toInt(), facing
            )
            val target = if (aimCol != null && aimRow != null) {
                // Validate against the original aim, never a direction inferred from the target.
                candidates.firstOrNull { wrapCol(it.c) == wrapCol(aimCol) && it.r == aimRow }
                    ?: return false
            } else MeleeTargeting.select(candidates) {
                tileMap.inY(it.r) && (tileMap.hasPieceAt(wrapCol(it.c), it.r) || cellContainsEnemy(it))
            }
            val targetCol = target.c
            val targetRow = target.r
            if (!tileMap.inY(targetRow)) return false
            // Wrap X for cell computations
            val wrappedX = wrapCol(targetCol)
            var anythingHit = false

            val targetLat = if (activeWorldAxis == WorldAxis.Latitude) wrappedX else tileMap.activeSlice
            val targetLon = if (activeWorldAxis == WorldAxis.Longitude) wrappedX else tileMap.activeSlice
            if (bedRound.bed?.contains(tileMap, targetLat, targetLon, targetRow) == true) {
                if (!remoteInput) bedRound.hit(targetLat, targetLon, targetRow)
                return true
            }
            if(alien.contains(targetLat,targetLon,targetRow)) {
                return alien.hit(bedRound.selfId,targetLat,targetLon,targetRow,equipment.loadout.punchDamage + if((specials.power[bedRound.selfId] ?: 0)>0) 2 else 0)
            }
            // Damage tile if present
            if (tileMap.hasPieceAt(wrappedX, targetRow)) {
                val damage = tileMap.damagePieceAt(wrappedX, targetRow, 10) ?: return false
                val remaining = damage.remainingHealth
                mapVersion++
                // If the block at this anchor was destroyed, grant a Block item to the player
                if (remaining <= 0) {
                    if (vm.addItemToInventory(ItemType.Block, 1, damage.material)) {
                        pushNotice(tr("${damage.material.name} block") + " +1")
                    }
                }
                anythingHit = true
            }

            // A block is the only target when present; otherwise hit one enemy in this cell.
            if (anythingHit) return true
            run {
                val cellLeft = wrappedX * unitPx
                val cellRight = cellLeft + unitPx
                val cellBottom = targetRow * unitPx
                val cellTop = cellBottom + unitPx
                val worldWidthPx = tileMap.width * unitPx
                val zombie = zombies.firstOrNull { z ->
                    if (!isOnPlayerPlane(z.otherAxisPx)) return@firstOrNull false
                    val zBottom = z.bottomPx
                    val zTop = zBottom + playerHeightPx
                    if (cellTop <= zBottom || cellBottom >= zTop) return@firstOrNull false
                    val baseLeft = z.worldXPx
                    val candidates = floatArrayOf(baseLeft, baseLeft - worldWidthPx, baseLeft + worldWidthPx)
                    candidates.any { zl -> !(cellRight <= zl || cellLeft >= zl + playerWidthPx) }
                }
                if (zombie != null) {
                    vm.damageZombie(zombie.id, unitPx, equipment.loadout.punchDamage + if((specials.power[bedRound.selfId] ?: 0)>0) 2 else 0)
                    anythingHit = true
                    return@run
                }
                val skeleton = skeletons.firstOrNull { s ->
                    if (!isOnPlayerPlane(s.otherAxisPx)) return@firstOrNull false
                    val sBottom = s.bottomPx
                    val sTop = sBottom + playerHeightPx
                    if (cellTop <= sBottom || cellBottom >= sTop) return@firstOrNull false
                    val baseLeft = s.worldXPx
                    val candidates = floatArrayOf(baseLeft, baseLeft - worldWidthPx, baseLeft + worldWidthPx)
                    candidates.any { sl -> !(cellRight <= sl || cellLeft >= sl + playerWidthPx) }
                }
                if (skeleton != null) {
                    vm.damageSkeleton(skeleton.id, unitPx, equipment.loadout.punchDamage + if((specials.power[bedRound.selfId] ?: 0)>0) 2 else 0)
                    anythingHit = true
                }
            }

            return anythingHit
        }

        fun performPlaceAt(xDp: Dp, bottomPx: Float, facing: Direction, aimCol: Int? = null, aimRow: Int? = null): Boolean {
            val pieceWidth = selectedShape.width
            val pieceHeight = selectedShape.height
            val xPx = xDpToPx(xDp)
            val centerXPx = xPx + playerWidthPx / 2f
            val colCenter = floor(centerXPx / unitPx).toInt()
            val leftCol = floor((xPx) / unitPx).toInt()
            val rightCol = floor((xPx + playerWidthPx - 0.001f) / unitPx).toInt()
            val bottomRow = floor((bottomPx - 0.001f) / unitPx).toInt()
            val topRow = floor(((bottomPx + playerHeightPx - 0.001f) / unitPx)).toInt()

            fun canPlaceAt(anchorCol: Int, anchorRow: Int): Boolean {
                if (!tileMap.inY(anchorRow) || !tileMap.inY(anchorRow + pieceHeight - 1)) return false
                // Prevent placing exactly where the player stands (disallow true AABB overlap)
                val playerCols = playerOverlappingColumns(xPx)
                val playerRows = playerOverlappingRows(bottomPx)
                val placeCols = anchorCol..(anchorCol + pieceWidth - 1)
                val placeRows = anchorRow..(anchorRow + pieceHeight - 1)
                val overlapCols = placeCols.any { it in playerCols }
                val overlapRows = placeRows.any { it in playerRows }
                if (overlapCols && overlapRows) return false
                // Prevent placing over remote peers (treat peers as 2x3 AABBs)
                if (connectionStatus) {
                    peersState.values.forEach { peer ->
                        if (!peerOnPlayerPlane(peer)) return@forEach
                        val pCol0 = floor(peer.worldXPx / unitPx).toInt()
                        val pCol1 = floor((peer.worldXPx + playerWidthPx - 0.001f) / unitPx).toInt()
                        val pRow0 = floor((peer.heightPx - 0.001f) / unitPx).toInt()
                        val pRow1 = floor(((peer.heightPx + playerHeightPx - 0.001f) / unitPx)).toInt()
                        val colsOverlap = (anchorCol..(anchorCol + pieceWidth - 1)).any { c ->
                            val cw = ((c % tileMap.width) + tileMap.width) % tileMap.width
                            cw in pCol0..pCol1
                        }
                        val rowsOverlap = (anchorRow..(anchorRow + pieceHeight - 1)).any { r -> r in pRow0..pRow1 }
                        if (colsOverlap && rowsOverlap) return false
                    }
                }
                // Ensure the shape's occupied region is empty (respect X wrapping).
                for (dx in 0 until pieceWidth) {
                    val cx = ((anchorCol + dx) % tileMap.width + tileMap.width) % tileMap.width
                    for (dy in 0 until pieceHeight) {
                        val ry = anchorRow + dy
                        if (!tileMap.inY(ry) || tileMap.get(cx, ry)) return false
                    }
                }
                return true
            }

            // Choose anchor for a 3x3 block. Prefer aimed cell if provided and adjacent; otherwise use facing.
            var targetCol = 0
            var targetRow = 0
            var hasTarget = false

            fun isAdjacentToPlayerCell(c: Int, r: Int): Boolean {
                val playerCols = playerOverlappingColumns(xPx)
                val playerRows = playerOverlappingRows(bottomPx)
                val centerBand = (colCenter - 1)..(colCenter + 1)
                val adjacentLeft = (c == leftCol - 1 && r in playerRows)
                val adjacentRight = (c == rightCol + 1 && r in playerRows)
                val adjacentUp = (r == topRow + 1 && c in centerBand)
                val adjacentDown = (r == bottomRow - 1 && c in centerBand)
                return adjacentLeft || adjacentRight || adjacentUp || adjacentDown
            }

            fun anchorFromAimCell(c: Int, r: Int): Pair<Int, Int>? {
                // Determine which side the aimed cell lies on
                return when {
                    c < leftCol -> Pair(c - 2, r) // left side: aimed cell is block's rightmost column
                    c > rightCol -> Pair(c, r)    // right side: aimed cell is block's leftmost column
                    r > topRow -> Pair(c - 1, r)  // above head: center horizontally on aim; aim is bottom row of block
                    r < bottomRow -> Pair(c - 1, r - 2) // below feet: aim is top row
                    else -> null
                }
            }

            if (aimCol != null && aimRow != null && isAdjacentToPlayerCell(aimCol, aimRow)) {
                val anchor = anchorFromAimCell(aimCol, aimRow)
                if (anchor != null) {
                    targetCol = anchor.first
                    targetRow = anchor.second
                    hasTarget = canPlaceAt(targetCol, targetRow)
                }
            }

            if (!hasTarget) {
                when (facing) {
                    Direction.Left -> {
                        targetCol = leftCol - 3
                        val pr = playerOverlappingRows(bottomPx)
                        targetRow = pr.first
                        val placeRows = targetRow..(targetRow + 2)
                        val touchesVertically = placeRows.any { it in pr }
                        hasTarget = touchesVertically && canPlaceAt(targetCol, targetRow)
                    }
                    Direction.Right -> {
                        targetCol = rightCol + 1
                        val pr = playerOverlappingRows(bottomPx)
                        targetRow = pr.first
                        val placeRows = targetRow..(targetRow + 2)
                        val touchesVertically = placeRows.any { it in pr }
                        hasTarget = touchesVertically && canPlaceAt(targetCol, targetRow)
                    }
                    // Diagonals: synthesize an aimed adjacent cell and reuse anchorFromAimCell logic
                    Direction.UpLeft -> {
                        val aimR = topRow + 1
                        anchorFromAimCell(leftCol, aimR)?.let { (ac, ar) ->
                            targetCol = ac; targetRow = ar; hasTarget = canPlaceAt(targetCol, targetRow)
                        }
                    }
                    Direction.LeftUp -> {
                        val aimC = leftCol - 1
                        anchorFromAimCell(aimC, topRow)?.let { (ac, ar) ->
                            targetCol = ac; targetRow = ar; hasTarget = canPlaceAt(targetCol, targetRow)
                        }
                    }
                    Direction.UpRight -> {
                        val aimR = topRow + 1
                        anchorFromAimCell(rightCol, aimR)?.let { (ac, ar) ->
                            targetCol = ac; targetRow = ar; hasTarget = canPlaceAt(targetCol, targetRow)
                        }
                    }
                    Direction.RightUp -> {
                        val aimC = rightCol + 1
                        anchorFromAimCell(aimC, topRow)?.let { (ac, ar) ->
                            targetCol = ac; targetRow = ar; hasTarget = canPlaceAt(targetCol, targetRow)
                        }
                    }
                    Direction.DownLeft -> {
                        val aimR = bottomRow - 1
                        anchorFromAimCell(leftCol, aimR)?.let { (ac, ar) ->
                            targetCol = ac; targetRow = ar; hasTarget = canPlaceAt(targetCol, targetRow)
                        }
                    }
                    Direction.LeftDown -> {
                        val aimC = leftCol - 1
                        anchorFromAimCell(aimC, bottomRow)?.let { (ac, ar) ->
                            targetCol = ac; targetRow = ar; hasTarget = canPlaceAt(targetCol, targetRow)
                        }
                    }
                    Direction.DownRight -> {
                        val aimR = bottomRow - 1
                        anchorFromAimCell(rightCol, aimR)?.let { (ac, ar) ->
                            targetCol = ac; targetRow = ar; hasTarget = canPlaceAt(targetCol, targetRow)
                        }
                    }
                    Direction.RightDown -> {
                        val aimC = rightCol + 1
                        anchorFromAimCell(aimC, bottomRow)?.let { (ac, ar) ->
                            targetCol = ac; targetRow = ar; hasTarget = canPlaceAt(targetCol, targetRow)
                        }
                    }
                }
            }

            if (!hasTarget) return false

            // Place a material/shape-aware voxel piece on the currently active axis.
            if (isHostSelected == false) {
                val material = inventory.getOrNull(vm.selectedSlot.intValue)?.material ?: return false
                return requestWorldAction("PLACE", listOf(wrapCol(targetCol).toString(), targetRow.toString(), material.ordinal.toString(), selectedShape.ordinal.toString()), spendSelected = true)
            }
            val id = vm.bag.placeBlock(vm.selectedSlot.intValue, tileMap, targetCol, targetRow, selectedShape)
            if (id != -1) {
                audio?.play(SoundEffect.Place)
                mapVersion++
                val wrappedX = ((targetCol % tileMap.width) + tileMap.width) % tileMap.width
                return true
            }
            return false
        }

        // Keep melee targets within the joystick's direction, including repeated swings.
        fun hit() {
            if (isAxisTransitioning || controlsBlocked) return
            combatMotion.start(mp?.getSelfId() ?: 0)
            audio?.play(SoundEffect.Hit)
            val xPx = xDpToPx(playerWorldXDp)
            val joyMag = sqrt(joystickX * joystickX + joystickY * joystickY)
            val facing = if (joyMag > 0.15f) vectorToDirection(joystickX, joystickY) else lastDirection
            val candidates = MeleeTargeting.candidates(
                floor(xPx / unitPx).toInt(),
                floor((xPx + playerWidthPx - 0.001f) / unitPx).toInt(),
                floor(heightPx / unitPx).toInt(),
                floor((heightPx + playerHeightPx - 0.001f) / unitPx).toInt(), facing
            )
            if (worldConfig.exploration && !isJumping) {
                val position = localRopePoint()
                val portal = ExplorationWorld.hitPortal(position.lat, position.lon, position.row,
                    activeWorldAxis, candidates, tileMap.portals)
                if (portal != null) { travelThroughPortal(portal); return }
            }
            val aimed = MeleeTargeting.select(candidates) {
                tileMap.inY(it.r) && (tileMap.hasPieceAt(wrapCol(it.c), it.r) || cellContainsEnemy(it))
            }
            val aimColWrapped = wrapCol(aimed.c)
            val aimRow = aimed.r

            // Update preview state so the UI reflects the selection during the press
            previewSelectedAimCol = aimColWrapped
            previewSelectedAimRow = aimRow
            previewSelectedFacing = facing

            if (isHostSelected == false) {
                val lat = if (activeWorldAxis == WorldAxis.Latitude) aimColWrapped else tileMap.activeSlice
                val lon = if (activeWorldAxis == WorldAxis.Longitude) aimColWrapped else tileMap.activeSlice
                if (bedRound.bed?.contains(tileMap, lat, lon, aimRow) == true) bedRound.hit(lat, lon, aimRow)
                else requestWorldAction("HIT", listOf(aimColWrapped.toString(), aimRow.toString(), facing.ordinal.toString()))
                return
            }
            performHitAt(playerWorldXDp, heightPx, facing, aimed.c, aimRow)
        }

        // Place logic: place a 3x3 block into the adjacent cell (anchor) in last look direction
        fun place() {
            if (isAxisTransitioning || controlsBlocked) return
            val slot = inventory.getOrNull(vm.selectedSlot.intValue)
            if (slot == null || slot.type == null || slot.count <= 0) {
                // Nothing selected
                return
            }
            when (slot.type) {
                ItemType.Block -> {
                    val aimC = previewSelectedAimCol
                    val aimR = previewSelectedAimRow
                    performPlaceAt(playerWorldXDp, heightPx, lastDirection, aimC, aimR)
                }
                ItemType.Potion -> {
                    if (playerHp < 5) {
                        playerHp += 1
                        vm.consumeSelectedItem()
                        consumeSequence++
                        audio?.play(SoundEffect.Drink)
                        pushNotice(tr("+1 heart"))
                    } else {
                        pushNotice(tr("Hearts are full"))
                    }
                }
                else -> { /* no-op */ }
            }
        }

        // Jump and vertical physics with tile collisions
        fun jump() {
            if (isAxisTransitioning || controlsBlocked) return
            if (vineSwing != null || vineStand != null) {
                vineSwing=null;vineStand=null;vineRegrabAfter=currentTimeMillis()+500
            }
            if (isJumping) return
            audio?.play(SoundEffect.Jump)
            isJumping = true
            if (isHostSelected == false) {
                mp?.sendInput(left = false, right = false, jump = true, hit = false)
            }
            scope.launch {
                fun overlapsMammyHorizontally(xPx: Float): Boolean {
                    if (!mammyVisible) return false
                    val cols = playerOverlappingColumns(xPx)
                    return cols.any {
                        val wrapped = ((it % tileMap.width) + tileMap.width) % tileMap.width
                        wrapped == mammyGoalCol || wrapped == (mammyGoalCol + 1) % tileMap.width
                    }
                }
                val g = -3000f // px/s^2
                val targetJumpHeightPx = unitPx * equipment.loadout.jumpHeight
                var v = sqrt(2f * -g * targetJumpHeightPx) // px/s upward
                val frameMs = 16L
                val dt = PlayerPhysicsClock.STEP_SECONDS
                verticalClock.reset(nanoTime())
                physics@ while (true) {
                    if (isPaused || isAxisTransitioning) {
                        verticalClock.reset()
                        delay(frameMs)
                        continue
                    }
                    for (step in 0 until verticalClock.advance(nanoTime())) {
                        val prevBottom = heightPx
                        v += g * dt
                        heightPx += v * dt

                        val xPx = xDpToPx(playerWorldXDp)
                        val cols = playerOverlappingColumns(xPx)

                        // Ceiling collision when ascending: sweep all crossed rows and clamp to the first tile bottom
                        if (v > 0f) {
                            val prevTop = prevBottom + playerHeightPx
                            val nowTop = heightPx + playerHeightPx
                            val startRow = floor(prevTop / unitPx).toInt()
                            val endRow = floor(nowTop / unitPx).toInt()
                            if (endRow >= startRow) {
                                var hitRow: Int? = null
                                // Check every row boundary we crossed this frame (handles fast/tunneling motion)
                                for (row in (startRow + 1)..endRow) {
                                    var hit = false
                                    for (c in cols) {
                                        if (tileMap.get(c, row)) { hit = true; break }
                                    }
                                    if (hit) { hitRow = row; break }
                                }
                            if (hitRow != null) {
                                    val tileBottomPx = hitRow!! * unitPx
                                    heightPx = tileBottomPx - playerHeightPx
                                    v = 0f
                                }
                            }
                            // Mammy is a solid one-cell-wide, three-cell-high character.
                            val mammyBottomPx = mammyGoalBottomRow * unitPx
                            if (overlapsMammyHorizontally(xPx) && prevTop <= mammyBottomPx && nowTop >= mammyBottomPx) {
                                heightPx = mammyBottomPx - playerHeightPx
                                v = 0f
                            }
                            // Also collide with peers' bottoms when ascending (no pass-through)
                            if (connectionStatus) {
                                val myPrevTop = prevBottom + playerHeightPx
                                val myNowTop = heightPx + playerHeightPx
                                peersState.values.forEach { peer ->
                            if (!peerOnPlayerPlane(peer)) return@forEach
                                    val pBottom = peer.heightPx
                                    val px = peer.worldXPx
                                    val pCols = run {
                                        val c0 = floor(px / unitPx).toInt()
                                        val c1 = floor((px + playerWidthPx - 0.001f) / unitPx).toInt()
                                        c0..c1
                                    }
                                    val horizOverlap = cols.any { it in pCols }
                                    if (horizOverlap && myPrevTop <= pBottom && myNowTop >= pBottom) {
                                        heightPx = pBottom - playerHeightPx
                                        v = 0f
                                    }
                                }
                            }
                            // Also collide with any zombie bottom when ascending
                            zombies.forEach { z ->
                                if (!isOnPlayerPlane(z.otherAxisPx)) return@forEach
                                val myPrevTop = prevBottom + playerHeightPx
                                val myNowTop = heightPx + playerHeightPx
                                val zBottom = z.bottomPx
                                val zCols = run {
                                    val c0 = floor(z.worldXPx / unitPx).toInt()
                                    val c1 = floor((z.worldXPx + playerWidthPx - 0.001f) / unitPx).toInt()
                                    c0..c1
                                }
                                val horizOverlap = cols.any { it in zCols }
                                if (horizOverlap && myPrevTop <= zBottom && myNowTop >= zBottom) {
                                    heightPx = zBottom - playerHeightPx
                                    v = 0f
                                }
                            }
                            // Safety: if after integration our top is already inside a solid tile (e.g., block placed above), resolve
                            run {
                                val topRowNow = floor(((heightPx + playerHeightPx - 0.001f) / unitPx)).toInt()
                                var overlap = false
                                for (c in cols) { if (tileMap.get(c, topRowNow)) { overlap = true; break } }
                                if (overlap) {
                                    val tileBottomPx = topRowNow * unitPx
                                    heightPx = tileBottomPx - playerHeightPx
                                    v = 0f
                                }
                            }
                        }

                        // Landing when descending: player's bottom crosses any tile top between prev and now
                        if (v <= 0f) {
                            val startRow = floor((prevBottom - 0.001f) / unitPx).toInt()
                            val endRow = floor((heightPx) / unitPx).toInt()
                            if (endRow < startRow || heightPx <= 0f) {
                                var landingTopPx: Float? = null
                                // Sweep all crossed row tops and find the highest top we intersected
                                for (row in startRow downTo max(endRow, 0)) {
                                    var hit = false
                                    for (c in cols) { if (tileMap.get(c, row)) { hit = true; break } }
                                    if (hit) {
                                        val topPx = (row + 1) * unitPx
                                        if (topPx in heightPx..prevBottom) {
                                            landingTopPx =
                                                max(landingTopPx ?: Float.NEGATIVE_INFINITY, topPx)
                                        }
                                    }
                                }
                                if (overlapsMammyHorizontally(xPx)) {
                                    val mammyTop = (mammyGoalBottomRow + 3) * unitPx
                                    if (mammyTop in heightPx..prevBottom) {
                                        landingTopPx = max(landingTopPx ?: Float.NEGATIVE_INFINITY, mammyTop)
                                    }
                                }
                                // Also consider landing on peers' tops (allow stacking)
                                if (connectionStatus) {
                                    peersState.values.forEach { peer ->
                            if (!peerOnPlayerPlane(peer)) return@forEach
                                        val px = peer.worldXPx
                                        val pCols = run {
                                            val c0 = floor(px / unitPx).toInt()
                                            val c1 = floor((px + playerWidthPx - 0.001f) / unitPx).toInt()
                                            c0..c1
                                        }
                                        val horizOverlap = cols.any { it in pCols }
                                        if (horizOverlap) {
                                            val peerTop = peer.heightPx + playerHeightPx
                                            if (peerTop in heightPx..prevBottom) {
                                                landingTopPx =
                                                    max(landingTopPx ?: Float.NEGATIVE_INFINITY, peerTop)
                                            }
                                        }
                                    }
                                }
                                // Also consider landing on any zombie's top
                                zombies.forEach { z ->
                                    if (!isOnPlayerPlane(z.otherAxisPx)) return@forEach
                                    val zCols = run {
                                        val c0 = floor(z.worldXPx / unitPx).toInt()
                                        val c1 = floor((z.worldXPx + playerWidthPx - 0.001f) / unitPx).toInt()
                                        c0..c1
                                    }
                                    val horizOverlap = cols.any { it in zCols }
                                    if (horizOverlap) {
                                        val zTop = z.bottomPx + playerHeightPx
                                        if (zTop in heightPx..prevBottom) {
                                            landingTopPx =
                                                max(landingTopPx ?: Float.NEGATIVE_INFINITY, zTop)
                                        }
                                    }
                                }
                                // Also consider landing on any skeleton's top
                                skeletons.forEach { s ->
                                    if (!isOnPlayerPlane(s.otherAxisPx)) return@forEach
                                    val sCols = run {
                                        val c0 = floor(s.worldXPx / unitPx).toInt()
                                        val c1 = floor((s.worldXPx + playerWidthPx - 0.001f) / unitPx).toInt()
                                        c0..c1
                                    }
                                    val horizOverlap = cols.any { it in sCols }
                                    if (horizOverlap) {
                                        val sTop = s.bottomPx + playerHeightPx
                                        if (sTop in heightPx..prevBottom) {
                                            landingTopPx =
                                                max(landingTopPx ?: Float.NEGATIVE_INFINITY, sTop)
                                        }
                                    }
                                }
                                if (landingTopPx != null) {
                                    heightPx = landingTopPx
                                    isJumping = false
                                    break@physics
                                }
                                if (heightPx <= 0f) {
                                    heightPx = 0f
                                    isJumping = false
                                    break@physics
                                }
                            }
                        }

                        // Bottom-overlap safety: if feet ended up inside a solid tile, snap to its top
                        var shouldBreak = false
                        run {
                            val bottomRowNow = floor((heightPx - 0.001f) / unitPx).toInt()
                            if (bottomRowNow >= 0) {
                                var overlap = false
                                for (c in cols) { if (tileMap.get(c, bottomRowNow)) { overlap = true; break } }
                                if (overlap) {
                                    val topPx = (bottomRowNow + 1) * unitPx
                                    heightPx = topPx
                                    if (v < 0f) v = 0f
                                    // Consider this a landing to avoid re-entering the tile next frame
                                    isJumping = false
                                    shouldBreak = true
                                }
                            }
                        }
                        if (shouldBreak) break@physics
                    }

                    delay(frameMs)
                }
            }
        }

        val worldStream = remember(tileMap) { WorldPacketStream() }
        fun sendWorld(kind: String, body: String) {
            worldStream.packets(kind, body).forEach { mp?.sendRoundEvent(it) }
        }
        fun publishEntities() = sendWorld("ENTITIES", WorldReplication.encodeEntities(vm.worldEntities(activeWorldAxis, unitPx)))
        fun publishBaseline() {
            sendWorld("BASE", WorldReplication.encodeBlocks(tileMap.networkBlocks().map { it.id to it }))
            publishEntities()
            bedRound.publish()
            companion.publish()
            alien.publish()
            dog.publish()
            specials.publish()
            shocks.publish()
            sendWorld("READY", "")
        }

        fun applyRemoteAction(actor: Int, action: WorldAction): String = if(specials.event?.intro == true || shocks.active("P",actor.toLong())) "false;_;_" else SharedWorldAuthority(
            tileMap, vm, unitPx, playerWidthPx, playerHeightPx, activeWorldAxis, bedRound.position,
            positions = { (peersState.keys + bedRound.selfId).mapNotNull(bedRound.position) },
            bed = { bedRound.bed }, hitBed = { lat, lon, row, id -> bedRound.hit(lat, lon, row, id) },
            hitAlien = { actor,lat,lon,row,damage -> alien.hit(actor,lat,lon,row,damage) },
            punchDamage = { actor -> (peerEquipment[actor] ?: EquipmentLoadout()).punchDamage + if((specials.power[actor] ?: 0)>0) 2 else 0 },
            onWorldChanged = { mapVersion++ }
        ).apply(actor, action)

        val receivedDamage = remember { mutableSetOf<Long>() }
        vm.remotePlayerDamage = { id, damage -> mp?.sendRoundEvent("DAMAGE;${nanoTime()};$id;$damage") }
        fun applyPeerPosition(id: Int, parts: List<String>) {
            if (id == mp?.getSelfId()) return
            val lat = parts[0].toInt()
            val lon = parts[1].toInt()
            val bottom = parts[2].toInt()
            val peer = peersState.getOrPut(id) { RemotePeer() }
            val changedRegion = worldConfig.exploration && peer.latitude?.let { ExplorationWorld.biome(it, peer.longitude ?: 10) != ExplorationWorld.biome(lat / 1000, lon / 1000) } == true
            peer.latitudeMilli = lat; peer.longitudeMilli = lon
            peer.latitude = tileMap.wrap(lat / 1000); peer.longitude = tileMap.wrap(lon / 1000)
            peer.targetXPx = (if (activeWorldAxis == WorldAxis.Latitude) lat else lon) / 1000f * unitPx
            peer.targetHeightPx = bottom / 1000f * unitPx
            if (!peer.hasPos || peer.projectedAxis != activeWorldAxis || changedRegion) {
                peer.worldXPx = peer.targetXPx; peer.heightPx = peer.targetHeightPx
                peer.worldXDp = pxToDp(peer.worldXPx); peer.hasPos = true
            }
            peer.projectedAxis = activeWorldAxis
            peerBlocking[id] = parts.getOrNull(8)?.toBooleanStrictOrNull()==true
            combatMotion.receive(id,parts.getOrNull(9)?.toIntOrNull() ?: 0)
            peerWorldAxes[id] = WorldAxis.entries.getOrElse(parts[4].toInt()) { WorldAxis.Latitude }
            peer.facingRight = parts[3].toBooleanStrict()
            peer.hp = parts[5].toInt().coerceIn(0, 5)
            peer.outfit = outfitFromPlayerState(parts)
            peerEquipment[id] = EquipmentLoadout.decode(parts.getOrNull(7))
            val vine=parts.getOrNull(10)?.toIntOrNull()?.let { SwingVines.all.getOrNull(it) }
            val angle=parts.getOrNull(11)?.toFloatOrNull()
            val reach=parts.getOrNull(12)?.toFloatOrNull() ?: vine?.length ?: 21f
            val swing=if(vine!=null && angle!=null && angle.isFinite() && angle in -1.05f..1.05f && reach.isFinite() && reach in vine.topReach..vine.length) VineSwing(vine,angle,reach=reach) else null
            if(swing!=null && kotlin.math.abs(swing.point.lat-lat/1000f)<3 && kotlin.math.abs(swing.point.row-bottom/1000f)<3 && kotlin.math.abs(swing.vine.longitude-lon/1000f)<2)
                peerSwings[id]=swing else peerSwings.remove(id)
        }

        // Transport callbacks can arrive on socket threads; all game mutations stay on Main.
        LaunchedEffect(mp, isHostSelected, tileMap) {
            mp?.updateListener(object : MultiplayerListenerAdapter() {
                override fun onRoundEvent(senderId: Int, payload: String) {
                    scope.launch {
                        runCatching {
                            when {
                                specials.receive(senderId,payload) -> Unit
                                dog.receive(senderId,payload) -> Unit
                                shocks.receive(senderId,payload) -> Unit
                                alien.receive(senderId,payload) -> Unit
                                companion.receive(senderId,payload) -> Unit
                                payload.startsWith("ROPE_TUG;") && senderId==0 && isHostSelected==false -> {
                                    val p=payload.split(';')
                                    if(p[2].toInt()==mp.getSelfId() && seenTugs.add(p[1].toLong())) {
                                        val target=RopePoint.decode(p[3])
                                        if(companion.distance(localRopePoint(),target)<=4f) {
                                            playerWorldXDp=unit*target.along(activeWorldAxis)
                                            playerOtherAxisDp=unit*target.other(activeWorldAxis)
                                        }
                                    }
                                }
                                payload.startsWith("SOUND;") && senderId == 0 && isHostSelected == false -> {
                                    SoundEffect.entries.firstOrNull { it.name == payload.substringAfter(';') }
                                        ?.let { audio?.play(it) }
                                }
                                payload.startsWith("PLAYER;") && isHostSelected == true -> {
                                    val body = payload.substringAfter(';')
                                    applyPeerPosition(senderId, body.split(';'))
                                    mp.sendRoundEvent("PLAYER_STATE;$senderId;$body")
                                }
                                payload.startsWith("PLAYER_LEFT;") && senderId == 0 && isHostSelected == false -> {
                                    peersState.remove(payload.substringAfter(';').toInt())
                                }
                                payload.startsWith("PLAYER_STATE;") && senderId == 0 && isHostSelected == false -> {
                                    val parts = payload.split(';')
                                    applyPeerPosition(parts[1].toInt(), parts.drop(2))
                                }
                                payload.startsWith("DAMAGE;") && senderId == 0 && isHostSelected == false -> {
                                    val parts = payload.split(';')
                                    if (parts[2].toInt() == mp.getSelfId() && receivedDamage.add(parts[1].toLong())) {
                                        playerHp = (playerHp - equipment.damage(parts[3].toInt().coerceIn(0, 5))).coerceAtLeast(0)
                                        playerFlashUntil = currentTimeMillis() + 150L
                                    }
                                }
                                payload.startsWith("ACTION;") && isHostSelected == true -> {
                                    val action = WorldAction.decode(payload)
                                    val result = actionReceipts.resolve(action.id) {
                                        runCatching { applyRemoteAction(senderId, action) }.getOrDefault("false;_;_")
                                    }
                                    mp.sendRoundEvent("ACTION_RESULT;${action.id};$senderId;$result")
                                }
                                payload.startsWith("ACTION_RESULT;") && senderId == 0 && isHostSelected == false -> {
                                    val p = payload.split(';')
                                    if (p[2].toInt() == mp.getSelfId()) {
                                        val pending = pendingActions.remove(p[1].toLong()) ?: return@launch
                                        if (pending.action.verb == "CRAFT") {
                                            craftingPending = false
                                            if (p[3].toBooleanStrict()) finishCraft(pending.action.arguments.single())
                                            else pushNotice(tr("Move closer to the workbench"))
                                        }
                                        if (p[3].toBooleanStrict()) {
                                            when (pending.action.verb) {
                                                "PLACE" -> audio?.play(SoundEffect.Place)
                                                else -> Unit
                                            }
                                        }
                                        if (!p[3].toBooleanStrict()) pending.cost?.let { vm.bag.add(it.type!!, 1, it.material) }
                                        else if (p[4] != "_") {
                                            val type = ItemType.valueOf(p[4])
                                            val material = if (p[5] == "_") null else BlockMaterial.valueOf(p[5])
                                            if (!vm.bag.add(type, 1, material)) pushNotice(tr("Inventory full"))
                                        }
                                    }
                                }
                                payload.startsWith("WORLD_READY;") && isHostSelected == true -> {
                                    mp.sendRoundEvent("WORLD_CONFIG;${tileMap.width};${tileMap.height};${worldConfig.exploration};${worldConfig.unlimited};${worldConfig.portalSeed}")
                                    val dimensions = payload.split(';')
                                    if (dimensions.getOrNull(1)?.toIntOrNull() == tileMap.width && dimensions.getOrNull(2)?.toIntOrNull() == tileMap.height && dimensions.getOrNull(3)?.toBooleanStrictOrNull() == worldConfig.exploration && dimensions.getOrNull(4)?.toBooleanStrictOrNull() == worldConfig.unlimited && dimensions.getOrNull(5)?.toIntOrNull() == worldConfig.portalSeed) {
                                        publishBaseline()
                                        bedRound.replayRewards(senderId)
                                    }
                                }
                                payload.startsWith("WORLD_CONFIG;") && senderId == 0 && isHostSelected == false -> {
                                    val parts = payload.split(';')
                                    val width = parts[1].toInt()
                                    val height = parts[2].toInt()
                                    require(width in listOf(192, 384, 576) && height == worldConfig.height)
                                    val exploration = parts.getOrNull(3)?.toBooleanStrictOrNull() ?: false
                                    require(!exploration || width == ExplorationWorld.SIZE)
                                    val unlimited = parts.getOrNull(4)?.toBooleanStrictOrNull() ?: false
                                    val portalSeed = parts.getOrNull(5)?.toIntOrNull() ?: 0
                                    if (width != tileMap.width || exploration != worldConfig.exploration || unlimited != worldConfig.unlimited || portalSeed != worldConfig.portalSeed)
                                        onWorldConfig(worldConfig.copy(horizontalSize = width, exploration = exploration, unlimited = unlimited, portalSeed = portalSeed))
                                }
                                payload.startsWith("WORLD;") && senderId == 0 && isHostSelected == false -> {
                                    val transaction = worldStream.receive(payload) ?: return@launch
                                    when (transaction.first) {
                                        "BASE", "TERRAIN" -> {
                                            val changes = WorldReplication.decodeBlocks(transaction.second, tileMap)
                                            tileMap.applyNetworkBlocks(changes, reset = transaction.first == "BASE")
                                            mapVersion++
                                        }
                                        "READY" -> syncingWorld = false
                                        "ENTITIES" -> vm.applyWorldEntities(WorldReplication.decodeEntities(transaction.second), activeWorldAxis, unitPx, ::pxToDp)
                                    }
                                }
                                else -> bedRound.receive(senderId, payload)
                            }
                        }.onFailure {
                            if (isHostSelected == false && payload.startsWith("WORLD")) {
                                syncingWorld = true
                                pushNotice(tr("Resynchronizing the shared world…"))
                                mp.sendRoundEvent("WORLD_READY;${tileMap.width};${tileMap.height};${worldConfig.exploration};${worldConfig.unlimited};${worldConfig.portalSeed}")
                            }
                        }
                    }
                }
                override fun onPeerPos(id: Int, col: Int, row: Int, facingRight: Boolean, latitude: Int?, longitude: Int?) {
                    scope.launch {
                        if (id == mp.getSelfId()) return@launch
                        val peer = peersState.getOrPut(id) { RemotePeer() }
                        if (peer.latitudeMilli != null) return@launch
                        peer.latitude = latitude
                        peer.longitude = longitude
                        peer.targetXPx = (if (activeWorldAxis == WorldAxis.Latitude) latitude ?: col else longitude ?: col) * unitPx
                        peer.targetHeightPx = row * unitPx
                        if (!peer.hasPos) {
                            peer.worldXPx = peer.targetXPx
                            peer.heightPx = peer.targetHeightPx
                            peer.worldXDp = pxToDp(peer.worldXPx)
                            peer.hasPos = true
                        }
                        peer.facingRight = facingRight
                    }
                }
                override fun onPeerConnected(id: Int) { scope.launch {
                    if (id != mp.getSelfId()) {
                        peersState.getOrPut(id) { RemotePeer() }
                        pushNotice(tr("Player #{0} joined", id))
                    }
                } }
                override fun onPeerDisconnected(id: Int) { scope.launch {
                    peersState.remove(id)
                    if (isHostSelected == true) mp.sendRoundEvent("PLAYER_LEFT;$id")
                    pushNotice(tr("Player #{0} left", id))
                } }
                override fun onConnectionChanged(connected: Boolean) { scope.launch {
                    connectionStatus = connected
                    pushNotice(if (connected) tr("Connected") else tr("Connection lost"))
                    if (isHostSelected == false) syncingWorld = true
                    if (connected) {
                        mp.sendSelectedPlayer(player.name)
                        if (isHostSelected == false) mp.sendRoundEvent("WORLD_READY;${tileMap.width};${tileMap.height};${worldConfig.exploration};${worldConfig.unlimited};${worldConfig.portalSeed}")
                    }
                } }
                override fun onPeerSelectedPlayer(id: Int, name: String) { scope.launch {
                    if (id != mp.getSelfId()) peersState.getOrPut(id) { RemotePeer() }.selected =
                        listOf(Player.Leo, Player.Ian, Player.Papa).firstOrNull { it.name == name }
                } }
            })
        }

        LaunchedEffect(connectionStatus, isHostSelected, unitPx, tileMap) {
            while (connectionStatus && isHostSelected == true) {
                val changes = tileMap.takeNetworkChanges()
                if (changes.isNotEmpty()) sendWorld("TERRAIN", WorldReplication.encodeBlocks(changes))
                publishEntities()
                delay(100L)
            }
        }

        LaunchedEffect(tileMap, isHostSelected, unitPx) {
            if (isHostSelected == false) return@LaunchedEffect
            var last = nanoTime()
            while (true) {
                androidx.compose.runtime.withFrameNanos {
                    val now = nanoTime()
                    val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    last = now
                    if (!isPaused) vm.advanceProjectiles(tileMap, unitPx, playerWidthPx, playerHeightPx, dt)
                }
            }
        }

        LaunchedEffect(connectionStatus, isHostSelected, unitPx, tileMap.width) {
            val worldWidthPx = tileMap.width * unitPx
            while (connectionStatus && isHostSelected == false) {
                vm.interpolateRemoteSnapshots(worldWidthPx, ::pxToDp)
                delay(16L)
            }
        }

        // Fixed-rate, sub-tile world positions are independent of rendering and screen density.
        LaunchedEffect(connectionStatus, isHostSelected, unitPx) {
            while (connectionStatus) {
                if (!syncingWorld) {
                    val lat = xDpToPx(if (activeWorldAxis == WorldAxis.Latitude) playerWorldXDp else playerOtherAxisDp)
                    val lon = xDpToPx(if (activeWorldAxis == WorldAxis.Longitude) playerWorldXDp else playerOtherAxisDp)
                    val body = "${(lat / unitPx * 1000).toInt()};${(lon / unitPx * 1000).toInt()};${(heightPx / unitPx * 1000).toInt()};${lastDirection.isFacingRight()};${activeWorldAxis.ordinal};$playerHp;${outfit.wireId};${equipment.loadout.encode()};${equipment.blocking};${combatMotion.remaining[mp?.getSelfId() ?: 0] ?: 0};${vineSwing?.let { SwingVines.all.indexOf(it.vine) } ?: -1};${vineSwing?.angle ?: 0f};${vineSwing?.reach ?: 21f}"
                    mp?.sendRoundEvent(if (isHostSelected == true) "PLAYER_STATE;0;$body" else "PLAYER;$body")
                }
                delay(50L)
            }
        }

        LaunchedEffect(playerWorldXDp, heightPx, unitPx, hasFoundMammy, mammyVisible) {
            if (!isPaused && !hasFoundMammy && mammyVisible) {
                val playerCenterXPx = xDpToPx(playerWorldXDp) + playerWidthPx / 2f
                val mammyCenterXPx = (mammyGoalCol + 1f) * unitPx
                val mammyBottomPx = mammyGoalBottomRow * unitPx
                val closeX = abs(shortestWorldDx(playerCenterXPx, mammyCenterXPx)) <= unitPx * 3f
                val closeY = abs(heightPx - mammyBottomPx) <= unitPx * 4f
                if (closeX && closeY) {
                    bedRound.rescue()
                }
            }
        }

        GamePresentation {
            // Multiplayer role selection overlay
            if (isHostSelected == null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = tr("Multiplayer: choose role"), color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Button(onClick = { isHostSelected = true }) { Text(tr("Host")) }
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = { isHostSelected = false }) { Text(tr("Join")) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(text = tr("Host address: {0}", "0.0.0.0:${MultiplayerManager.DEFAULT_PORT}"), color = Color.White)
                    Text(text = tr("Host address: {0}", "${MultiplayerManager.DEFAULT_HOST_IP}:${MultiplayerManager.DEFAULT_PORT}"), color = Color.White)
                }
            } else {
                val status = if (mp == null) {
                    tr("Solo")
                } else {
                    val connectionLabel = if (connectionStatus) tr("connected") else tr("connecting...")
                    (if (isHostSelected == true) tr("Host: {0}", connectionLabel) else tr("Client: {0}", connectionLabel)) +
                        " | " + tr("Friends: {0}", peersState.size)
                }
                Text(
                    text = status,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                )
                if (mp != null && !connectionStatus) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 44.dp, end = 12.dp)
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = if (isHostSelected == true) {
                                tr("Waiting for players")
                            } else {
                                tr("Connection lost")
                            },
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = {
                                pushNotice(if (isHostSelected == true) tr("Restarting host...") else tr("Retrying connection..."))
                                mp.stop()
                                mp.start()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.18f),
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
                        ) {
                            Text(if (isHostSelected == true) tr("Restart Host") else tr("Retry"))
                        }
                    }
                }
            }

            Text(
                text = if(worldConfig.unlimited) tr("Unlimited exploration") else (if (cycle.safe) tr("Safe time") + " " else if (cycle.hunting) tr("Nightfall — enemies hunting!") + " " else tr("Find Mammy") + " ") +
                    "${cycle.remainingSeconds / 60}:${(cycle.remainingSeconds % 60).toString().padStart(2, '0')}",
                color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).zIndex(3f)
                    .clickable { isMinimapOpen = false; hintRequest++ }
                    .background(Color.Black.copy(alpha = 0.6f)).padding(8.dp)
            )
            val playerCenterForObjectivePx = xDpToPx(playerWorldXDp) + playerWidthPx / 2f
            val mammyCenterForObjectivePx = (mammyGoalCol + 1f) * unitPx
            val mammyDxTiles = shortestWorldDx(playerCenterForObjectivePx, mammyCenterForObjectivePx) / unitPx
            val mammyDirection = when {
                hasFoundMammy -> "found"
                mammyDxTiles > 1f -> "right"
                mammyDxTiles < -1f -> "left"
                else -> "nearby"
            }
            val mammyDistanceTiles = abs(mammyDxTiles).toInt()
            if ((accessibility.showInfoText || showTemporaryHint) && !isMinimapOpen) Text(
                text = if(vineStand!=null) {
                    tr("Move left/right to step off. Down to climb. Jump to let go.")
                } else if(vineSwing!=null) {
                    tr(if(vineSwing!!.vine.swingable) "Left/right to swing, up/down to climb. Jump to let go." else "Up/down to climb, left/right to switch sides. Jump to let go.")
                } else if (hazardContacts.isNotEmpty()) {
                    tr(hazardContacts.first().kind.title)
                } else if(SwingVines.grab(localRopePoint(),tileMap)!=null) {
                    tr("Press up to climb the vine")
                } else if(worldConfig.unlimited) {
                    tr("Explore freely. Mammy has an optional gift.")
                } else if (hasFoundMammy) {
                    tr("Mammy found — rest before the next search")
                } else if (currentBiome != null && ExplorationWorld.biome(mammyLocation.latitude, mammyLocation.longitude) != currentBiome) {
                    tr("Hit a portal to visit another biome")
                } else if (!mammyVisible) {
                    tr("Turn the world to look for Mammy")
                } else {
                    tr("Find Mammy: {0} tiles {1}", mammyDistanceTiles, tr(mammyDirection))
                },
                color = Color.White,
                modifier = Modifier
                    .zIndex(2f)
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .background(Color(0xFF17324E))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )

            val specialCooldown = specials.cooldowns[bedRound.selfId] ?: 0
            Button(onClick={
                if(vineSwing==null && !isJumping) specials.use(SpecialView(-centerX.value/unit.value,(screenWidth-centerX).value/unit.value,
                    cameraBottomPx/unitPx,(cameraBottomPx/unitPx+playableHeight.value/unit.value).coerceAtMost(tileMap.height.toFloat())))
            },enabled=!controlsBlocked && !isJumping && vineSwing==null && specials.event==null && specialCooldown==0,
                contentPadding=PaddingValues(6.dp),
                modifier=Modifier.align(if (viewportHeight < 460.dp) Alignment.BottomEnd else Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(top = if (viewportHeight < 460.dp) 0.dp else 210.dp,
                        bottom = if (viewportHeight < 460.dp) 12.dp else 0.dp,
                        end = if (viewportHeight < 460.dp) 110.dp else 14.dp)
                    .size(64.dp).zIndex(5f)
                    .semantics {
                        contentDescription=tr(player.special().title)
                        stateDescription=if(specialCooldown>0) tr("Special ready in {0}s",(specialCooldown+999)/1000) else tr(player.special().title)
                    }) {
                SpecialAbilityIcon(player.special(),specialCooldown/30000f,Modifier.fillMaxSize())
            }
            SpecialIntro(specials.event,reduceMotion,Modifier.fillMaxSize().zIndex(30f))
            if (syncingWorld) {
                Text(if (connectionStatus) tr("Waiting for the host’s world…") else tr("Connection lost. Trying to reconnect…\nIf the host has left, open the game menu and choose Leave game."),
                    color = Color.White, modifier = Modifier.zIndex(8f).align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.8f)).padding(16.dp))
            }

            // Play area fills whole screen
            var aimCol by remember { mutableStateOf<Int?>(null) }
            var aimRow by remember { mutableStateOf<Int?>(null) }
            var isPlacePreviewing by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(skyColor)
                    .pointerInput(foundationHeight, unitPx, centerX) {
                        detectTapGestures(onTap = { pos ->
                            if (isAxisTransitioning || controlsBlocked) return@detectTapGestures
                            // Convert screen tap to world grid (consider camera offset)
                            val cameraOffsetXDpNow = playerWorldXDp - centerX
                            val cameraOffsetXPxNow = xDpToPx(cameraOffsetXDpNow)
                            if (pos.y >= size.height - xDpToPx(foundationHeight)) return@detectTapGestures
                            val worldX = pos.x / cameraZoom + cameraOffsetXPxNow
                            val targetY = WorldCamera.worldY(pos.y, size.height - xDpToPx(foundationHeight), cameraBottomPx, cameraZoom)
                            if (targetY < 0f) return@detectTapGestures
                            val selected = inventory.getOrNull(vm.selectedSlot.intValue)
                            if (selected?.type in listOf(ItemType.Arrow,ItemType.ElectricArrow) && selected!!.count > 0) {
                                if (isHostSelected == false) {
                                    if (requestWorldAction("FIRE", listOf((worldX / unitPx * 1000).toInt().toString(), (targetY / unitPx * 1000).toInt().toString(), selected.type!!.name), spendSelected = true)) {
                                        bowFacingRight = worldX >= xDpToPx(playerWorldXDp) + playerWidthPx / 2f
                                        bowSequence++
                                    }
                                } else if (vm.fireSelectedArrow(
                                        targetX = worldX,
                                        targetY = targetY,
                                        unitPx = unitPx,
                                        playerWidthPx = playerWidthPx,
                                        playerHeightPx = playerHeightPx,
                                        worldWidthPx = tileMap.width * unitPx
                                    )
                                ) {
                                    bowFacingRight = worldX >= xDpToPx(playerWorldXDp) + playerWidthPx / 2f
                                    bowSequence++
                                    pushNotice(tr("Arrow fired"))
                                }
                                return@detectTapGestures
                            }
                            val col = floor(worldX / unitPx).toInt()
                            val row = floor(targetY / unitPx).toInt()
                            aimCol = ((col % tileMap.width) + tileMap.width) % tileMap.width
                            aimRow = row
                            // Set facing based on precise angle from player center to tapped cell center
                            run {
                                val playerCenterX = xDpToPx(playerWorldXDp) + playerWidthPx / 2f
                                val playerCenterY = heightPx + playerHeightPx / 2f
                                val cellCenterX = (aimCol!! + 0.5f) * unitPx
                                val cellCenterY = (aimRow!! + 0.5f) * unitPx
                                previewSelectedFacing = vectorToDirection(cellCenterX - playerCenterX, cellCenterY - playerCenterY)
                            }
                        })
                    }
            ) {
                FoundationLayer(foundationRows, unit * WorldViewport.BLOCK_CELLS,
                    playerWorldXDp - centerX, Modifier.matchParentSize(), cameraZoom)
                // Keep the playable world above the immutable visual floor. Its world
                // coordinates and collisions remain identical on phones and tablets.
                GamePresentation(Modifier.matchParentSize().padding(bottom = foundationHeight).clipToBounds()) {
                Box(Modifier.align(Alignment.BottomStart)
                    .wrapContentSize(Alignment.BottomStart, unbounded = true)
                    .requiredSize(screenWidth, unit * (tileMap.height + 12))
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 1f)
                        scaleX = cameraZoom; scaleY = cameraZoom
                        translationY = cameraBottomPx * cameraZoom
                    }) {
                // Camera offset in dp (world x at player minus screen center)
                val cameraOffsetXDp = playerWorldXDp - centerX
                val cameraOffsetXPx = xDpToPx(cameraOffsetXDp)

                // Observe mapVersion so UI updates when tiles are destroyed
                val _mv = mapVersion
                // Canvas uses world-sized geometry without Compose's child-size constraints.
                // Clip copies to the viewport before drawing; destroyed voxels remain empty.
                val blocksSnapshot = remember(mapVersion, tileMap.projectileRevision, activeWorldAxis, tileMap.activeSlice) {
                    tileMap.snapshotRenderableBlocks().filterNot { it.managed }
                }
                val nearbyTerrain = remember(tileMap, mapVersion, tileMap.projectileRevision, activeWorldAxis,
                    tileMap.activeSlice, worldConfig.nearbyTerrainRows) {
                    val view = tileMap.collisionView()
                    buildList {
                        for (distance in worldConfig.nearbyTerrainRows.coerceIn(0, 2) downTo 1) {
                            for (direction in listOf(-1, 1)) {
                                val fixed = tileMap.activeSlice + distance * direction
                                view.setActiveSlice(activeWorldAxis, fixed, fixed)
                                add((if (distance == 1) 0.12f else 0.05f) to view.snapshotRenderableBlocks().filterNot { it.managed })
                            }
                        }
                    }
                }
                Canvas(Modifier.matchParentSize()) {
                    val hinge = xDpToPx(centerX) + playerWidthPx / 2f
                    if (isAxisTransitioning) drawPaperSky(paperFrame, turnFromAxis, turnDirection, hinge, cycle.darkness)
                    if (!worldConfig.exploration) drawDistantMountains(cameraOffsetXPx, unitPx, activeWorldAxis, cycle.darkness)
                    else {
                        val row = floor(heightPx / unitPx).toInt()
                        val col = floor(xDpToPx(playerWorldXDp) / unitPx).toInt()
                        val underground = (row + 3 until tileMap.height).any { tileMap.get(col, it) }
                        if (underground) drawRect(Color(0xFF14212C))
                        if (activeWorldAxis == WorldAxis.Latitude) {
                            for (boundary in 0..3) {
                                val left = (boundary * ExplorationWorld.REGION - ExplorationWorld.BORDER) * unitPx - cameraOffsetXPx
                                drawRect(Color(0xFF263D47), Offset(left, 0f), Size(ExplorationWorld.BORDER * 2 * unitPx, size.height))
                            }
                        }
                    }
                    if (!isAxisTransitioning) drawWorldScenery(tileMap, localRopePoint(), activeWorldAxis, unitPx, xDpToPx(centerX))
                    val worldWidthPx = tileMap.width * unitPx
                    // Ghost terrain stays behind the active slice and never enters its collision map.
                    if (!isAxisTransitioning) nearbyTerrain.forEach { (opacity, blocks) ->
                        for (b in blocks) {
                            val baseLeft = b.col * unitPx - cameraOffsetXPx
                            val width = b.w * unitPx
                            val firstCopy = ceil((-width - baseLeft) / worldWidthPx).toInt()
                            val lastCopy = floor((size.width - baseLeft) / worldWidthPx).toInt()
                            val top = max(0f, size.height - (b.row + b.h) * unitPx)
                            val bottom = min(size.height, size.height - b.row * unitPx)
                            if (bottom <= top) continue
                            for (copy in firstCopy..lastCopy) {
                                val left = max(0f, baseLeft + copy * worldWidthPx)
                                val right = min(size.width, baseLeft + copy * worldWidthPx + width)
                                if (right > left) drawRect(b.color.copy(alpha = opacity),
                                    Offset(left, top), Size(right - left, bottom - top))
                            }
                        }
                    }
                    for (b in blocksSnapshot) {
                        val ratio = b.health.toFloat() / b.maxHealth.coerceAtLeast(1)
                        val color = b.color.copy(alpha = (0.45f + ratio * 0.55f))
                        val baseLeft = b.col * unitPx - cameraOffsetXPx
                        val width = b.w * unitPx
                        val firstCopy = ceil((-width - baseLeft) / worldWidthPx).toInt()
                        val lastCopy = floor((size.width - baseLeft) / worldWidthPx).toInt()
                        for (copy in firstCopy..lastCopy) {
                            val left = max(0f, baseLeft + copy * worldWidthPx)
                            val right = min(size.width, baseLeft + copy * worldWidthPx + width)
                            val top = max(0f, size.height - (b.row + b.h) * unitPx)
                            val bottom = min(size.height, size.height - b.row * unitPx)
                            if (right > left && bottom > top) {
                                if (!isAxisTransitioning) {
                                    drawRect(color, Offset(left, top), Size(right - left, bottom - top))
                                } else {
                                    // Split merged runs so the player's supporting column has its
                                    // own hinge. Never change voxel coordinates for this effect.
                                    val columnLeft = hinge - playerWidthPx / 2f
                                    val columnRight = hinge + playerWidthPx / 2f
                                    val skew = if (paperFrame.opening) -turnDirection else turnDirection
                                    if (left < columnLeft) paperRect(color, left, top, min(right, columnLeft), bottom,
                                        columnLeft, paperFrame.widthScale, paperFrame.fold, skew)
                                    if (right > columnRight) paperRect(color, max(left, columnRight), top, right, bottom,
                                        columnRight, paperFrame.widthScale, paperFrame.fold, skew)
                                    if (right > columnLeft && left < columnRight) {
                                        paperRect(color, max(left, columnLeft), top, min(right, columnRight), bottom,
                                            hinge, paperFrame.columnScale, 0f, skew)
                                        paperRect(Color.White.copy(alpha = paperFrame.fold * 0.22f),
                                            max(left, columnLeft), top, min(right, hinge), bottom,
                                            hinge, paperFrame.columnScale, 0f, skew)
                                    }
                                }
                            }
                        }
                    }
                }

                HuntBedLayer(bedRound.bed, activeWorldAxis, tileMap.activeSlice,
                    tileMap.width, unit, centerX, playerWorldXDp, screenWidth)

                // Long-press Place preview ghost: show all touching 1x1 options; select via joystick and show ghost if valid
                if (isPlacePreviewing && vm.hasSelectedBlock()) {
                    val xPx = xDpToPx(playerWorldXDp)
                    val colCenter = floor((xPx + playerWidthPx / 2f) / unitPx).toInt()
                    val leftCol = floor(xPx / unitPx).toInt()
                    val rightCol = floor((xPx + playerWidthPx - 0.001f) / unitPx).toInt()
                    val bottomRow = floor((heightPx - 0.001f) / unitPx).toInt()
                    val topRow = floor(((heightPx + playerHeightPx - 0.001f) / unitPx)).toInt()
                    val playerRows = playerOverlappingRows(heightPx)
                    val playerCols = playerOverlappingColumns(xPx)
                    var ghostCol = 0
                    var ghostRow = 0
                    var showGhost = false

                    fun canPlaceAtPreview(anchorCol: Int, anchorRow: Int): Boolean {
                        if (!tileMap.inY(anchorRow) || !tileMap.inY(anchorRow + 2)) return false
                        // disallow overlap with player
                        val placeCols = anchorCol..(anchorCol + 2)
                        val placeRows = anchorRow..(anchorRow + 2)
                        val overlapCols = placeCols.any { it in playerCols }
                        val overlapRows = placeRows.any { it in playerRows }
                        if (overlapCols && overlapRows) return false
                        // disallow overlap with peers
                        if (connectionStatus) {
                            peersState.values.forEach { peer ->
                        if (!peerOnPlayerPlane(peer)) return@forEach
                                val pCol0 = floor(peer.worldXPx / unitPx).toInt()
                                val pCol1 = floor((peer.worldXPx + playerWidthPx - 0.001f) / unitPx).toInt()
                                val pRow0 = floor((peer.heightPx - 0.001f) / unitPx).toInt()
                                val pRow1 = floor(((peer.heightPx + playerHeightPx - 0.001f) / unitPx)).toInt()
                                val colsOverlap = placeCols.any { c -> ((c % tileMap.width) + tileMap.width) % tileMap.width in pCol0..pCol1 }
                                val rowsOverlap = placeRows.any { r -> r in pRow0..pRow1 }
                                if (colsOverlap && rowsOverlap) return false
                            }
                        }
                        // disallow overlap with skeletons
                        skeletons.forEach { s ->
                            if (!isOnPlayerPlane(s.otherAxisPx)) return@forEach
                            val sCol0 = floor(s.worldXPx / unitPx).toInt()
                            val sCol1 = floor((s.worldXPx + playerWidthPx - 0.001f) / unitPx).toInt()
                            val sRow0 = floor((s.bottomPx - 0.001f) / unitPx).toInt()
                            val sRow1 = floor(((s.bottomPx + playerHeightPx - 0.001f) / unitPx)).toInt()
                            val colsOverlap = placeCols.any { c -> ((c % tileMap.width) + tileMap.width) % tileMap.width in sCol0..sCol1 }
                            val rowsOverlap = placeRows.any { r -> r in sRow0..sRow1 }
                            if (colsOverlap && rowsOverlap) return false
                        }
                        // region emptiness
                        for (dx in 0..2) {
                            val cx = ((anchorCol + dx) % tileMap.width + tileMap.width) % tileMap.width
                            for (dy in 0..2) {
                                val ry = anchorRow + dy
                                if (!tileMap.inY(ry) || tileMap.get(cx, ry)) return false
                            }
                        }
                        return true
                    }

                    val candidates = mutableListOf<Cell>()
                    // Left strip
                    for (r in playerRows) { candidates += Cell(leftCol - 1, r) }
                    // Right strip
                    for (r in playerRows) { candidates += Cell(rightCol + 1, r) }
                    // Up strip (use 3 cells centered on player center)
                    for (c in (colCenter - 1)..(colCenter + 1)) { candidates += Cell(c, topRow + 1) }
                    // Down strip (use 3 cells centered on player center)
                    for (c in (colCenter - 1)..(colCenter + 1)) { candidates += Cell(c, bottomRow - 1) }

                    // Choose a selected cell using joystick direction (fallback to last facing)
                    val playerCenterX = xPx + playerWidthPx / 2f
                    val playerCenterY = heightPx + playerHeightPx / 2f
                    val joyMag = sqrt(joystickX*joystickX + joystickY*joystickY)
                    var best: Cell? = null
                    var bestScore = Float.NEGATIVE_INFINITY
                    val (dirX, dirY) = if (joyMag > 0.15f) {
                        // Global: up on joystick should select/aim upper block (no inversion)
                        joystickX to joystickY
                    } else {
                        directionToVector(lastDirection)
                    }
                    candidates.forEach { cell ->
                        if (!tileMap.inY(cell.r)) return@forEach
                        val cx = (cell.c + 0.5f) * unitPx
                        val cy = (cell.r + 0.5f) * unitPx
                        var vx = cx - playerCenterX
                        var vy = cy - playerCenterY
                        val vm = sqrt(vx*vx + vy*vy)
                        if (vm > 0f) { vx /= vm; vy /= vm }
                        val score = vx*dirX + vy*dirY
                        if (score > bestScore) { bestScore = score; best = cell }
                    }
                    val selected = best ?: candidates.first()
                    val selCol = ((selected.c % tileMap.width) + tileMap.width) % tileMap.width
                    val selRow = selected.r
                    previewSelectedAimCol = selCol
                    previewSelectedAimRow = selRow
                    // Infer facing from precise angle to the selected cell center (10-sector mapping)
                    run {
                        val cx = (selCol + 0.5f) * unitPx
                        val cy = (selRow + 0.5f) * unitPx
                        previewSelectedFacing = vectorToDirection(cx - playerCenterX, cy - playerCenterY)
                    }

                    // Highlight selected cell thicker
                    run {
                        val aimLeftPx = ((selCol * unitPx) - cameraOffsetXPx)
                        val aimBottomDp = pxToDp(selRow * unitPx)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .offset(x = pxToDp(aimLeftPx), y = -aimBottomDp)
                                .size(unit)
                                .border(width = 2.dp, color = Color.Red)
                        )
                    }

                    // Compute ghost placement from the selected aim cell using the same rules as actual placement
                    fun anchorFromAimCellPreview(c: Int, r: Int): Pair<Int, Int>? {
                        return when {
                            c < leftCol -> Pair(c - 2, r)
                            c > rightCol -> Pair(c, r)
                            r > topRow -> Pair(c - 1, r)
                            r < floor((heightPx) / unitPx).toInt() -> Pair(c - 1, r - 2)
                            else -> null
                        }
                    }
                    val anchor = anchorFromAimCellPreview(selCol, selRow)
                    if (anchor != null) {
                        val ac = anchor.first
                        val ar = anchor.second
                        if (canPlaceAtPreview(ac, ar)) {
                            ghostCol = ac
                            ghostRow = ar
                            showGhost = true
                        }
                    }

                    if (showGhost) {
                        val ghostLeftPx = (ghostCol * unitPx) - cameraOffsetXPx
                        val ghostBottomDp = pxToDp(ghostRow * unitPx)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .offset(x = pxToDp(ghostLeftPx), y = -ghostBottomDp)
                                .size(width = unit * 3, height = unit * 3)
                                .background((inventory.getOrNull(vm.selectedSlot.intValue)?.material?.color ?: Color.White).copy(alpha = 0.45f))
                                .border(width = 2.dp, color = Color.White)
                        )
                    }
                }

                if (worldConfig.exploration) SwingVineLayer(tileMap,localRopePoint(),activeWorldAxis,unit,centerX,listOfNotNull(vineSwing)+peerSwings.filterKeys { it in peersState }.values)
                if (worldConfig.exploration) BiomeHazardLayer(tileMap,localRopePoint(),activeWorldAxis,unit,centerX,cycle.elapsedMs,reduceMotion,vinesOnly=true)

                // Arriving players remain visible in front of their paired portal.
                if (worldConfig.exploration) PortalLayer(tileMap, activeWorldAxis, playerWorldXDp,
                    unit, centerX, currentBiome!!)

                // Both local and remote characters render in front of climbing and swinging vines.
                // Player sprite (always centered)
                val heightDp = pxToDp(heightPx)
                Box(modifier = Modifier.align(Alignment.BottomStart)
                    .offset(x = centerX, y = -heightDp)
                    .size(width = playerWidth, height = playerHeight)) {
                val grippingVine = !isParalyzed && vineSwing != null && specials.event?.let { !it.intro && it.actor==bedRound.selfId } != true
                if (grippingVine) VineCharacterArt(player,outfit,playerWidth,playerHeight,lastDirection.isFacingRight(),vineSwing?.angle ?: 0f,tr("Player"))
                else if (consuming && !isParalyzed) ConsumingCharacter(player, outfit, lastDirection.isFacingRight(), reduceMotion)
                else if (drawingBow && !isParalyzed) ActionCharacter(player, outfit, bowFacingRight, consuming = false, reduceMotion = reduceMotion)
                else Image(
                    painter = if(isParalyzed) shockedPlayerPainter(player,outfit) else if(combatMotion.active(mp?.getSelfId() ?: 0)) attackPlayerPainter(player,outfit) else characterPainter(player, outfit, if (showWalkingPose) CharacterPose.Walk else CharacterPose.Side),
                    contentDescription = tr("Player"),
                    alignment = Alignment.BottomCenter,
                    contentScale = androidx.compose.ui.layout.ContentScale.FillHeight,
                    modifier = Modifier
                        .fillMaxSize()
                        .requiredWidth(if(isParalyzed || combatMotion.active(mp?.getSelfId() ?: 0)) playerHeight*1.1f else playerWidth)
                        .graphicsLayer {
                            alpha = if(specials.event?.let { !it.intro && it.actor==bedRound.selfId } == true) 0f else if(isParalyzed) 1f else 1f - paperFrame.frontPose
                            scaleX = playerSpriteScaleX(player, lastDirection.isFacingRight())
                            rotationY = turnDirection * paperFrame.fold * 65f
                            cameraDistance = 12f * density.density
                        },
                    colorFilter = if(isParalyzed) shockTint(shocks.clock,reduceMotion) else if (!reduceMotion && currentTimeMillis() <= playerFlashUntil) ColorFilter.tint(Color.Red) else null
                )
                if (isAxisTransitioning && !isParalyzed && !grippingVine) Image(
                    painter = characterPainter(player, outfit, CharacterPose.Front),
                    contentDescription = null,
                    alignment = Alignment.BottomCenter,
                    contentScale = androidx.compose.ui.layout.ContentScale.FillHeight,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        alpha = paperFrame.frontPose
                        rotationY = turnDirection * (paperProgress.value - 0.5f) * 60f
                        scaleX = 1f - paperFrame.fold * 0.12f
                        cameraDistance = 12f * density.density
                    }
                )
                if(!grippingVine && specials.event?.let { !it.intro && it.actor==bedRound.selfId } != true) EquippedAppearance(equipment.loadout)
                }

                // Render all remote peers (host and client) with wrap-aware positioning
                if (connectionStatus) {
                    val worldWidthDp = pxToDp(tileMap.width * unitPx)
                    peersState.forEach { (id, peer) ->
                        if (!peerOnPlayerPlane(peer) || specials.event?.let { !it.intro && it.actor==id } == true) return@forEach
                        val baseX = centerX + (peer.worldXDp - playerWorldXDp)
                        val candidates = listOf(baseX, baseX - worldWidthDp, baseX + worldWidthDp)
                        val peerHeightDp = pxToDp(peer.heightPx)
                        val peerPlayer = peer.selected ?: Player.Ian
                        val peerPose = if (peer.walkCycle.showWalkingPose) CharacterPose.Walk else CharacterPose.Side
                        candidates.forEach { candX ->
                            // Only draw if intersects the viewport horizontally
                            if (candX > -playerWidth && candX < screenWidth + playerWidth) {
                                Box(Modifier.align(Alignment.BottomStart).offset(x=candX,y=-peerHeightDp).size(playerWidth,playerHeight)) {
                                    val peerGrip = !shocks.active("P",id.toLong()) && peerSwings[id]!=null
                                    if(peerGrip) VineCharacterArt(peerPlayer,peer.outfit,playerWidth,playerHeight,peer.facingRight,peerSwings[id]?.angle ?: 0f,"Peer $id")
                                    else Image(if(shocks.active("P",id.toLong())) shockedPlayerPainter(peerPlayer,peer.outfit) else if(combatMotion.active(id)) attackPlayerPainter(peerPlayer,peer.outfit) else characterPainter(peerPlayer,peer.outfit,peerPose), "Peer $id",
                                        colorFilter=if(shocks.active("P",id.toLong())) shockTint(shocks.clock,reduceMotion) else null,
                                        modifier=Modifier.fillMaxSize().requiredWidth(if(shocks.active("P",id.toLong()) || combatMotion.active(id)) playerHeight*1.1f else playerWidth).graphicsLayer { scaleX=playerSpriteScaleX(peerPlayer,peer.facingRight) },
                                        contentScale=androidx.compose.ui.layout.ContentScale.FillHeight)
                                    if(!peerGrip) EquippedAppearance(peerEquipment[id] ?: EquipmentLoadout())
                                }
                            }
                        }
                    }
                }

                AlienLayer(alien,activeWorldAxis,tileMap.activeSlice,tileMap.width,unit,centerX,localRopePoint(),reduceMotion,shocks)
                val ropeTargets = buildMap<RopeTarget,RopePoint> {
                    alien.targetPoint?.let { put(RopeTarget("A",0),it) }
                    companion.players().forEach { (id,p) -> if(id!=bedRound.selfId) put(RopeTarget("P",id.toLong()),p) }
                    vm.worldEntities(activeWorldAxis,unitPx).filter { it.kind in listOf("Z","S") && it.hp>0 }.forEach {
                        put(RopeTarget(it.kind,it.id),RopePoint(it.latitude/1000f,it.longitude/1000f,it.height/1000f))
                    }
                }
                CompanionLayer(companion,ropeTargets,localRopePoint(),activeWorldAxis,tileMap.width,unit,centerX,
                    equipment.loadout.hand==ItemType.Rope,controlsBlocked)

                if (worldConfig.exploration) BiomeHazardLayer(tileMap, localRopePoint(), activeWorldAxis, unit, centerX, cycle.elapsedMs, reduceMotion)
                DogLayer(dog, tileMap, localRopePoint(), activeWorldAxis, unit, centerX, isPaused, reduceMotion)
                SpecialAttackLayer(specials.event,tileMap,localRopePoint(),activeWorldAxis,unit,centerX,reduceMotion)
                if (worldConfig.exploration) WorkbenchLayer(tileMap,localRopePoint(),activeWorldAxis,unit,centerX)
                EnemyLayer(vm, tileMap, worldConfig, playerWorldXDp, playerOtherAxisDp,
                    centerX, screenWidth, unit, playerWidth, playerHeight, isPaused, reduceMotion, shocks)

                // Render Potions if present
                if (potions.isNotEmpty()) {
                    val worldWidthDp = pxToDp(tileMap.width * unitPx)
                    potions.forEach { p ->
                        if (tileMap.wrap(p.otherCol) != tileMap.activeSlice) return@forEach
                        val pXDp = pxToDp(p.col * unitPx)
                        val baseX = centerX + (pXDp - playerWorldXDp)
                        val candidates = listOf(baseX, baseX - worldWidthDp, baseX + worldWidthDp)
                        val pHeightDp = pxToDp(p.row * unitPx)
                        candidates.forEach { candX ->
                            if (candX > -unit && candX < screenWidth + unit) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .offset(x = candX, y = -pHeightDp)
                                        .size(unit)
                                        .background(Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    GameIcon(when(p.type) { ItemType.ElectricArrow -> GameIconKind.ElectricArrow; ItemType.Arrow -> GameIconKind.Arrow; else -> GameIconKind.Potion }, InventorySlot(p.type, 1).displayName, Modifier.size(unit), tint = if(p.type==ItemType.ElectricArrow) Color.Yellow else Color.White)
                                }
                            }
                        }
                    }
                }

                // Render story objective
                val mammyWorldWidthDp = pxToDp(tileMap.width * unitPx)
                val mammyXDp = pxToDp(mammyGoalCol * unitPx)
                val mammyBaseX = centerX + (mammyXDp - playerWorldXDp)
                val mammyCandidates = listOf(
                    mammyBaseX,
                    mammyBaseX - mammyWorldWidthDp,
                    mammyBaseX + mammyWorldWidthDp
                )
                val mammyBottomDp = pxToDp(mammyGoalBottomRow * unitPx)
                mammyCandidates.forEach { candX ->
                    if (mammyVisible && bedRound.celebrationRemainingMs == 0L && candX > -playerWidth * 3f && candX < screenWidth + playerWidth * 3f) {
                        Image(
                            painter = mammyPainter(),
                            contentDescription = tr("Mammy"),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .offset(x = candX, y = -mammyBottomDp)
                                .size(width = playerWidth, height = playerHeight),
                            colorFilter = if (hasFoundMammy) {
                                ColorFilter.tint(Color.White.copy(alpha = 0.35f), BlendMode.SrcAtop)
                            } else {
                                null
                            }
                        )
                    }
                }

                bedRound.celebrationLocation?.let { location ->
                    if (location.intersects(activeWorldAxis, tileMap.activeSlice, tileMap.width)) {
                        val dx = shortestWorldDx(xDpToPx(playerWorldXDp), location.along(activeWorldAxis) * unitPx)
                        MammyCelebration(bedRound.celebrationRemainingMs, reduceMotion,
                            Modifier.align(Alignment.BottomStart)
                                .offset(x = centerX + pxToDp(dx) - playerWidth / 2, y = -pxToDp(location.row * unitPx))
                                .size(playerWidth * 2, playerHeight))
                    }
                }

                bedRound.loot.forEach { item ->
                    val location = WorldLocation(item.latitude, item.longitude, item.row)
                    if (location.intersects(activeWorldAxis, tileMap.activeSlice, tileMap.width)) {
                        val base = centerX + pxToDp(location.along(activeWorldAxis) * unitPx) - playerWorldXDp
                        val span = pxToDp(tileMap.width * unitPx)
                        for (x in listOf(base, base - span, base + span)) {
                            if (x > -unit * 2 && x < screenWidth + unit * 2) Box(
                                modifier = Modifier.align(Alignment.BottomStart).offset(x, -pxToDp(item.row * unitPx)).size(unit)
                                    .semantics { contentDescription = tr("{0} pickup, {1}", InventorySlot(item.type, item.count).displayName, item.count) }
                            ) {
                                GameIcon(when(item.type) { ItemType.Map -> GameIconKind.Map; ItemType.Arrow -> GameIconKind.Arrow; ItemType.ElectricArrow -> GameIconKind.ElectricArrow; else -> GameIconKind.Potion }, null, Modifier.fillMaxSize())
                                if (item.count > 1) Text(item.count.toString(), color = Color.White, fontSize = 7.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black, androidx.compose.ui.geometry.Offset(1f, 1f), 1f)),
                                    modifier = Modifier.align(Alignment.BottomEnd).offset(y = 2.dp))
                            }
                        }
                    }
                }

                } // Zoomed world content.
                } // Raised world viewport; HUD remains anchored to the physical screen.
                // Notices banner (top center)
                LaunchedEffect(Unit) {
                    while (true) {
                        val now = currentTimeMillis()
                        notices.removeAll { now - it.createdAt > 3500 }
                        delay(500)
                    }
                }
                Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (accessibility.showInfoText && !isMinimapOpen) notices.forEach { n ->
                        Box(modifier = Modifier
                            .padding(2.dp)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(n.text, color = Color.White)
                        }
                    }
                }


                Row(Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(top = 12.dp, end = 92.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf(-1, 1).forEach { direction ->
                        val index = WorldCamera.zoomLevels.indexOf(cameraZoom)
                        val next = index + direction
                        Button(onClick = { cameraZoom = WorldCamera.zoomLevels[next] },
                            enabled = next in WorldCamera.zoomLevels.indices,
                            modifier = Modifier.size(48.dp).semantics { contentDescription = tr(if (direction < 0) "Zoom out" else "Zoom in") },
                            contentPadding = PaddingValues(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.5f))) {
                            Text(if (direction < 0) "−" else "+", fontSize = 24.sp)
                        }
                    }
                }


                // HUD: Player hearts at top center
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 42.dp).clearAndSetSemantics {
                            contentDescription = tr("Health: {0} of 5 hearts", playerHp)
                            liveRegion = LiveRegionMode.Polite
                        },
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(5) { i ->
                        val filled = i < playerHp
                        AnimatedHealthHeart(filled, reduceMotion)
                    }
                }

                // Foreground HUD control: this must stay inside the play layer, above the sky/world.
                Button(
                    onClick = {
                        if (isAxisTransitioning || controlsBlocked) return@Button
                        isAxisTransitioning = true
                        audio?.play(SoundEffect.Turn)
                        movementClock.reset()
                        verticalClock.reset()
                        turnFromAxis = activeWorldAxis
                        scope.launch {
                            try {
                            paperProgress.snapTo(0f)
                            if (!reduceMotion) paperProgress.animateTo(0.5f, tween(320, easing = FastOutSlowInEasing))
                        // Store coordinates as (latitude, longitude), then deliberately swap
                        // which coordinate is the movement axis. Updating the map here as well
                        // avoids one composition frame of the old slice after switching.
                        val previousActive = playerWorldXDp
                        val previousOther = playerOtherAxisDp
                        val nextAxis = if (activeWorldAxis == WorldAxis.Latitude) WorldAxis.Longitude else WorldAxis.Latitude
                        playerWorldXDp = previousOther
                        playerOtherAxisDp = previousActive
                        activeWorldAxis = nextAxis
                        val activeCell = floor(xDpToPx(previousOther) / unitPx).toInt()
                        val fixedCell = floor(xDpToPx(previousActive) / unitPx).toInt()
                        if (nextAxis == WorldAxis.Latitude) tileMap.setActiveSlice(nextAxis, activeCell, fixedCell)
                        else tileMap.setActiveSlice(nextAxis, fixedCell, activeCell)
                        vm.swapNpcAxes(::pxToDp)
                        mapVersion++
                        pushNotice(tr("World turned"))
                            if (reduceMotion) paperProgress.snapTo(1f) else paperProgress.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
                            } finally {
                                // Reduced-motion turns can finish before another composition.
                                movementClock.reset()
                                verticalClock.reset()
                                isAxisTransitioning = false
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(top = 12.dp, end = 12.dp).size(56.dp)
                        .semantics { contentDescription = tr("Switch latitude longitude") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.42f), contentColor = Color.White),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
                    , contentPadding = PaddingValues(8.dp)
                ) { GameIcon(GameIconKind.Rotate, tr("Turn the world"), Modifier.size(28.dp), tint = Color.White) }

                if (ownsMap && isMinimapOpen) {
                    val span = tileMap.width * unitPx
                    val playerPoint = minimapPosition(xDpToPx(playerWorldXDp), xDpToPx(playerOtherAxisDp), activeWorldAxis, span)
                    val markers = buildList {
                        zombies.forEach { z ->
                            val point = minimapPosition(z.worldXPx, z.otherAxisPx, activeWorldAxis, span)
                            add(MinimapMarker(point.x, point.y, Color.Green))
                        }
                        skeletons.forEach { sk ->
                            val point = minimapPosition(sk.worldXPx, sk.otherAxisPx, activeWorldAxis, span)
                            add(MinimapMarker(point.x, point.y, Color(0xFFFFA040)))
                        }
                        val mammyPoint = minimapPosition(mammyLocation.latitude.toFloat(), mammyLocation.longitude.toFloat(), WorldAxis.Latitude, tileMap.width.toFloat())
                        if (worldConfig.exploration) tileMap.portals.forEach {
                            add(MinimapMarker(it.location.latitude.toFloat() / tileMap.width,
                                it.location.longitude.toFloat() / tileMap.depth, it.destination.portalColor))
                        }
                        if (!cycle.safe) add(MinimapMarker(mammyPoint.x, mammyPoint.y, Color(0xFFFF80C0)))
                        if (connectionStatus) peersState.values.forEach { peer ->
                            val lat = peer.latitude
                            val lon = peer.longitude
                            if (lat != null && lon != null) {
                                val point = minimapPosition(lat.toFloat(), lon.toFloat(), WorldAxis.Latitude, tileMap.width.toFloat())
                                add(MinimapMarker(point.x, point.y, Color(0xFF608CFF)))
                            }
                        }
                    }
                    WorldMinimap(markers, playerPoint, activeWorldAxis,
                        Modifier.align(Alignment.TopEnd).safeDrawingPadding()
                            .padding(top = mapTop, end = 110.dp).zIndex(6f)
                            // Keep the bottom inventory bar clear; the right action rail stays outside the map.
                            .heightIn(max = (hudSafeHeight - mapTop - 92.dp).coerceAtLeast(80.dp)),
                        showPortals = worldConfig.exploration)
                }

                val canCraftHere = !paused && !syncingWorld && !isParalyzed && playerHp > 0 &&
                    !isJumping && specials.event == null && Crafting.nearby(tileMap,localRopePoint()) != null
                if (canCraftHere) Button(onClick={isInventoryOpen=false;isCraftingOpen=true},
                    enabled=!controlsBlocked,modifier=Modifier.align(Alignment.TopStart).padding(top=110.dp,start=14.dp)) {
                    Text(tr("Craft"))
                }
                if(isCraftingOpen) CraftingDialog(vm.bag,craftingPending,canCraftHere,onMake={ id ->
                    if(canCraftHere && !craftingPending && Crafting.plan(vm.bag,id).result==CraftResult.Crafted) {
                        if(isHostSelected==false) craftingPending=requestWorldAction("CRAFT",listOf(id))
                        else finishCraft(id)
                    }
                },onClose={isCraftingOpen=false})

                // Inventory HUD: collapsed shortcut plus expandable slots
                val inventorySlotSize = 48.dp
                val inventorySlotIconSize = 22.dp
                val inventorySlotCountSize = 14.sp
                val visibleInventoryIndexes = listOf(vm.selectedSlot.intValue.coerceIn(0,inventorySlots-1))
                if(isInventoryOpen) InventoryDialog(vm.bag,vm.selectedSlot.intValue,equipment,
                    onSelect={ i -> vm.selectedSlot.intValue=i
                        if(inventory[i].type==ItemType.Rope) equipment.loadout=equipment.loadout.copy(hand=ItemType.Rope)
                    },onClose={isInventoryOpen=false})
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .safeDrawingPadding()
                        // Keep expanded slots and Close above the movement/action controls.
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // The map belongs beside the bag, with 16dp total spacing between touch targets.
                    if (ownsMap) Button(
                        onClick = { isMinimapOpen = !isMinimapOpen },
                        modifier = Modifier.padding(end = 11.dp).size(48.dp)
                            .semantics {
                                contentDescription = if (isMinimapOpen) tr("Hide minimap") else tr("Show minimap")
                                selected = isMinimapOpen
                            },
                        contentPadding = PaddingValues(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isMinimapOpen) Color(0xFF526C42) else Color.Black.copy(alpha = 0.55f), contentColor = Color.White)
                    ) { GameIcon(GameIconKind.Map, null, Modifier.size(32.dp)) }
                    Button(
                        onClick = { isInventoryOpen = !isInventoryOpen },
                        modifier = Modifier.size(48.dp),
                        contentPadding = PaddingValues(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.35f), contentColor = Color.White),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
                    ) {
                        GameIcon(GameIconKind.Bag,if (isInventoryOpen) tr("Close") else tr("Bag"),Modifier.size(28.dp))
                    }
                    visibleInventoryIndexes.forEach { i ->
                        val slot = inventory[i]
                        val isSelected = i == vm.selectedSlot.intValue
                        val borderColor = if (isSelected) Color.Yellow else Color.White.copy(alpha = 0.6f)
                        Box(
                            modifier = Modifier
                                .size(inventorySlotSize)
                                .border(2.dp, borderColor)
                                .background(Color.Black.copy(alpha = 0.25f))
                                .semantics {
                                    contentDescription = "${slot.displayName}, ${slot.count}"
                                    role = Role.Button
                                    selected = isSelected
                                    onClick(tr("Select {0}", slot.displayName)) {
                                        vm.selectedSlot.intValue = i
                                        isInventoryOpen = true
                                        true
                                    }
                                    if (slot.type == ItemType.Block && slot.count > 0) onLongClick(tr("Choose block shape")) {
                                        vm.selectedSlot.intValue = i
                                        isShapePickerOpen = true
                                        true
                                    }
                                }
                                .pointerInput(i, slot.type, slot.material, isInventoryOpen) {
                                    detectTapGestures(
                                        onTap = {
                                            vm.selectedSlot.intValue = i
                                            if (!isInventoryOpen) isInventoryOpen = true
                                        },
                                        onLongPress = {
                                            if (slot.type == ItemType.Block && slot.count > 0) {
                                                vm.selectedSlot.intValue = i
                                                isShapePickerOpen = !isShapePickerOpen
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (slot.type != null && slot.count > 0) {
                                if (slot.type == ItemType.Block) {
                                    BlockMaterialIcon(slot.material ?: BlockMaterial.Grass,Modifier.size(inventorySlotIconSize))
                                } else if(slot.type.isEquipment) EquipmentIcon(slot.type,Modifier.size(inventorySlotIconSize))
                                else GameIcon(when(slot.type) { ItemType.Arrow -> GameIconKind.Arrow; ItemType.ElectricArrow -> GameIconKind.ElectricArrow; ItemType.Map -> GameIconKind.Map; else -> GameIconKind.Potion }, null, Modifier.size(inventorySlotIconSize), tint = if(slot.type==ItemType.ElectricArrow) Color.Yellow else Color.White)
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(1.dp)
                                ) {
                                    Text(
                                        text = slot.count.toString(),
                                        color = Color.White,
                                        fontSize = inventorySlotCountSize,
                                        modifier = Modifier.background(Color(0xFF10243A)).padding(horizontal = 2.dp).clearAndSetSemantics {}
                                    )
                                }
                            }
                        }
                    }
                    if (isShapePickerOpen) {
                        Column(
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.7f)).padding(3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(tr("Shape"), color = Color.White, fontSize = 9.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                BlockShape.entries.forEach { shape ->
                                    Button(
                                        onClick = { selectedShape = shape; isShapePickerOpen = false },
                                        modifier = Modifier.size(44.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = if (shape == selectedShape) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.25f))
                                    ) {
                                        BlockShapeIcon(shape)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // One clock drives spawn waves, safety and nightfall without stale captured state.
            LaunchedEffect(tileMap, isHostSelected) {
                var last = nanoTime()
                while (true) {
                    delay(100L)
                    val now = nanoTime()
                    val delta = (now - last) / 1_000_000L
                    last = now
                    if (isPaused || isHostSelected == null) continue
                    val previous = cycle
                    if (isHostSelected != false) {
                        val forbidden = buildList {
                            bedRound.position(bedRound.selfId)?.let { add(it) }
                            peersState.keys.forEach { bedRound.position(it)?.let { location -> add(location) } }
                            zombies.forEach { z -> add(WorldLocation(
                                floor((if (activeWorldAxis == WorldAxis.Latitude) z.worldXPx else z.otherAxisPx) / unitPx).toInt(),
                                floor((if (activeWorldAxis == WorldAxis.Longitude) z.worldXPx else z.otherAxisPx) / unitPx).toInt(), 0)) }
                            skeletons.forEach { sk -> add(WorldLocation(
                                floor((if (activeWorldAxis == WorldAxis.Latitude) sk.worldXPx else sk.otherAxisPx) / unitPx).toInt(),
                                floor((if (activeWorldAxis == WorldAxis.Longitude) sk.worldXPx else sk.otherAxisPx) / unitPx).toInt(), 0)) }
                        }
                        val newSearch = bedRound.advance(delta, forbidden)
                        vm.hunting = cycle.hunting
                        if (newSearch) {
                            repeat(maxZombies) { spawnZombieRandom() }
                            repeat(maxSkeletons) { spawnSkeletonRandom() }
                            pushNotice(tr("Find Mammy before nightfall!"))
                        }
                        repeat(cycle.wavesSince(previous)) {
                            if(!worldConfig.unlimited || zombies.size < 16) spawnZombieRandom()
                            if(!worldConfig.unlimited || skeletons.size < 12) spawnSkeletonRandom()
                        }
                    }
                }
            }

            // Zombie AI loop moved into GameViewModel (per-zombie jobs)

            // Smoothly interpolate remote players' movement to avoid jumpy motion
            LaunchedEffect(connectionStatus, unitPx) {
                var lastFrameNs = nanoTime()
                while (connectionStatus) {
                    val nowNs = nanoTime()
                    val elapsedMs = (nowNs - lastFrameNs) / 1_000_000L
                    lastFrameNs = nowNs
                    val worldWidthPxF = tileMap.width * unitPx
                    fun adjustTarget(current: Float, target: Float): Float {
                        var t = target
                        val d = t - current
                        if (abs(d) > worldWidthPxF / 2f) {
                            t += if (d > 0f) -worldWidthPxF else worldWidthPxF
                        }
                        return t
                    }
                    val alpha = 0.25f
                    peersState.values.forEach { peer ->
                        val coordinate = if (activeWorldAxis == WorldAxis.Latitude) peer.latitudeMilli else peer.longitudeMilli
                        peer.targetXPx = coordinate?.let { it / 1000f * unitPx } ?: peer.targetXPx
                        if (peer.projectedAxis != activeWorldAxis) {
                            peer.worldXPx = peer.targetXPx
                            peer.projectedAxis = activeWorldAxis
                        }
                        val adjTargetX = adjustTarget(peer.worldXPx, peer.targetXPx)
                        val adjTargetY = peer.targetHeightPx
                        val moveX = (adjTargetX - peer.worldXPx) * alpha
                        val moveY = (adjTargetY - peer.heightPx) * alpha
                        peer.walkCycle = peer.walkCycle.advance(elapsedMs, abs(moveX) > 0.01f && abs(moveY) < 0.01f)
                        peer.worldXPx += moveX
                        peer.heightPx += moveY
                        val worldWidthPx = tileMap.width * unitPx
                        peer.worldXPx = ((peer.worldXPx % worldWidthPx) + worldWidthPx) % worldWidthPx
                        peer.worldXDp = pxToDp(peer.worldXPx)
                    }
                    delay(16L)
                }
            }
            // Peer positions are interpolated each frame. Observe the resulting support,
            // so moving away or disconnecting also starts a fall for a stationary rider.
            val peerSupportTop = supportingPeerTop(xDpToPx(playerWorldXDp), heightPx)

            // Start falling if leaving support (walk off edges)
            LaunchedEffect(playerWorldXDp, isJumping, activeWorldAxis, isAxisTransitioning, zombies, skeletons, mapVersion, peerSupportTop, vineStand, vineSwing != null) {
                if (!isJumping && !isAxisTransitioning && vineStand == null && vineSwing == null) {
                    val xPx = xDpToPx(playerWorldXDp)
                    val cols = playerOverlappingColumns(xPx)
                    val supportedTopPxCandidates = mutableListOf<Float>()
                    peerSupportTop?.let { supportedTopPxCandidates += it }
                    for (c in cols) {
                        // Support exists if there is a solid tile directly under the player's bottom
                        val rowUnder = floor((heightPx - 0.001f) / unitPx).toInt()
                        if (rowUnder >= 0 && tileMap.get(c, rowUnder)) {
                            supportedTopPxCandidates += (rowUnder + 1) * unitPx
                        } else if (heightPx == 0f) {
                            supportedTopPxCandidates += 0f
                        }
                    }
                    if (mammyOverlapsColumns(cols)) {
                        supportedTopPxCandidates += (mammyGoalBottomRow + 3) * unitPx
                    }
                    // Also treat zombies' tops as valid support
                    if (zombies.isNotEmpty()) {
                        zombies.forEach { z ->
                            if (!isOnPlayerPlane(z.otherAxisPx)) return@forEach
                            val zCols = run {
                                val c0 = floor(z.worldXPx / unitPx).toInt()
                                val c1 = floor((z.worldXPx + playerWidthPx - 0.001f) / unitPx).toInt()
                                c0..c1
                            }
                            val horizOverlap = cols.any { it in zCols }
                            if (horizOverlap) {
                                val zTop = z.bottomPx + playerHeightPx
                                supportedTopPxCandidates += zTop
                            }
                        }
                    }
                    // Also treat skeletons' tops as valid support
                    if (skeletons.isNotEmpty()) {
                        skeletons.forEach { s ->
                            if (!isOnPlayerPlane(s.otherAxisPx)) return@forEach
                            val sCols = run {
                                val c0 = floor(s.worldXPx / unitPx).toInt()
                                val c1 = floor((s.worldXPx + playerWidthPx - 0.001f) / unitPx).toInt()
                                c0..c1
                            }
                            val horizOverlap = cols.any { it in sCols }
                            if (horizOverlap) {
                                val sTop = s.bottomPx + playerHeightPx
                                supportedTopPxCandidates += sTop
                            }
                        }
                    }
                    val eps = 0.5f
                    val supported = supportedTopPxCandidates.any { abs(it - heightPx) < eps }
                    if (!supported) {
                        isJumping = true
                        scope.launch {
                            var v = 0f
                            val g = -3000f
                            val frameMs = 16L
                            val dt = PlayerPhysicsClock.STEP_SECONDS
                            verticalClock.reset(nanoTime())
                            physics@ while (true) {
                                if (isAxisTransitioning || isPaused) {
                                    verticalClock.reset()
                                    delay(frameMs)
                                    continue
                                }
                                for (step in 0 until verticalClock.advance(nanoTime())) {
                                    val prevBottom = heightPx
                                    v += g * dt
                                    heightPx += v * dt

                                    val landingCols = playerOverlappingColumns(xDpToPx(playerWorldXDp))
                                    var landingTopPx: Float? = null
                                    val startRow = floor((prevBottom - 0.001f) / unitPx).toInt()
                                    val endRow = floor((heightPx) / unitPx).toInt()
                                    for (row in startRow downTo max(endRow, 0)) {
                                        var hit = false
                                        for (c in landingCols) { if (tileMap.get(c, row)) { hit = true; break } }
                                        if (hit) {
                                            val topPx = (row + 1) * unitPx
                                            if (prevBottom >= topPx && heightPx <= topPx) {
                                                landingTopPx =
                                                    max(landingTopPx ?: Float.NEGATIVE_INFINITY, topPx)
                                            }
                                        }
                                    }
                                    if (!cycle.safe) mammyLocation.landingTop(prevBottom / unitPx, heightPx / unitPx,
                                        landingCols, activeWorldAxis, tileMap.activeSlice, tileMap.width)?.let { top ->
                                        landingTopPx = max(landingTopPx ?: Float.NEGATIVE_INFINITY, top * unitPx)
                                    }
                                    // Consider landing on peers' tops as support
                                    if (connectionStatus) {
                                        peersState.values.forEach { peer ->
                            if (!peerOnPlayerPlane(peer)) return@forEach
                                            val px = peer.worldXPx
                                            val pCols = run {
                                                val c0 = floor(px / unitPx).toInt()
                                                val c1 = floor((px + playerWidthPx - 0.001f) / unitPx).toInt()
                                                c0..c1
                                            }
                                            val horizOverlap = landingCols.any { it in pCols }
                                            if (horizOverlap) {
                                                val peerTop = peer.heightPx + playerHeightPx
                                                if (prevBottom >= peerTop && heightPx <= peerTop) {
                                                    landingTopPx = max(landingTopPx ?: Float.NEGATIVE_INFINITY, peerTop)
                                                }
                                            }
                                        }
                                    }
                                    if (landingTopPx != null) {
                                        heightPx = landingTopPx!!
                                        isJumping = false
                                        break@physics
                                    }
                                    if (heightPx <= 0f) {
                                        heightPx = 0f
                                        isJumping = false
                                        break@physics
                                    }
                                    // Bottom-overlap safety in passive fall: if feet ended up inside a tile, snap out and stop falling
                                    var stopFall = false
                                    run {
                                        val bottomRowNow = floor((heightPx - 0.001f) / unitPx).toInt()
                                        if (bottomRowNow >= 0) {
                                            var overlap = false
                                            for (c in landingCols) { if (tileMap.get(c, bottomRowNow)) { overlap = true; break } }
                                            if (overlap) {
                                                val topPx = (bottomRowNow + 1) * unitPx
                                                heightPx = topPx
                                                isJumping = false
                                                stopFall = true
                                            }
                                        }
                                    }
                                    if (stopFall) break@physics
                                }
                                delay(frameMs)
                            }
                        }
                    }
                }
            }

            // Controls overlay
            // Bottom-left: circular joystick
            val joystickSize = 160.dp
            val thumbSize = 48.dp
            val trackPadding = 12.dp
            var knobOffsetPx by remember { mutableStateOf(Offset.Zero) } // offset from center

            // Continuous movement loop driven by joystick horizontal value
            LaunchedEffect(Unit) {
                val frameMs = 16L
                val dt = PlayerPhysicsClock.STEP_SECONDS
                val maxSpeedPxPerSec = with(density) { 420.dp.toPx() } // tune max speed
                var walkCycle = WalkCycle()
                var lastFrameNs = nanoTime()
                while (true) {
                    val nowNs = nanoTime()
                    val elapsedMs = (nowNs - lastFrameNs) / 1_000_000L
                    lastFrameNs = nowNs
                    val previousX = xDpToPx(playerWorldXDp)
                    val v = if (vineSwing != null || isPlacePreviewing || isAxisTransitioning || controlsBlocked) 0f else joystickX
                    val steps = if (controlsBlocked || isAxisTransitioning || isPlacePreviewing) {
                        movementClock.reset()
                        0
                    } else movementClock.advance(nowNs)
                    val storm = hazardContacts.firstOrNull { it.kind == HazardKind.Tornado }
                    val wind = if (storm == null) 0f else {
                        val midpoint = if (activeWorldAxis == WorldAxis.Latitude) storm.latitude + storm.width / 2f else storm.longitude + storm.depth / 2f
                        if (xDpToPx(playerWorldXDp) / unitPx < midpoint) -6f else 6f
                    }
                    if (vineSwing == null && (abs(v) > 0.01f || wind != 0f)) {
                        repeat(steps) {
                            val deltaPx = (v * maxSpeedPxPerSec * equipment.loadout.speedMultiplier * (if(equipment.blocking) .5f else 1f) * BiomeHazards.speed(hazardContacts) + wind * unitPx) * dt
                            playerWorldXDp = moveHorizontalSubStepped(playerWorldXDp, deltaPx, heightPx)
                        }
                        // Preserve the full aim direction when the stick is released.
                        lastDirection = vectorToDirection(joystickX, joystickY)
                    }
                    val moved = abs(shortestWorldDx(previousX, xDpToPx(playerWorldXDp))) > 0.01f
                    walkCycle = walkCycle.advance(elapsedMs, moved && !isJumping)
                    showWalkingPose = walkCycle.showWalkingPose
                    delay(frameMs)
                }
            }

            var accessibleMoveJob by remember { mutableStateOf<Job?>(null) }
            fun stepAccessibly(direction: Float) {
                if (controlsBlocked || isAxisTransitioning || accessibleMoveJob?.isActive == true) return
                accessibleMoveJob = scope.launch {
                    joystickY = 0f
                    joystickX = direction
                    try { delay(120L) } finally { joystickX = 0f }
                }
            }
            fun aimAccessibly(up: Boolean) {
                if((vineSwing!=null || vineStand!=null || SwingVines.grab(localRopePoint(),tileMap)!=null) && !controlsBlocked && accessibleMoveJob?.isActive!=true) {
                    accessibleMoveJob=scope.launch { joystickY=if(up) 1f else -1f;try { delay(250L) } finally { joystickY=0f } }
                    return
                }
                if (!isPaused) {
                    lastDirection = vectorToDirection(0f, if (up) 1f else -1f)
                    previewSelectedFacing = lastDirection
                }
            }

            val sizePx = with(density) { joystickSize.toPx() }
            val centerPx = sizePx / 2f
            val thumbRadiusPx = with(density) { (thumbSize.toPx() / 2f) }
            val paddingPx = with(density) { trackPadding.toPx() }
            val movementRadiusPx = centerPx - thumbRadiusPx - paddingPx

            if (accessibility.buttonControls) {
                Column(Modifier.align(Alignment.BottomStart).safeDrawingPadding().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AccessibleDirectionButton(tr("Step left"), "←", !isPaused) { stepAccessibly(-1f) }
                        AccessibleDirectionButton(tr("Step right"), "→", !isPaused) { stepAccessibly(1f) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AccessibleDirectionButton(tr(if(vineSwing!=null || vineStand!=null || SwingVines.grab(localRopePoint(),tileMap)!=null) "Climb up" else "Aim up"), "↑", !isPaused) { aimAccessibly(true) }
                        AccessibleDirectionButton(tr(if(vineSwing!=null || vineStand!=null) "Climb down" else "Aim down"), "↓", !isPaused) { aimAccessibly(false) }
                    }
                }
            } else Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .safeDrawingPadding()
                    .padding(16.dp)
                    .size(joystickSize)
                    .semantics {
                        contentDescription = tr("Move and aim")
                        customActions = listOf(
                            CustomAccessibilityAction(tr("Step left")) { stepAccessibly(-1f); true },
                            CustomAccessibilityAction(tr("Step right")) { stepAccessibly(1f); true },
                            CustomAccessibilityAction(tr("Aim up")) { aimAccessibly(true); true },
                            CustomAccessibilityAction(tr("Aim down")) { aimAccessibly(false); true }
                        )
                    }
                    .drawBehind {
                        // Outer circular track (very translucent)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.12f),
                            radius = size.minDimension / 2f,
                            center = Offset(size.width / 2f, size.height / 2f)
                        )
                        // Inner ring to indicate boundary
                        drawCircle(
                            color = Color(0xFF17324E),
                            radius = (size.minDimension / 2f) - with(density) { 2.dp.toPx() },
                            center = Offset(size.width / 2f, size.height / 2f),
                            style = Stroke(width = with(density) { 3.dp.toPx() })
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { /* no-op */ },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                // Update knob offset and clamp to circular radius
                                val proposed = knobOffsetPx + Offset(dragAmount.x, dragAmount.y)
                                val dx = proposed.x
                                val dy = proposed.y
                                val mag = sqrt(dx*dx + dy*dy)
                                val clamped = if (mag > movementRadiusPx && mag > 0f) {
                                    val scale = movementRadiusPx / mag
                                    Offset(dx * scale, dy * scale)
                                } else proposed
                                knobOffsetPx = clamped

                                // Normalized components -1..1
                                joystickX = if (movementRadiusPx > 0f) (clamped.x / movementRadiusPx) else 0f
                                // Global inversion: pushing up should yield positive joystickY
                                joystickY = if (movementRadiusPx > 0f) (-clamped.y / movementRadiusPx) else 0f

                                // Determine last direction using 12-sector mapping (0°=Right, 90°=Up)
                                if (mag > movementRadiusPx * 0.1f) {
                                    // Global inversion: pass inverted Y so pushing up yields Up
                                    val dir = vectorToDirection(clamped.x, -clamped.y)
                                    val prevDir = lastDirection
                                    lastDirection = dir
                                    if (prevDir != lastDirection && connectionStatus) {
                                        // Immediately send updated facing/position so peers reflect true direction even if we don't move
                                        val xPxNow = xDpToPx(playerWorldXDp)
                                        val colNow = floor(xPxNow / unitPx).toInt()
                                        val rowNow = floor((heightPx) / unitPx).toInt()
                                        val wrappedCol = ((colNow % tileMap.width) + tileMap.width) % tileMap.width
                                        mp?.sendPos(wrappedCol, rowNow, lastDirection.isFacingRight(),
                                            WorldRows.index(xDpToPx(if (activeWorldAxis == WorldAxis.Latitude) playerWorldXDp else playerOtherAxisDp), unitPx, tileMap.width),
                                            WorldRows.index(xDpToPx(if (activeWorldAxis == WorldAxis.Longitude) playerWorldXDp else playerOtherAxisDp), unitPx, tileMap.width))
                                    }
                                }
                            },
                            onDragEnd = {
                                knobOffsetPx = Offset.Zero
                                joystickX = 0f
                                joystickY = 0f
                            },
                            onDragCancel = {
                                knobOffsetPx = Offset.Zero
                                joystickX = 0f
                                joystickY = 0f
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Thumb (circular, translucent)
                Box(
                    modifier = Modifier
                        .offset(x = pxToDp(knobOffsetPx.x), y = pxToDp(knobOffsetPx.y))
                        .size(thumbSize)
                        .background(Color(0xFF17324E).copy(alpha = 0.9f), shape = CircleShape)
                )
            }

            Column(Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(start=12.dp,top=60.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                val attached = companion.links.any { it.owner==bedRound.selfId || it.target==RopeTarget("P",bedRound.selfId.toLong()) }
                if(companion.links.any { it.owner==bedRound.selfId && it.target.kind!="C" })
                    Button(onClick={companion.request("PULL")},enabled=!controlsBlocked) { Text(tr("Pull to my position")) }
                if(attached) Button(onClick={companion.request("RELEASE")},enabled=!controlsBlocked) { Text(tr("Release rope")) }
                if(equipment.loadout.shield) {
                    val blockInteraction=remember { MutableInteractionSource() }
                    val held by blockInteraction.collectIsPressedAsState()
                    LaunchedEffect(held,controlsBlocked) { equipment.blocking=held && !controlsBlocked }
                    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { equipment.blocking=false } }
                    Button(onClick={},interactionSource=blockInteraction,enabled=!controlsBlocked) { Text(tr("Hold Block")) }
                }
            }
            // Bottom-right: Jump and Hit buttons, transparent containers
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .safeDrawingPadding()
                    .padding(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                Button(
                    onClick = { jump() },
                    modifier = Modifier.semantics { contentDescription = tr("Jump") },
                    enabled = !isJumping && !controlsBlocked,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF17324E), contentColor = Color.White),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
                ) { ActionIcon(ActionIconKind.Jump) }
                Spacer(modifier = Modifier.height(8.dp))
                val hitInteraction = remember { MutableInteractionSource() }
                val isHitPressed by hitInteraction.collectIsPressedAsState()
                var lastHitAtMs by remember { mutableLongStateOf(0L) }
                fun throttledHit(minDelayMs: Long = 200L) {
                    if (controlsBlocked) return
                    val now = currentTimeMillis()
                    if (now - lastHitAtMs >= minDelayMs) {
                        lastHitAtMs = now
                        hit()
                    }
                }
                LaunchedEffect(isHitPressed) {
                    if (isHitPressed) {
                        throttledHit(minDelayMs = 200L)
                        delay(400L)
                    }
                    while (isHitPressed) {
                        throttledHit(minDelayMs = 400L)
                        delay(400L)
                    }
                }
                Button(
                    // Fast taps can begin/end between compositions, bypassing the hold effect.
                    // The shared throttle prevents a second hit when the hold already fired.
                    onClick = { throttledHit() },
                    modifier = Modifier.semantics { contentDescription = tr("Hit") },
                    enabled = !controlsBlocked,
                    interactionSource = hitInteraction,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF17324E), contentColor = Color.White),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
                ) { ActionIcon(ActionIconKind.Hit) }
                Spacer(modifier = Modifier.height(8.dp))
                val placeInteraction = remember { MutableInteractionSource() }
                val isPlacePressed by placeInteraction.collectIsPressedAsState()
                val selectedActionIcon = when (inventory.getOrNull(vm.selectedSlot.intValue)?.type) {
                    ItemType.Potion -> ActionIconKind.Use
                    else -> ActionIconKind.Place
                }
                val selectedActionDescription = when (selectedActionIcon) {
                    ActionIconKind.Use -> tr("Use")
                    else -> tr("Place")
                }
                LaunchedEffect(isPlacePressed, isPaused) {
                    if (isPaused) { isPlacePreviewing = false; return@LaunchedEffect }
                    isPlacePreviewing = isPlacePressed
                    if (isPlacePressed) {
                        previewSelectedAimCol = null
                        previewSelectedAimRow = null
                        previewSelectedFacing = null
                    }
                }
                Button(
                    // Click handles fast taps and accessibility activation exactly once.
                    // A cancelled press only dismisses the preview; it never places a block.
                    onClick = {
                        previewSelectedFacing?.let { lastDirection = it }
                        place()
                        isPlacePreviewing = false
                        previewSelectedAimCol = null
                        previewSelectedAimRow = null
                        previewSelectedFacing = null
                    },
                    modifier = Modifier.semantics { contentDescription = selectedActionDescription },
                    enabled = !controlsBlocked,
                    interactionSource = placeInteraction,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF17324E), contentColor = Color.White),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
                ) { ActionIcon(selectedActionIcon) }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GameScreenPreview() {
    DontGoToBedTheme {
        GameScreen(player = Player.Leo, mp = null)
    }
}

/** Keep the large presentation lambda out of the session setup method. Box is
 * inline; a real composable boundary lets ART optimise each method separately. */
@Composable
private fun GamePresentation(modifier: Modifier = Modifier.fillMaxSize(), content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Box(modifier = modifier, content = content)
}
