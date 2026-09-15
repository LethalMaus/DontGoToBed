package dev.jamescullimore.dontgotobed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readValue
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.darwin.freeifaddrs
import platform.darwin.getifaddrs
import platform.darwin.ifaddrs
import platform.darwin.inet_ntoa
import platform.posix.AF_INET
import platform.posix.IFF_LOOPBACK
import platform.posix.IFF_UP
import platform.posix.sockaddr_in

@Composable
actual fun PlatformApp() {
    val localIp = remember { getLocalIpAddress() }
    GameApp(localIp)
}

/**
 * Returns the active LAN IPv4 address shown to peers joining an iOS-hosted game.
 * The server still listens on all interfaces; this is only the address players enter.
 */
@OptIn(ExperimentalForeignApi::class)
private fun getLocalIpAddress(): String = memScoped {
    val addresses = allocPointerTo<ifaddrs>()
    if (getifaddrs(addresses.ptr) != 0) return@memScoped "0.0.0.0"

    try {
        var current: CPointer<ifaddrs>? = addresses.value
        while (current != null) {
            val entry = current.pointed
            val socketAddress = entry.ifa_addr
            val flags = entry.ifa_flags.toInt()
            if (
                socketAddress != null &&
                socketAddress.pointed.sa_family.toInt() == AF_INET &&
                flags and IFF_UP != 0 &&
                flags and IFF_LOOPBACK == 0
            ) {
                val ipv4 = socketAddress.reinterpret<sockaddr_in>().pointed
                inet_ntoa(ipv4.sin_addr.readValue())?.toKString()?.let { return@memScoped it }
            }
            current = entry.ifa_next
        }
        "0.0.0.0"
    } finally {
        freeifaddrs(addresses.value)
    }
}
