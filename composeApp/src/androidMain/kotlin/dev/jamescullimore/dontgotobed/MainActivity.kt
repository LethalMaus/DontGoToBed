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
        LanDiscovery.appContext = applicationContext
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        if (BuildConfig.DEBUG && intent.hasExtra("debug_language")) {
            getSharedPreferences("game_settings", MODE_PRIVATE).edit()
                .putInt("language", intent.getIntExtra("debug_language", 0).coerceIn(0, 7)).apply()
        }
        // Regression-test entry point, deliberately unavailable in release builds.
        val debugDrop = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            intent.getBooleanExtra("debug_drop_on_mammy", false)
        val debugBed = if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
            intent.getIntExtra("debug_bed", 0).coerceIn(0, 4) else 0
        val debugAdventure = if(BuildConfig.DEBUG) intent.getIntExtra("debug_adventure",0).coerceIn(0,14) else 0
        val testOutfit = decodePlayerOutfit(intent.getStringExtra("debug_outfit"))
        val debugExploration = BuildConfig.DEBUG && intent.getBooleanExtra("debug_exploration", false)
        val testSize = intent.getIntExtra("debug_map_size", 192).takeIf { it in listOf(192, 384, 576) } ?: 192
        val testPlayer = Player.entries.firstOrNull { it.name == intent.getStringExtra("debug_player") } ?: Player.Leo
        if (BuildConfig.DEBUG && intent.hasExtra("debug_language")) {
            AppLocale.language = AppLanguage.entries.firstOrNull { it.id == intent.getIntExtra("debug_language", 1) } ?: AppLanguage.English
        }
        setContent {
            if (debugDrop || debugBed > 0 || debugExploration || debugAdventure>0) {
                GameScreen(testPlayer, initialIsHostSelected = true, mp = null,
                    worldConfig = WorldConfig(horizontalSize = if (debugExploration || debugAdventure>0) ExplorationWorld.SIZE else testSize, initialZombies = 0, initialSkeletons = 0, exploration = debugExploration || debugAdventure>0, unlimited=debugAdventure>0 && debugAdventure!=12, portalSeed=if(BuildConfig.DEBUG) intent.getIntExtra("debug_portal_seed",0) else 0),
                    debugDropOnMammy = debugDrop, debugBed = debugBed,debugAdventure=debugAdventure,outfit=testOutfit,
                    accessibility=AccessibilityOptions(showInfoText= !intent.getBooleanExtra("debug_hide_info", false),buttonControls=BuildConfig.DEBUG && intent.getBooleanExtra("debug_button_controls",false),reduceMotion=BuildConfig.DEBUG && intent.getBooleanExtra("debug_reduce_motion",false)))
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
