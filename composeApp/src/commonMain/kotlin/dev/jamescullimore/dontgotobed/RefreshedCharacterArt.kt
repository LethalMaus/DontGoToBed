package dev.jamescullimore.dontgotobed

import dontgotobed.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource

/** Approved pixel artwork. Coordinates refer to the unmodified generated atlases. */
internal data class RefreshedCharacterArt(val resource: DrawableResource, val frames: List<OutfitFrame>, val gripX: Float, val gripY: Float)

private val refreshedCharacters = mapOf(
    (Player.Leo to PlayerOutfit.Original) to RefreshedCharacterArt(Res.drawable.leo_original_poses_v2, listOf(OutfitFrame(126,13,297,489), OutfitFrame(622,13,246,489), OutfitFrame(1061,13,289,489), OutfitFrame(49,515,430,473), OutfitFrame(574,513,379,480), OutfitFrame(1150,508,267,496)), 1384f, 636f),
    (Player.Leo to PlayerOutfit.Adventure) to RefreshedCharacterArt(Res.drawable.leo_adventure_poses_v2, listOf(OutfitFrame(121,13,309,490), OutfitFrame(629,13,245,490), OutfitFrame(1114,13,261,490), OutfitFrame(38,536,423,458), OutfitFrame(567,522,396,476), OutfitFrame(1128,519,316,482)), 1356f, 605f),
    (Player.Leo to PlayerOutfit.Rugby) to RefreshedCharacterArt(Res.drawable.leo_rugby_poses_v1, listOf(OutfitFrame(150,14,260,496), OutfitFrame(614,14,263,496), OutfitFrame(1129,14,284,496), OutfitFrame(76,530,417,472), OutfitFrame(577,525,388,473), OutfitFrame(1146,519,281,489)), 1393f, 652f),
    (Player.Leo to PlayerOutfit.Rock) to RefreshedCharacterArt(Res.drawable.leo_rock_poses_v2, listOf(OutfitFrame(88,6,434,499), OutfitFrame(621,6,279,499), OutfitFrame(1101,6,299,499), OutfitFrame(28,520,480,481), OutfitFrame(584,518,386,483), OutfitFrame(1101,509,293,500)), 1357f, 632f),
    (Player.Papa to PlayerOutfit.Original) to RefreshedCharacterArt(Res.drawable.papa_original_marketing_v1, listOf(OutfitFrame(127,31,302,498), OutfitFrame(668,31,180,498), OutfitFrame(1096,31,341,498), OutfitFrame(37,547,458,447), OutfitFrame(581,533,383,466), OutfitFrame(1186,544,231,455)), 1365f, 576f),
    (Player.Papa to PlayerOutfit.Adventure) to RefreshedCharacterArt(Res.drawable.papa_adventure_marketing_v1, listOf(OutfitFrame(110,15,295,518), OutfitFrame(666,15,200,518), OutfitFrame(1102,15,356,517), OutfitFrame(41,541,452,459), OutfitFrame(566,546,401,455), OutfitFrame(1198,533,251,479)), 1390f, 574f),
    (Player.Papa to PlayerOutfit.Rugby) to RefreshedCharacterArt(Res.drawable.papa_rugby_marketing_v1, listOf(OutfitFrame(158,10,306,501), OutfitFrame(640,10,276,501), OutfitFrame(1093,10,335,501), OutfitFrame(63,529,449,469), OutfitFrame(512,526,473,472), OutfitFrame(1183,526,226,482)), 1350f, 568f),
    (Player.Papa to PlayerOutfit.Mma) to RefreshedCharacterArt(Res.drawable.papa_mma_marketing_v1, listOf(OutfitFrame(129,19,311,493), OutfitFrame(685,19,186,493), OutfitFrame(1117,19,319,493), OutfitFrame(60,528,429,453), OutfitFrame(572,529,390,455), OutfitFrame(1182,527,243,467)), 1395f, 570f),
)

internal fun refreshedCharacterArt(player: Player, outfit: PlayerOutfit): RefreshedCharacterArt? =
    refreshedCharacters[player to validOutfit(player, outfit)]
