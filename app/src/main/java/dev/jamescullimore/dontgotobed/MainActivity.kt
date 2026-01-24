package dev.jamescullimore.dontgotobed

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.jamescullimore.dontgotobed.ui.theme.DontGoToBedTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        setContent {
            DontGoToBedTheme {
                var selected by remember { mutableStateOf<Player?>(null) }
                var initialIsHost by remember { mutableStateOf<Boolean?>(null) }
                var initialHostIp by remember { mutableStateOf(MultiplayerManager.DEFAULT_HOST_IP) }
                val localIp = remember { getLocalIpAddress() }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        var sharedMp by remember { mutableStateOf<MultiplayerManager?>(null) }
                        if (selected == null) {
                            SelectCharacterScreen(
                                localIp = localIp,
                                mp = sharedMp,
                                setMp = { newMp -> sharedMp = newMp },
                                onSelect = { player, isHost, joinIp ->
                                    selected = player
                                    initialIsHost = isHost
                                    initialHostIp = if (!isHost) {
                                        joinIp?.ifBlank { MultiplayerManager.DEFAULT_HOST_IP } ?: MultiplayerManager.DEFAULT_HOST_IP
                                    } else {
                                        MultiplayerManager.DEFAULT_HOST_IP
                                    }
                                }
                            )
                        } else {
                            GameScreen(
                                player = selected!!,
                                initialIsHostSelected = initialIsHost,
                                initialHostIp = initialHostIp,
                                mp = sharedMp
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = WindowInsetsControllerCompat(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private fun getLocalIpAddress(): String {
    val default = "0.0.0.0"
    return try {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
        val list = java.util.Collections.list(interfaces)
        for (ni in list) {
            if (!ni.isUp || ni.isLoopback) continue
            val addrs = java.util.Collections.list(ni.inetAddresses)
            for (addr in addrs) {
                if (addr is java.net.Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                    return addr.hostAddress ?: default
                }
            }
        }
        default
    } catch (_: Exception) {
        default
    }
}