package dev.jamescullimore.dontgotobed

import kotlin.test.*

class StartMenuStateTest {
    @Test fun joiningCannotAccidentallyLaunchASoloWorld() {
        assertFalse(canStartSession(SessionChoice.Join, false, false))
        assertFalse(canStartSession(SessionChoice.Join, true, false))
        assertFalse(canStartSession(SessionChoice.Join, false, true))
        assertTrue(canStartSession(SessionChoice.Join, true, true))
        assertTrue(canStartSession(SessionChoice.Host, true, false))
        assertFalse(canStartSession(SessionChoice.Host, false, false))
        assertFalse(canStartSession(SessionChoice.Solo, true, true))
        assertTrue(canStartSession(SessionChoice.Solo, false, false))
    }

    @Test fun leavingMultiplayerClearsConnectionButKeepsCharacterAndAddress() {
        val state = StartMenuState(Player.Papa)
        state.session = SessionChoice.Join
        state.joinIp = "192.168.1.3"
        state.connected = true
        state.connecting = true
        state.peer = "Ian"
        state.error = "Old error"
        state.resetConnection()
        assertEquals(SessionChoice.Solo, state.session)
        assertFalse(state.connected)
        assertFalse(state.connecting)
        assertNull(state.peer)
        assertNull(state.error)
        assertEquals(Player.Papa, state.player)
        assertEquals("192.168.1.3", state.joinIp)
    }

    @Test fun characterPreferenceSurvivesRestartAndInvalidValuesFallBack() {
        val values = mutableMapOf<String, Int>()
        val store = object : SettingsStore {
            override fun readInt(key: String, default: Int) = values[key] ?: default
            override fun writeInt(key: String, value: Int) { values[key] = value }
        }
        store.saveSelectedPlayer(Player.Ian)
        assertEquals(Player.Ian, store.loadSelectedPlayer())
        values["selectedPlayer"] = Int.MAX_VALUE
        assertEquals(Player.Leo, store.loadSelectedPlayer())
        values["selectedPlayer"] = -1
        assertEquals(Player.Leo, store.loadSelectedPlayer())
    }
}
