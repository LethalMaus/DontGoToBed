package dev.jamescullimore.dontgotobed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun PlatformApp() {
    val localIp = remember { getLocalIpAddress() }
    GameApp(localIp)
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
