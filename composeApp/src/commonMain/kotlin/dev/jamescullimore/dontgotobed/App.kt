package dev.jamescullimore.dontgotobed

import androidx.compose.runtime.Composable
import dev.jamescullimore.dontgotobed.ui.theme.DontGoToBedTheme

@Composable
fun App() {
    DontGoToBedTheme {
        PlatformApp()
    }
}

@Composable
expect fun PlatformApp()
