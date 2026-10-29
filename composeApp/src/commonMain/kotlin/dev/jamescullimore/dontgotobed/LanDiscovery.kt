package dev.jamescullimore.dontgotobed

/** Versioned DNS-SD type keeps incompatible game protocols out of the join list. */
internal const val LAN_SERVICE_TYPE = "_dgtb-v7._tcp."
internal fun newLanGameName() = "DGTB-${kotlin.random.Random.nextInt(100000, 1000000)}"
internal data class NearbyGame(val id: String, val name: String, val address: String, val port: Int)

/** Each instance is single-use; stop also invalidates pending native callbacks. */
internal expect class LanDiscovery() {
    fun advertise(name: String, port: Int)
    fun browse(onGames: (List<NearbyGame>) -> Unit, onError: () -> Unit)
    fun stop()
}
