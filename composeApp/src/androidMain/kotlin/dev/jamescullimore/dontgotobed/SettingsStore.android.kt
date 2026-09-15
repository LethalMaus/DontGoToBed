package dev.jamescullimore.dontgotobed

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberSettingsStore(): SettingsStore {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        val preferences = context.getSharedPreferences("game_settings", Context.MODE_PRIVATE)
        object : SettingsStore {
            override fun readInt(key: String, default: Int) = try { preferences.getInt(key, default) } catch (_: ClassCastException) { default }
            override fun writeInt(key: String, value: Int) { preferences.edit().putInt(key, value).apply() }
        }
    }
}

actual fun revenueCatPublicKey(): String = BuildConfig.REVENUECAT_API_KEY

actual fun privacyPolicyFileName(): String = "privacy-android.txt"

@Composable
actual fun rememberPurchaseRecoveryStore(): SettingsStore {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        val preferences = context.getSharedPreferences("purchase_recovery", Context.MODE_PRIVATE)
        object : SettingsStore {
            override fun readInt(key: String, default: Int) = try { preferences.getInt(key, default) } catch (_: ClassCastException) { default }
            // Tiny, infrequent writes must reach disk before the store UI can interrupt us.
            override fun writeInt(key: String, value: Int) { preferences.edit().putInt(key, value).commit() }
        }
    }
}

@Composable
actual fun GameBackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled, onBack)
}
