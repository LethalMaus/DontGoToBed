package dev.jamescullimore.dontgotobed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults

@Composable
actual fun rememberSettingsStore(): SettingsStore = remember {
    object : SettingsStore {
        private val preferences = NSUserDefaults.standardUserDefaults
        override fun readInt(key: String, default: Int): Int =
            if (preferences.objectForKey(key) == null) default else preferences.integerForKey(key).toInt()
        override fun writeInt(key: String, value: Int) { preferences.setInteger(value.toLong(), key) }
    }
}

actual fun revenueCatPublicKey(): String =
    (NSBundle.mainBundle.objectForInfoDictionaryKey("RevenueCatPublicAPIKey") as? String)
        ?.takeUnless { it.startsWith("$(") }.orEmpty()

@Composable
actual fun GameBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

actual fun privacyPolicyFileName(): String = "privacy-ios.txt"

@Composable
actual fun rememberPurchaseRecoveryStore(): SettingsStore = remember {
    object : SettingsStore {
        private val preferences = NSUserDefaults.standardUserDefaults
        override fun readInt(key: String, default: Int): Int =
            if (preferences.objectForKey("purchaseRecovery.$key") == null) default else preferences.integerForKey("purchaseRecovery.$key").toInt()
        override fun writeInt(key: String, value: Int) { preferences.setInteger(value.toLong(), "purchaseRecovery.$key") }
    }
}
