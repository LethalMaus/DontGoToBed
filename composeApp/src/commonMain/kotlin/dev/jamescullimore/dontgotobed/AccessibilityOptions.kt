package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Local presentation/input preferences; deliberately not part of the shared world. */
data class AccessibilityOptions(val buttonControls: Boolean = false, val reduceMotion: Boolean = false)

fun SettingsStore.loadAccessibilityOptions() = AccessibilityOptions(
    buttonControls = readInt("buttonControls", 0) == 1,
    reduceMotion = readInt("reduceMotion", 0) == 1
)

fun SettingsStore.save(options: AccessibilityOptions) {
    writeInt("buttonControls", if (options.buttonControls) 1 else 0)
    writeInt("reduceMotion", if (options.reduceMotion) 1 else 0)
}

@Composable
expect fun rememberSystemReducedMotion(): Boolean

@Composable
fun AccessibilitySettings(options: AccessibilityOptions, onChange: (AccessibilityOptions) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF10243A)).padding(12.dp)) {
        Text("Accessibility", color = Color.White)
        listOf(
            Triple("Button controls", "Move in short steps without dragging the joystick.", options.buttonControls),
            Triple("Reduce motion and flashes", "Turn the world instantly and remove hit flashes. System reduced motion is also respected.", options.reduceMotion)
        ).forEachIndexed { index, (label, detail, checked) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .semantics(mergeDescendants = true) {}
                .toggleable(checked, role = Role.Switch, onValueChange = {
                    onChange(if (index == 0) options.copy(buttonControls = it) else options.copy(reduceMotion = it))
                }).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(label, color = Color.White)
                    Text(detail, color = Color(0xFFCBD9E8))
                }
                Switch(checked, onCheckedChange = null)
            }
        }
    }
}
