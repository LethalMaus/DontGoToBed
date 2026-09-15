package dev.jamescullimore.dontgotobed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.darwin.NSObject
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSURL

@Composable
internal actual fun rememberAudioFactory(): (String) -> AudioVoice = remember { { uri ->
    val player = AVAudioPlayer(contentsOfURL = NSURL.URLWithString(uri)!!, error = null)
    var finished = false
    val completion = object : NSObject(), AVAudioPlayerDelegateProtocol {
        override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) { finished = true }
    }
    player.delegate = completion
    player.prepareToPlay()
    player.volume = 0f
    object : AudioVoice {
        private val retainedDelegate = completion
        override val remaining get() = if (finished) 0f else (player.duration - player.currentTime).toFloat()
        override var volume: Float
            get() = player.volume
            set(value) { player.volume = value }
        override fun play() { if (!player.playing) player.play() }
        override fun pause() { player.pause() }
        override fun rewind() { finished = false; player.currentTime = 0.0 }
        override fun close() { player.stop(); if (player.delegate === retainedDelegate) player.delegate = null }
    }
} }
