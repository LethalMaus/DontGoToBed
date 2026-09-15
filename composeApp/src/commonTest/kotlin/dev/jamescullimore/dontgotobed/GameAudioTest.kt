package dev.jamescullimore.dontgotobed

import kotlin.test.*

class GameAudioTest {
    private class Voice : AudioVoice {
        override var remaining = 20f
        override var volume = 0f
        var playing = false
        var closed = false
        var starts = 0
        override fun play() { playing = true; starts++ }
        override fun pause() { playing = false }
        override fun rewind() { remaining = 20f }
        override fun close() { closed = true; playing = false }
    }
    private class Rig {
        val voices = mutableListOf<Pair<String, Voice>>()
        val audio = GameAudio({ file -> Voice().also { voices.add(file to it) } }, { it })
        fun tick(count: Int = 1) { repeat(count) { audio.tick() } }
        val current get() = voices.last().second
        val track get() = voices.last().first
    }
    @Test fun menuAndPanicLoopAndSearchCyclesThroughAllFourSongs() {
        val r = Rig()
        r.tick(); assertEquals("menu.mp3", r.track)
        r.current.remaining = 0f; r.tick(); assertEquals("menu.mp3", r.track)
        r.audio.scene = MusicScene.Search; r.tick(25)
        for (expected in listOf("search1", "search2", "search3", "search4", "search1")) {
            assertEquals("$expected.mp3", r.track)
            r.current.remaining = 0f; r.tick()
        }
        r.audio.scene = MusicScene.Panic; r.tick(25)
        assertEquals("panic.mp3", r.track)
        r.current.remaining = 0f; r.tick(); assertEquals("panic.mp3", r.track)
        r.audio.scene = MusicScene.Search; r.tick(25)
        assertEquals("search1.mp3", r.track)
    }
    @Test fun transitionsFadeAndCloseTheOldTrack() {
        val r = Rig(); r.tick(25)
        val old = r.current
        assertEquals(.65f, old.volume)
        r.audio.scene = MusicScene.Search; r.tick()
        assertTrue(old.volume < .65f); assertFalse(old.closed)
        r.tick(25); assertTrue(old.closed)
        r.current.remaining = .3f; r.tick()
        assertTrue(r.current.volume <= .3f * .65f)
        r.current.remaining = 0f; r.tick()
        assertTrue(r.current.volume < .1f)
    }
    @Test fun backgroundPausesAndVolumesStayIndependentIncludingMute() {
        val r = Rig(); r.audio.prepareEffects(); r.tick(25)
        r.audio.options = AudioOptions(0, 40); r.tick()
        assertEquals(0f, r.current.volume)
        r.audio.play(SoundEffect.Jump)
        val effect = r.voices.first { it.first == "jump.mp3" }.second
        assertTrue(effect.playing); assertEquals(.4f, effect.volume)
        r.audio.active = false; r.tick()
        assertFalse(r.current.playing); assertFalse(effect.playing)
        val starts = effect.starts
        r.audio.play(SoundEffect.Jump); assertEquals(starts, effect.starts)
        r.audio.active = true; r.tick(); assertTrue(r.current.playing)
        r.audio.close(); assertTrue(r.voices.all { it.second.closed })
    }
    @Test fun volumesPersistAndInvalidValuesAreClamped() {
        val values = mutableMapOf<String, Int>()
        val store = object : SettingsStore {
            override fun readInt(key: String, default: Int) = values[key] ?: default
            override fun writeInt(key: String, value: Int) { values[key] = value }
        }
        assertEquals(AudioOptions(), store.loadAudioOptions())
        store.save(AudioOptions(0, 37)); assertEquals(AudioOptions(0, 37), store.loadAudioOptions())
        values["musicVolume"] = -50; values["effectsVolume"] = 999
        assertEquals(AudioOptions(0, 100), store.loadAudioOptions())
    }
}
