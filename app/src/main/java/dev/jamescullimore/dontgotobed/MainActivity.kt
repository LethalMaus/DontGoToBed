package dev.jamescullimore.dontgotobed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import dev.jamescullimore.dontgotobed.ui.theme.DontGoToBedTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DontGoToBedTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)) {
                        GameScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun GameScreen() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val blockSize = 20.dp
        val step = 16.dp
        val maxX = maxWidth - blockSize
        val centerX = (maxWidth - blockSize) / 2
        var playerWorldX by remember { mutableStateOf(0.dp) }
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()

        // Platform/block configuration
        val obstacleSize = 20.dp
        data class Platform(val left: Dp, val bottom: Dp) {
            val right: Dp get() = left + obstacleSize
            val top: Dp get() = bottom + obstacleSize
        }
        val platforms = remember(maxWidth) {
            val list = mutableListOf<Platform>()
            // Ground platforms
            list += listOf(0.15f, 0.3f, 0.5f, 0.7f, 0.85f).map { frac ->
                Platform(maxWidth * frac - obstacleSize / 2, 0.dp)
            }
            // A stacked tower (3 high)
            val towerLeft = maxWidth * 0.4f - obstacleSize / 2
            list += Platform(towerLeft, 0.dp)
            list += Platform(towerLeft, obstacleSize)
            list += Platform(towerLeft, obstacleSize * 2)
            // Floating platforms
            list += Platform(maxWidth * 0.6f - obstacleSize / 2, obstacleSize * 3)
            list += Platform(maxWidth * 0.2f - obstacleSize / 2, obstacleSize * 2)
            list
        }

        // Jump state (height above ground in pixels)
        var heightPx by remember { mutableStateOf(0f) }
        var isJumping by remember { mutableStateOf(false) }

        fun platformsHorizontallyOver(nx: Dp): List<Platform> {
            val left = nx
            val right = nx + blockSize
            return platforms.filter { p ->
                right > p.left && left < p.right
            }
        }

        fun applyHorizontalCollision(prevX: Dp, nx: Dp, heightDp: Dp): Dp {
            val playerBottom = heightDp
            val playerTop = heightDp + blockSize
            var clampedX: Dp? = null

            if (nx > prevX) {
                // Moving right: check left edges of vertically-overlapping platforms
                val prevRight = prevX + blockSize
                val nxRight = nx + blockSize
                val blockingLefts = platforms.mapNotNull { p ->
                    val pBottom = p.bottom
                    val pTop = p.top
                    val verticalOverlap = playerTop > pBottom && playerBottom < pTop
                    if (verticalOverlap) {
                        val edge = p.left
                        if (prevRight <= edge && nxRight > edge) edge else null
                    } else null
                }
                val clampLeft = blockingLefts.minOrNull()
                if (clampLeft != null) {
                    clampedX = (clampLeft - blockSize)
                }
            } else if (nx < prevX) {
                // Moving left: check right edges of vertically-overlapping platforms
                val prevLeft = prevX
                val nxLeft = nx
                val blockingRights = platforms.mapNotNull { p ->
                    val pBottom = p.bottom
                    val pTop = p.top
                    val verticalOverlap = playerTop > pBottom && playerBottom < pTop
                    if (verticalOverlap) {
                        val edge = p.right
                        if (prevLeft >= edge && nxLeft < edge) edge else null
                    } else null
                }
                val clampRight = blockingRights.maxOrNull()
                if (clampRight != null) {
                    clampedX = clampRight
                }
            }
            return (clampedX ?: nx).coerceIn(0.dp, maxX)
        }

        fun moveLeft() {
            val currentHeightDp = with(density) { heightPx.toDp() }
            val tentative = (playerWorldX - step)
            playerWorldX = applyHorizontalCollision(playerWorldX, tentative, currentHeightDp)
        }
        fun moveRight() {
            val currentHeightDp = with(density) { heightPx.toDp() }
            val tentative = (playerWorldX + step)
            playerWorldX = applyHorizontalCollision(playerWorldX, tentative, currentHeightDp)
        }

        fun jump() {
            if (isJumping) return
            isJumping = true
            scope.launch {
                // Much higher jump: ~2x apex height (v scaled by sqrt(2))
                var v = 1980f // initial upward velocity (px/s)
                val g = -3000f // gravity (px/s^2), negative because up is positive here
                val frameMs = 16L
                val dt = frameMs / 1000f
                while (true) {
                    val prevHeightDp = with(density) { heightPx.toDp() }
                    v += g * dt
                    heightPx += v * dt

                    // Ceiling collision: if ascending and the player's top crosses a platform's bottom, clamp and stop upward motion
                    val heightDpNow = with(density) { heightPx.toDp() }
                    if (v > 0f) {
                        val prevTop = prevHeightDp + blockSize
                        val nowTop = heightDpNow + blockSize
                        val hitBottom: Dp? = platformsHorizontallyOver(playerWorldX)
                            .map { it.bottom }
                            .filter { b -> prevTop <= b && nowTop > b }
                            .minOrNull()
                        if (hitBottom != null) {
                            // Clamp so player's top touches platform bottom, cancel upward velocity
                            heightPx = with(density) { (hitBottom - blockSize).toPx() }
                            v = 0f
                        }
                    }

                    // Land on a platform if descending past its top while horizontally over it
                    if (v <= 0f) {
                        val landingTop: Dp? = platformsHorizontallyOver(playerWorldX)
                            .map { it.top }
                            .filter { t -> heightDpNow <= t && prevHeightDp >= t }
                            .maxOrNull()
                        if (landingTop != null) {
                            heightPx = with(density) { landingTop.toPx() }
                            isJumping = false
                            break
                        }
                    }

                    // Land on ground
                    if (heightPx <= 0f) {
                        heightPx = 0f
                        isJumping = false
                        break
                    }
                    kotlinx.coroutines.delay(frameMs)
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Play area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF87CEEB)) // sky
                    .pointerInput(maxX, density) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            val delta = with(density) { dragAmount.toDp() }
                            val currentHeightDp = with(density) { heightPx.toDp() }
                            playerWorldX = applyHorizontalCollision(playerWorldX, playerWorldX + delta, currentHeightDp)
                        }
                    }
            ) {
                // Ground
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(12.dp)
                        .background(Color(0xFF228B22))
                )

                // Platforms (ground, stacked, floating)
                val cameraOffsetX = playerWorldX - centerX
                platforms.forEach { p ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = p.left - cameraOffsetX, y = -p.bottom)
                            .size(obstacleSize)
                            .background(Color.DarkGray)
                    )
                }

                // Player block
                val heightDp = with(density) { heightPx.toDp() }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = centerX, y = -heightDp)
                        .size(blockSize)
                        .background(Color.Red)
                )
            }

            // Start falling if leaving support
            LaunchedEffect(playerWorldX, isJumping) {
                if (!isJumping) {
                    val hDp = with(density) { heightPx.toDp() }
                    val supported = if (hDp == 0.dp) true else platformsHorizontallyOver(playerWorldX).any { it.top == hDp }
                    if (!supported) {
                        isJumping = true
                        scope.launch {
                            var v = 0f
                            val g = -3000f
                            val frameMs = 16L
                            val dt = frameMs / 1000f
                            while (true) {
                                val prevHeightDp = with(density) { heightPx.toDp() }
                                v += g * dt
                                heightPx += v * dt
                                val hNow = with(density) { heightPx.toDp() }
                                val landingTop: Dp? = platformsHorizontallyOver(playerWorldX)
                                    .map { it.top }
                                    .filter { t -> hNow <= t && prevHeightDp >= t }
                                    .maxOrNull()
                                if (landingTop != null) {
                                    heightPx = with(density) { landingTop.toPx() }
                                    isJumping = false
                                    break
                                }
                                if (heightPx <= 0f) {
                                    heightPx = 0f
                                    isJumping = false
                                    break
                                }
                                kotlinx.coroutines.delay(frameMs)
                            }
                        }
                    }
                }
            }

            // Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Interaction sources to detect press and hold
                val leftInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val rightInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

                var leftPressed by remember { mutableStateOf(false) }
                var rightPressed by remember { mutableStateOf(false) }

                // Observe left button interactions
                LaunchedEffect(leftInteraction) {
                    leftInteraction.interactions.collect { interaction ->
                        when (interaction) {
                            is androidx.compose.foundation.interaction.PressInteraction.Press -> leftPressed = true
                            is androidx.compose.foundation.interaction.PressInteraction.Release -> leftPressed = false
                            is androidx.compose.foundation.interaction.PressInteraction.Cancel -> leftPressed = false
                            else -> Unit
                        }
                    }
                }
                // Observe right button interactions
                LaunchedEffect(rightInteraction) {
                    rightInteraction.interactions.collect { interaction ->
                        when (interaction) {
                            is androidx.compose.foundation.interaction.PressInteraction.Press -> rightPressed = true
                            is androidx.compose.foundation.interaction.PressInteraction.Release -> rightPressed = false
                            is androidx.compose.foundation.interaction.PressInteraction.Cancel -> rightPressed = false
                            else -> Unit
                        }
                    }
                }

                // While pressed, keep moving every few milliseconds
                LaunchedEffect(leftPressed) {
                    while (leftPressed) {
                        moveLeft()
                        kotlinx.coroutines.delay(16)
                    }
                }
                LaunchedEffect(rightPressed) {
                    while (rightPressed) {
                        moveRight()
                        kotlinx.coroutines.delay(16)
                    }
                }

                Button(onClick = { moveLeft() }, interactionSource = leftInteraction) { Text("Left") }
                Button(onClick = { jump() }, enabled = !isJumping) { Text("Jump") }
                Button(onClick = { moveRight() }, interactionSource = rightInteraction) { Text("Right") }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GameScreenPreview() {
    DontGoToBedTheme {
        GameScreen()
    }
}