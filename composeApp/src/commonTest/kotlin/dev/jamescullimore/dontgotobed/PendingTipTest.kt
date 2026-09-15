package dev.jamescullimore.dontgotobed

import kotlin.test.*

class PendingTipTest {
    private class MemoryStore : SettingsStore {
        val values = mutableMapOf<String, Int>()
        override fun readInt(key: String, default: Int) = values[key] ?: default
        override fun writeInt(key: String, value: Int) { values[key] = value }
    }

    @Test fun interruptedPaymentSurvivesNewReaderWithoutLosingTimestamp() {
        val store = MemoryStore()
        val original = PendingTip("tip_medium", 1_800_123_456_789L)
        original.save(store)
        assertEquals(original, PendingTip.read(store))
    }

    @Test fun clearingReminderDoesNotRestoreOldPayment() {
        val store = MemoryStore()
        PendingTip("tip_small", 1_800_123_456_789L).save(store)
        PendingTip.clear(store)
        assertNull(PendingTip.read(store))
    }

    @Test fun partialOrCorruptMarkerDoesNotBlockPurchasing() {
        val store = MemoryStore()
        store.values["pendingTimeLow"] = 100
        assertNull(PendingTip.read(store))
        store.values["pendingProduct"] = 99
        assertNull(PendingTip.read(store))
        store.values["pendingProduct"] = 0
        store.values["pendingTimeLow"] = 0
        assertNull(PendingTip.read(store))
    }
}
