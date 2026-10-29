package dev.jamescullimore.dontgotobed

import kotlinx.cinterop.*
import platform.Foundation.*
import platform.darwin.inet_ntop
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.*

internal actual class LanDiscovery actual constructor() {
    private var stopped = false
    private var publisher: NSNetService? = null
    private var browser: NSNetServiceBrowser? = null
    private val services = linkedMapOf<String, NSNetService>()
    private val games = linkedMapOf<String, NearbyGame>()
    private var changed: (List<NearbyGame>) -> Unit = {}
    private var failed: () -> Unit = {}
    private fun main(block: () -> Unit) { dispatch_async(dispatch_get_main_queue(), block) }
    private fun key(service: NSNetService) = service.domain + service.type + service.name
    private val resolver = object : NSObject(), NSNetServiceDelegateProtocol {
        override fun netServiceDidResolveAddress(sender: NSNetService) {
            if (stopped || services[key(sender)] != sender) return
            val address = sender.addresses?.asSequence()?.mapNotNull { raw ->
                val data = raw as? NSData ?: return@mapNotNull null
                if (data.length < sizeOf<sockaddr_in>().toULong()) return@mapNotNull null
                val addr = data.bytes?.reinterpret<sockaddr_in>() ?: return@mapNotNull null
                if (addr.pointed.sin_family.toInt() != AF_INET) return@mapNotNull null
                memScoped {
                    val buffer = allocArray<ByteVar>(INET_ADDRSTRLEN)
                    inet_ntop(AF_INET, addr.pointed.sin_addr.ptr, buffer, INET_ADDRSTRLEN.toUInt())?.toKString()
                }
            }?.firstOrNull()
            if (address != null && sender.port in 1..65535) {
                games[key(sender)] = NearbyGame(key(sender), sender.name, address, sender.port.toInt())
                changed(games.values.sortedBy { it.name })
            }
        }
        override fun netService(sender: NSNetService, didNotResolve: Map<Any?, *>) {
            if (!stopped && services[key(sender)] == sender) {
                games.remove(key(sender)); changed(games.values.sortedBy { it.name })
            }
        }
    }
    private val browserDelegate = object : NSObject(), NSNetServiceBrowserDelegateProtocol {
        @ObjCSignatureOverride
        override fun netServiceBrowser(browser: NSNetServiceBrowser, didFindService: NSNetService, moreComing: Boolean) {
            if (stopped) return
            val id = key(didFindService)
            services.remove(id)?.stop()
            services[id] = didFindService
            didFindService.delegate = resolver
            didFindService.resolveWithTimeout(8.0)
        }
        @ObjCSignatureOverride
        override fun netServiceBrowser(browser: NSNetServiceBrowser, didRemoveService: NSNetService, moreComing: Boolean) {
            if (stopped) return
            services.remove(key(didRemoveService))?.let { it.stop(); it.delegate = null }
            games.remove(key(didRemoveService))
            changed(games.values.sortedBy { it.name })
        }
        override fun netServiceBrowser(browser: NSNetServiceBrowser, didNotSearch: Map<Any?, *>) {
            if (!stopped) failed()
        }
    }
    actual fun advertise(name: String, port: Int) = main {
        if (!stopped) publisher = NSNetService(domain = "local.", type = LAN_SERVICE_TYPE, name = name, port = port).also { it.publish() }
    }
    actual fun browse(onGames: (List<NearbyGame>) -> Unit, onError: () -> Unit) = main {
        if (stopped || browser != null) return@main
        changed = onGames; failed = onError
        browser = NSNetServiceBrowser().also {
            it.delegate = browserDelegate
            it.searchForServicesOfType(LAN_SERVICE_TYPE, "local.")
        }
    }
    actual fun stop() = main {
        stopped = true
        publisher?.stop(); publisher = null
        browser?.stop(); browser?.delegate = null; browser = null
        services.values.forEach { it.stop(); it.delegate = null }
        services.clear(); games.clear(); changed = {}; failed = {}
    }
}
