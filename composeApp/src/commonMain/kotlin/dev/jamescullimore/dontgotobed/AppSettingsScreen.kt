package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AppSettingsScreen(audio: AudioOptions, onAudio: (AudioOptions) -> Unit,
    accessibility: AccessibilityOptions, onAccessibility: (AccessibilityOptions) -> Unit,
    onLanguage: (AppLanguage) -> Unit, onBack: () -> Unit, onHelp: () -> Unit,
    onSupport: () -> Unit, onPrivacy: () -> Unit) {
    var panel by remember { mutableStateOf<String?>(null) }
    GameBackHandler(panel != null) { panel = null }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF10243A))
        .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout)).padding(24.dp), contentAlignment = Alignment.Center) {
        val columns = if (maxWidth >= 500.dp) 2 else 1
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Settings"), color = Color(0xFFFFCE66), fontSize = 26.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onBack) { Text(tr("Done")) }
            }
            val languageName = if (AppLocale.language == AppLanguage.System) tr("System language") else AppLocale.language.nativeName
            val actions = listOf(
                Triple(tr("Language"), languageName, { panel = "language" }),
                Triple(tr("Sound"), tr("Music & effects"), { panel = "sound" }),
                Triple(tr("Accessibility"), tr("Controls & motion"), { panel = "accessibility" }),
                Triple(tr("How to play"), tr("Adventure guide"), onHelp),
                Triple(tr("Grown-ups"), tr("Optional support"), onSupport),
                Triple(tr("Privacy"), tr("Read our policy"), onPrivacy)
            )
            actions.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (title, detail, action) ->
                        FilledTonalButton(onClick = action, modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(detail, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    panel?.let { current ->
        AlertDialog(onDismissRequest = { panel = null },
            title = { Text(tr(when(current) { "language" -> "Language"; "sound" -> "Sound"; else -> "Accessibility" })) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    when (current) {
                        "language" -> AppLanguage.entries.forEach { language ->
                            TextButton(onClick = { onLanguage(language); panel = null }, modifier = Modifier.fillMaxWidth()) {
                                RadioButton(selected = AppLocale.language == language, onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (language == AppLanguage.System) tr("System language") else language.nativeName,
                                    modifier = Modifier.weight(1f))
                            }
                        }
                        "sound" -> AudioSettings(audio, onAudio)
                        else -> AccessibilitySettings(accessibility, onAccessibility)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { panel = null }) { Text(tr("Done")) } })
    }
}
