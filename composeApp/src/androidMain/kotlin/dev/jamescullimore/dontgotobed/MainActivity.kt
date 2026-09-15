package dev.jamescullimore.dontgotobed

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        // Regression-test entry point, deliberately unavailable in release builds.
        val debugDrop = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            intent.getBooleanExtra("debug_drop_on_mammy", false)
        val debugBed = if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
            intent.getIntExtra("debug_bed", 0).coerceIn(0, 3) else 0
        val testSize = intent.getIntExtra("debug_map_size", 192).takeIf { it in listOf(192, 384, 576) } ?: 192
        val testPlayer = Player.entries.firstOrNull { it.name == intent.getStringExtra("debug_player") } ?: Player.Leo
        setContent {
            if (debugDrop || debugBed > 0) {
                GameScreen(testPlayer, initialIsHostSelected = true, mp = null,
                    worldConfig = WorldConfig(horizontalSize = testSize, initialZombies = 0, initialSkeletons = 0),
                    debugDropOnMammy = debugDrop, debugBed = debugBed)
            } else App()
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
