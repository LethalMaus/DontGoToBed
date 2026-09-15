package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

@Composable
fun WorldSettingsScreen(config: WorldConfig, onChange: (WorldConfig) -> Unit, onBack: () -> Unit,
    accessibility: AccessibilityOptions = AccessibilityOptions(), onAccessibility: (AccessibilityOptions) -> Unit = {},
    audioOptions: AudioOptions = AudioOptions(), onAudio: (AudioOptions) -> Unit = {}) {
    fun adjust(value: Int, delta: Int, range: IntRange) = (value + delta).coerceIn(range)
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF10243A)).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("World settings", color = Color.White)
        Spacer(Modifier.height(20.dp))
        Text("World size: ${config.horizontalSize} × ${config.horizontalSize} × ${config.height}", color = Color.White)
        Text(WorldLayouts.name(config.horizontalSize), color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(192, 384, 576).forEach { size -> Button(modifier = Modifier.semantics { contentDescription = "World size $size, ${WorldLayouts.name(size)}"; selected = config.horizontalSize == size }, onClick = { onChange(config.copy(horizontalSize = size)) }) { Text("$size", color = Color.White) } }
        }
        Spacer(Modifier.height(14.dp))
        Text("Starting zombies: ${config.initialZombies}", color = Color.White)
        Row { Button(modifier = Modifier.semantics { contentDescription = "Fewer starting zombies" }, enabled = config.initialZombies > 0, onClick = { onChange(config.copy(initialZombies = adjust(config.initialZombies, -1, 0..12))) }) { Text("−", color = Color.White) }; Button(modifier = Modifier.semantics { contentDescription = "More starting zombies" }, enabled = config.initialZombies < 12, onClick = { onChange(config.copy(initialZombies = adjust(config.initialZombies, 1, 0..12))) }) { Text("+", color = Color.White) } }
        Text("Starting skeletons: ${config.initialSkeletons}", color = Color.White)
        Row { Button(modifier = Modifier.semantics { contentDescription = "Fewer starting skeletons" }, enabled = config.initialSkeletons > 0, onClick = { onChange(config.copy(initialSkeletons = adjust(config.initialSkeletons, -1, 0..12))) }) { Text("−", color = Color.White) }; Button(modifier = Modifier.semantics { contentDescription = "More starting skeletons" }, enabled = config.initialSkeletons < 12, onClick = { onChange(config.copy(initialSkeletons = adjust(config.initialSkeletons, 1, 0..12))) }) { Text("+", color = Color.White) } }
        Spacer(Modifier.height(14.dp))
        Text("Nearby enemies: ${if (config.nearbyEnemyRows == 0) "Off" else "${config.nearbyEnemyRows} row(s) each side"}", color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (rows in 0..2) {
                Button(modifier = Modifier.semantics { contentDescription = "Nearby enemies: $rows rows each side"; selected = config.nearbyEnemyRows == rows }, onClick = { onChange(config.copy(nearbyEnemyRows = rows)) }) {
                    Text(if (rows == 0) "Off" else "$rows", color = Color.White)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Nearby terrain: ${if (config.nearbyTerrainRows == 0) "Off" else "${config.nearbyTerrainRows} row(s) each side"}", color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (rows in 0..2) {
                Button(modifier = Modifier.semantics { contentDescription = "Nearby terrain: $rows rows each side"; selected = config.nearbyTerrainRows == rows }, onClick = { onChange(config.copy(nearbyTerrainRows = rows)) }) {
                    Text(if (rows == 0) "Off" else "$rows", color = Color.White)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        AudioSettings(audioOptions, onAudio)
        AccessibilitySettings(accessibility, onAccessibility)
        Button(onClick = onBack) { Text("Back", color = Color.White) }
    }
}
