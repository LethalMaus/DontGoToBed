package dev.jamescullimore.dontgotobed

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import java.net.Inet4Address

@Suppress("DEPRECATION")
internal actual class LanDiscovery actual constructor() {
    companion object { internal lateinit var appContext: Context }
    private val handler = Handler(Looper.getMainLooper())
    private val manager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var stopped = false
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private val found = linkedMapOf<String, NsdServiceInfo>()
    private val games = linkedMapOf<String, NearbyGame>()
    private val pending = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var changed: (List<NearbyGame>) -> Unit = {}
    private var failed: () -> Unit = {}
    private fun main(block: () -> Unit) { handler.post { block() } }

    actual fun advertise(name: String, port: Int) = main {
        if (stopped) return@main
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) = Unit
        }
        registration = listener
        runCatching { manager.registerService(NsdServiceInfo().apply {
            serviceName = name; serviceType = LAN_SERVICE_TYPE; setPort(port)
        }, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    actual fun browse(onGames: (List<NearbyGame>) -> Unit, onError: () -> Unit) = main {
        if (stopped || discovery != null) return@main
        changed = onGames; failed = onError
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) = main {
                if (!stopped) failed()
            }
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) = main {
                if (!stopped && info.serviceType.trimEnd('.') == LAN_SERVICE_TYPE.trimEnd('.')) {
                    found[info.serviceName] = info
                    pending.addLast(info)
                    resolveNext()
                }
            }
            override fun onServiceLost(info: NsdServiceInfo) = main {
                if (!stopped) {
                    found.remove(info.serviceName); games.remove(info.serviceName)
                    changed(games.values.sortedBy { it.name })
                }
            }
        }
        discovery = listener
        runCatching { manager.discoverServices(LAN_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { failed() }
    }

    // Older Android releases allow only one outstanding resolve at a time.
    private fun resolveNext() {
        if (stopped || resolving) return
        while (pending.isNotEmpty()) {
            val info = pending.removeFirst()
            if (found[info.serviceName] !== info) continue
            resolving = true
            val listener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(service: NsdServiceInfo, code: Int) = main {
                    resolving = false
                    resolveNext()
                }
                override fun onServiceResolved(service: NsdServiceInfo) = main {
                    resolving = false
                    if (!stopped && found[info.serviceName] === info) {
                        // Both game servers currently bind IPv4 sockets.
                        val address = (if (android.os.Build.VERSION.SDK_INT >= 34) service.hostAddresses.filterIsInstance<Inet4Address>().firstOrNull()
                            else service.host as? Inet4Address)?.hostAddress
                        if (address != null && service.port in 1..65535) {
                            games[info.serviceName] = NearbyGame(info.serviceName, service.serviceName, address, service.port)
                            changed(games.values.sortedBy { it.name })
                        }
                    }
                    resolveNext()
                }
            }
            try { manager.resolveService(info, listener) } catch (_: Exception) {
                resolving = false; failed(); continue
            }
            return
        }
    }

    actual fun stop() = main {
        stopped = true
        discovery?.let { runCatching { manager.stopServiceDiscovery(it) } }
        registration?.let { runCatching { manager.unregisterService(it) } }
        discovery = null; registration = null
        found.clear(); games.clear(); pending.clear()
        changed = {}; failed = {}
    }
}
