package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dontgotobed.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** Readable without the adult gate, network access, or initializing purchases. */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    var policy by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { policy = Res.readBytes("files/${privacyPolicyFileName()}").decodeToString() }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(24.dp)) {
            TextButton(onClick = onBack) { Text("Back") }
            SelectionContainer {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(policy ?: "Loading privacy policy…", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

expect fun privacyPolicyFileName(): String
