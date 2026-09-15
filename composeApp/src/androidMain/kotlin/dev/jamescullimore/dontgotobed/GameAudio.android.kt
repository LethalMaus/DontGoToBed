package dev.jamescullimore.dontgotobed

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberAudioFactory(): (String) -> AudioVoice {
    val context = LocalContext.current.applicationContext
    return remember(context) { { uri ->
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            context.assets.openFd(uri.removePrefix("file:///android_asset/")).use { player.setDataSource(it) }
            player.prepare()
            player.setVolume(0f, 0f)
        } catch (error: Exception) { player.release(); throw error }
        object : AudioVoice {
            override val remaining get() = (player.duration - player.currentPosition) / 1000f
            override var volume = 0f
                set(value) { field = value; player.setVolume(value, value) }
            override fun play() { if (!player.isPlaying) player.start() }
            override fun pause() { if (player.isPlaying) player.pause() }
            override fun rewind() { player.seekTo(0) }
            override fun close() { player.release() }
        }
    } }
}
