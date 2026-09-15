package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Credits are available offline, alongside the audio controls. */
@Composable
internal fun AudioCreditsButton() {
    var open by remember { mutableStateOf(false) }
    Button(onClick = { open = true }) { Text("Audio credits") }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("Audio credits") },
        text = {
            SelectionContainer {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Music", style = MaterialTheme.typography.titleMedium)
                    Text("Music composed by Liborio Conti. Thank you for the music accompanying our adventure!")
                    Text("https://www.no-copyright-music.com/")
                    Text("Music license: https://www.no-copyright-music.com/license/")
                    Text("Sound effects", style = MaterialTheme.typography.titleMedium)
                    Text("Sound effects sourced from Pixabay. Thank you to the contributing creators!")
                    Text("https://pixabay.com/sound-effects/")
                    Text("Pixabay Content License: https://pixabay.com/service/license-summary/")
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } }
    )
}
