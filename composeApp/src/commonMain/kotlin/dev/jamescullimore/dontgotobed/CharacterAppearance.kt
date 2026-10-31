package dev.jamescullimore.dontgotobed

import dontgotobed.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource

/** Cosmetic only: outfits never change a character's stats or abilities. Stable IDs go on the wire. */
enum class PlayerOutfit(val wireId: String, val preferenceId: Int) {
    Original("original", 0), Adventure("adventure", 1), Rugby("rugby", 2),
    Mma("mma", 3), Rock("rock", 4), Trex("trex", 5)
}

internal fun outfitsFor(player: Player): List<PlayerOutfit> = listOf(
    PlayerOutfit.Original, PlayerOutfit.Adventure, PlayerOutfit.Rugby
) + when (player) {
    Player.Papa -> listOf(PlayerOutfit.Mma)
    Player.Leo -> listOf(PlayerOutfit.Rock)
    Player.Ian -> listOf(PlayerOutfit.Trex)
}

internal fun validOutfit(player: Player, outfit: PlayerOutfit): PlayerOutfit =
    outfit.takeIf { it in outfitsFor(player) } ?: PlayerOutfit.Original

internal fun decodePlayerOutfit(value: String?): PlayerOutfit =
    PlayerOutfit.entries.firstOrNull { it.wireId == value } ?: PlayerOutfit.Original

/** Old six-field position packets and unknown future outfits use the original artwork. */
internal fun outfitFromPlayerState(fields: List<String>): PlayerOutfit = decodePlayerOutfit(fields.getOrNull(6))

internal fun outfitLabel(player: Player, outfit: PlayerOutfit): String = when (outfit) {
    PlayerOutfit.Original -> tr("Original")
    PlayerOutfit.Adventure -> when (player) {
        Player.Leo -> tr("Forest")
        Player.Ian -> tr("Sunset")
        Player.Papa -> tr("Ocean")
    }
    PlayerOutfit.Rugby -> tr("Irish rugby")
    PlayerOutfit.Mma -> tr("MMA")
    PlayerOutfit.Rock -> tr("Rock band")
    PlayerOutfit.Trex -> tr("T-rex")
}

internal fun SettingsStore.loadOutfit(player: Player): PlayerOutfit = validOutfit(player,
    PlayerOutfit.entries.firstOrNull { it.preferenceId == readInt("outfit_${player.name}", 0) }
        ?: PlayerOutfit.Original)

internal fun SettingsStore.saveOutfit(player: Player, outfit: PlayerOutfit) =
    writeInt("outfit_${player.name}", validOutfit(player, outfit).preferenceId)

internal fun characterFront(player: Player, outfit: PlayerOutfit = PlayerOutfit.Original): DrawableResource = when (player) {
    Player.Leo -> if (outfit == PlayerOutfit.Adventure) Res.drawable.leo_forest_front else Res.drawable.leo_front
    Player.Ian -> if (outfit == PlayerOutfit.Adventure) Res.drawable.ian_sunset_front else Res.drawable.ian_front
    Player.Papa -> error("Papa uses refreshedCharacterArt for every pose")
}

internal fun characterSide(player: Player, walking: Boolean, outfit: PlayerOutfit = PlayerOutfit.Original): DrawableResource = when (player) {
    Player.Leo -> characterFront(player, outfit)
    Player.Ian -> if (outfit == PlayerOutfit.Adventure) {
        if (walking) Res.drawable.ian_sunset_walk else Res.drawable.ian_sunset_side
    } else if (walking) Res.drawable.ian_walk else Res.drawable.ian_side
    Player.Papa -> if (outfit == PlayerOutfit.Adventure) {
        if (walking) Res.drawable.papa_ocean_walk else Res.drawable.papa_ocean_side
    } else if (walking) Res.drawable.papa_walk else Res.drawable.papa_side
}
