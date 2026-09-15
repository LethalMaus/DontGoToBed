package dev.jamescullimore.dontgotobed

/** Local recovery marker, never proof that a purchase completed. */
data class PendingTip(val product: String, val startedAt: Long) {
    companion object {
        val products = listOf("tip_small", "tip_medium", "tip_large")
        fun read(store: SettingsStore): PendingTip? {
            val product = products.getOrNull(store.readInt("pendingProduct", -1)) ?: return null
            val time = (store.readInt("pendingTimeHigh", 0).toLong() shl 32) or
                (store.readInt("pendingTimeLow", 0).toLong() and 0xffffffffL)
            return if (time > 0) PendingTip(product, time) else null
        }
        fun clear(store: SettingsStore) = store.writeInt("pendingProduct", -1)
    }

    fun save(store: SettingsStore) {
        require(product in products && startedAt > 0)
        // Publish the product marker last, after the complete timestamp.
        clear(store)
        store.writeInt("pendingTimeHigh", (startedAt shr 32).toInt())
        store.writeInt("pendingTimeLow", startedAt.toInt())
        store.writeInt("pendingProduct", products.indexOf(product))
    }
}
