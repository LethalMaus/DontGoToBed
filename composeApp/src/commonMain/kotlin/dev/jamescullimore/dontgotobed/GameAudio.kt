package dev.jamescullimore.dontgotobed

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dontgotobed.composeapp.generated.resources.Res
import kotlinx.coroutines.delay

internal enum class SoundEffect(val file: String) {
    Jump("jump"), Hit("hit"), Place("place"), Arrow("arrow"), Hurt("damage_taken"), Drink("drink"), Turn("turn"), Purr("cat_purr"), Howl("dog_howl")
}
internal enum class MusicScene(val tracks: List<String>) {
    Menu(listOf("menu")), Search(listOf("search1", "search2", "search3", "search4")), Panic(listOf("panic"))
}
data class AudioOptions(val music: Int = 65, val effects: Int = 80)
fun SettingsStore.loadAudioOptions() = AudioOptions(readInt("musicVolume", 65).coerceIn(0, 100), readInt("effectsVolume", 80).coerceIn(0, 100))
fun SettingsStore.save(options: AudioOptions) {
    writeInt("musicVolume", options.music.coerceIn(0, 100))
    writeInt("effectsVolume", options.effects.coerceIn(0, 100))
}

internal interface AudioVoice {
    val remaining: Float
    var volume: Float
    fun play()
    fun pause()
    fun rewind()
    fun close()
}
@Composable internal expect fun rememberAudioFactory(): (String) -> AudioVoice
internal val LocalGameAudio = staticCompositionLocalOf<GameAudio?> { null }

/** A short fade to silence at each boundary, followed by a fade into the next track. */
internal class GameAudio(private val create: (String) -> AudioVoice,
    private val resolve: (String) -> String = { Res.getUri("files/audio/$it") }) {
    var scene = MusicScene.Menu
    var options = AudioOptions()
    var active = true
    var purrProximity = 0f
    private var purr: AudioVoice? = null
    private var purrGain = 0f
    private var playingScene: MusicScene? = null
    private var index = 0
    private var music: AudioVoice? = null
    private var gain = 0f
    private val effects = mutableMapOf<SoundEffect, List<AudioVoice>>()
    private val nextVoice = mutableMapOf<SoundEffect, Int>()
    private fun voice(file: String) = create(resolve(file))
    fun prepareEffects() {
        SoundEffect.entries.filter { it != SoundEffect.Purr }.forEach { effect ->
            effects[effect] = List(2) { voice("${effect.file}.mp3") }
        }
    }
    fun play(effect: SoundEffect) {
        if (!active || options.effects == 0) return
        val pool = effects[effect] ?: return
        val next = nextVoice[effect] ?: 0
        pool[next].apply { rewind(); volume = options.effects / 100f; play() }
        nextVoice[effect] = (next + 1) % pool.size
    }
    fun tick() {
        val target = if (active) purrProximity.coerceIn(0f,1f) * .24f else 0f
        purrGain += (target-purrGain).coerceIn(-.025f,.025f)
        if (purrGain > .001f && active && options.effects > 0) {
            val sound = purr ?: voice("cat_purr.mp3").also { purr=it }
            if(sound.remaining < .06f) sound.rewind()
            sound.volume = purrGain * options.effects / 100f * (sound.remaining/.15f).coerceIn(0f,1f)
            sound.play()
        } else { purr?.volume=0f; purr?.pause() }
        effects.values.flatten().forEach { it.volume = if (active) options.effects / 100f else 0f }
        if (!active) {
            music?.pause()
            effects.values.flatten().forEach { it.pause() }
            return
        }
        val changing = playingScene != scene
        if (changing && music != null && gain > 0f) {
            gain = (gain - .05f).coerceAtLeast(0f)
            music?.volume = gain * options.music / 100f
            return
        }
        if (changing || music == null || music!!.remaining <= .06f) {
            music?.close()
            index = if (changing) 0 else (index + 1) % scene.tracks.size
            playingScene = scene
            music = voice("${scene.tracks[index]}.mp3")
            gain = 0f
        }
        val current = music ?: return
        gain = minOf(gain + .05f, (current.remaining / 1f).coerceIn(0f, 1f))
        current.volume = gain * options.music / 100f
        current.play()
    }
    fun close() {
        purr?.close(); purr = null
        music?.close(); music = null
        effects.values.flatten().forEach { it.close() }
        effects.clear()
    }
}

@Composable
internal fun rememberGameAudio(options: AudioOptions, active: Boolean): GameAudio {
    val factory = rememberAudioFactory()
    val audio = remember(factory) { GameAudio(factory) }
    SideEffect { audio.options = options; audio.active = active }
    LaunchedEffect(audio) {
        audio.prepareEffects()
        while (true) { audio.tick(); delay(50) }
    }
    DisposableEffect(audio) { onDispose { audio.close() } }
    return audio
}

@Composable
fun AudioSettings(options: AudioOptions, onChange: (AudioOptions) -> Unit) {
    Column {
        Text(tr("Music: {0}%", options.music))
        Slider(options.music.toFloat(), { onChange(options.copy(music = it.toInt())) },
            valueRange = 0f..100f, modifier = Modifier.semantics { contentDescription = tr("Music volume") })
        Text(tr("Sound effects: {0}%", options.effects))
        Slider(options.effects.toFloat(), { onChange(options.copy(effects = it.toInt())) },
            valueRange = 0f..100f, modifier = Modifier.semantics { contentDescription = tr("Sound effects volume") })
        AudioCreditsButton()
    }
}
