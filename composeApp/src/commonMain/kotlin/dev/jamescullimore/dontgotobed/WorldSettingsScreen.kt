package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WorldSettingsScreen(config: WorldConfig, onChange: (WorldConfig) -> Unit, onBack: () -> Unit, readOnly: Boolean = false) {
    var advanced by remember { mutableStateOf(false) }
    val cream = Color(0xFFFFF1CC)
    val gold = Color(0xFFFFCE66)
    Column(Modifier.fillMaxSize().background(Color(0xFF10243A)).safeDrawingPadding()
        .verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 700.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Choose your world"), color = gold, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onBack) { Text(tr("Done"), color = gold) }
            }
            Text(if (readOnly) tr("Your host chooses the world. Join their game to explore it together.") else tr("Exploration lets you continue a saved world or start a new one."), color = cream)
            listOf(192, 384, 576).forEachIndexed { index, size ->
                val chosen = !readOnly && !config.exploration && config.horizontalSize == size
                Surface(onClick = { onChange(config.copy(horizontalSize = size, exploration = false)) }, enabled = !readOnly,
                    shape = RoundedCornerShape(16.dp), color = Color(0xFF203C52),
                    border = BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) gold else Color(0xFF496478)),
                    modifier = Modifier.fillMaxWidth().semantics { selected = chosen }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        GameIcon(GameIconKind.Worlds, null, tint = gold)
                        Column(Modifier.weight(1f)) {
                            Text(WorldLayouts.name(size), color = cream, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(listOf(tr("A smaller world for a quick adventure"), tr("More space, bridges and paths to explore"), tr("Our biggest world, with extra landmarks"))[index], color = Color(0xFFABC4D7), fontSize = 13.sp)
                        }
                        if (chosen) Text(tr("Selected"), color = gold, fontSize = 12.sp)
                    }
                }
            }
            Surface(onClick = { onChange(config.copy(horizontalSize = ExplorationWorld.SIZE, exploration = true)) },
                enabled = !readOnly, shape = RoundedCornerShape(16.dp), color = Color(0xFF203C52),
                border = BorderStroke(if (config.exploration) 2.dp else 1.dp, if (config.exploration) gold else Color(0xFF496478)),
                modifier = Modifier.fillMaxWidth().semantics { selected = config.exploration }) {
                Column(Modifier.padding(16.dp)) {
                    Text(tr("Portal worlds · Preview"), color = gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(tr("Six biomes, caves, hazards and independent portal travel"), color = cream, fontSize = 13.sp)
                }
            }
            Text(tr("Adventure rules"), color = gold, fontWeight = FontWeight.Bold)
            listOf(false to "Find Mammy before nightfall!", true to "Unlimited exploration").forEach { (unlimited, label) ->
                Surface(onClick = { onChange(config.copy(unlimited = unlimited)) }, enabled = !readOnly,
                    color = Color(0xFF203C52), shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(if(config.unlimited == unlimited) 2.dp else 1.dp, if(config.unlimited == unlimited) gold else Color(0xFF496478)),
                    modifier = Modifier.fillMaxWidth().semantics { selected = config.unlimited == unlimited }) {
                    Column(Modifier.padding(14.dp)) {
                        Text(tr(label), color = cream)
                        if(unlimited) Text(tr("Explore, build and fight without a time limit. Mammy is optional."), color = cream, fontSize = 13.sp)
                    }
                }
            }
            if (!readOnly) {
                TextButton(onClick = { advanced = !advanced }) { GameIcon(GameIconKind.Settings, null, tint = gold); Spacer(Modifier.width(8.dp)); Text(if (advanced) tr("Hide world options") else tr("World options"), color = gold) }
                if (advanced) {
                    WorldCounter(tr("Starting zombies"), config.initialZombies, 0..12) { onChange(config.copy(initialZombies = it)) }
                    WorldCounter(tr("Starting skeletons"), config.initialSkeletons, 0..12) { onChange(config.copy(initialSkeletons = it)) }
                    Text(tr("Zero starting enemies does not disable enemies later in the round."), color = cream, fontSize = 12.sp)
                    WorldCounter(tr("Nearby enemy rows"), config.nearbyEnemyRows, 0..2) { onChange(config.copy(nearbyEnemyRows = it)) }
                    WorldCounter(tr("Nearby terrain rows"), config.nearbyTerrainRows, 0..2) { onChange(config.copy(nearbyTerrainRows = it)) }
                }
            }
        }
    }
}

@Composable
private fun WorldCounter(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFFFFF1CC), modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange(value - 1) }, enabled = value > range.first,
            modifier = Modifier.semantics { contentDescription = tr("Fewer {0}", label) }) { Text("−", color = Color(0xFFFFCE66), fontSize = 24.sp) }
        Text("$value", color = Color.White, modifier = Modifier.semantics { contentDescription = "$label: $value" })
        IconButton(onClick = { onChange(value + 1) }, enabled = value < range.last,
            modifier = Modifier.semantics { contentDescription = tr("More {0}", label) }) { Text("+", color = Color(0xFFFFCE66), fontSize = 24.sp) }
    }
}
