package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AccessibleDirectionButton(label: String, glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick, enabled = enabled,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(56.dp).semantics { contentDescription = label },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF17324E), contentColor = Color.White)) {
        Text(glyph, fontSize = 22.sp, modifier = Modifier.clearAndSetSemantics {})
    }
}
