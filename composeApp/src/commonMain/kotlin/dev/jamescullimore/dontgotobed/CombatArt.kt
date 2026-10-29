package dev.jamescullimore.dontgotobed

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.painter.Painter
import dontgotobed.composeapp.generated.resources.*

/** Cosmetic only: the authoritative hit is still applied by the existing combat action. */
internal class CombatMotion {
    val remaining=mutableStateMapOf<Int,Int>()
    fun start(id: Int) { remaining[id]=320 }
    fun receive(id: Int,time: Int) { if(time>0) remaining[id]=time.coerceAtMost(320) else remaining.remove(id) }
    fun tick(ms: Int) { remaining.toMap().forEach { (id,t)-> if(t<=ms) remaining.remove(id) else remaining[id]=t-ms } }
    fun active(id: Int)=(remaining[id] ?: 0)>80
}

@Composable
internal fun attackPlayerPainter(player: Player,outfit: PlayerOutfit): Painter {
    val refreshed = refreshedCharacterArt(player, outfit)
    if (refreshed != null) return atlasPainter(refreshed.resource, refreshed.frames[3])
    val index=when(validOutfit(player,outfit)) { PlayerOutfit.Original->0;PlayerOutfit.Adventure->1;PlayerOutfit.Rugby->2;else->3 }
    val frames=when(player) {
        Player.Leo->listOf(OutfitFrame(25,13,575,599),OutfitFrame(651,13,576,599),OutfitFrame(94,648,537,591),OutfitFrame(721,639,523,594))
        Player.Papa->listOf(OutfitFrame(35,18,585,599),OutfitFrame(656,18,582,599),OutfitFrame(35,634,588,619),OutfitFrame(656,634,573,609))
        Player.Ian->listOf(OutfitFrame(101,35,519,551),OutfitFrame(700,35,516,552),OutfitFrame(101,624,540,544),OutfitFrame(698,604,565,566))
    }
    return atlasPainter(when(player) { Player.Leo->Res.drawable.leo_attack;Player.Papa->Res.drawable.papa_attack;Player.Ian->Res.drawable.ian_attack },frames[index])
}

@Composable
internal fun attackEnemyPainter(skeleton: Boolean): Painter = atlasPainter(Res.drawable.enemies_attack,
    if(skeleton) OutfitFrame(1120,156,636,649) else OutfitFrame(52,156,638,649))
