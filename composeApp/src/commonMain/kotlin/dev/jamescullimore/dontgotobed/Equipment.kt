package dev.jamescullimore.dontgotobed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

internal val starterEquipment = listOf(ItemType.Rope, ItemType.Gloves, ItemType.SpeedBoots, ItemType.JumpBoots, ItemType.Shield, ItemType.Armour)
internal val ItemType.isEquipment get() = this in starterEquipment
internal enum class ArmourColour(val tint: Color) { Blue(Color(0xFF368BDD)), Green(Color(0xFF63A05D)), Red(Color(0xFFC95454)) }
internal data class EquipmentLoadout(
    val hand: ItemType? = null, val boots: ItemType? = null, val shield: Boolean = false,
    val armour: Boolean = false, val colour: ArmourColour = ArmourColour.Blue
) {
    val speedMultiplier get() = if (boots == ItemType.SpeedBoots) 1.3f else 1f
    val jumpHeight get() = if (boots == ItemType.JumpBoots) 6f else 4f
    val punchDamage get() = if (hand == ItemType.Gloves) 2 else 1
    fun toggle(item: ItemType): EquipmentLoadout = when (item) {
        ItemType.Rope, ItemType.Gloves -> copy(hand = if (hand == item) null else item)
        ItemType.SpeedBoots, ItemType.JumpBoots -> copy(boots = if (boots == item) null else item)
        ItemType.Shield -> copy(shield = !shield)
        ItemType.Armour -> copy(armour = !armour)
        else -> this
    }
    fun equipped(item: ItemType) = hand == item || boots == item || (item == ItemType.Shield && shield) || (item == ItemType.Armour && armour)
    fun encode() = "${hand?.name ?: "_"},${boots?.name ?: "_"},$shield,$armour,${colour.ordinal}"
    companion object {
        fun decode(value: String?): EquipmentLoadout = runCatching {
            val p = value!!.split(',')
            EquipmentLoadout(ItemType.entries.firstOrNull { it.name == p[0] && it in listOf(ItemType.Rope, ItemType.Gloves) },
                ItemType.entries.firstOrNull { it.name == p[1] && it in listOf(ItemType.SpeedBoots, ItemType.JumpBoots) },
                p[2].toBooleanStrict(), p[3].toBooleanStrict(), ArmourColour.entries[p[4].toInt()])
        }.getOrDefault(EquipmentLoadout())
    }
}
internal class PlayerEquipment {
    var loadout by mutableStateOf(EquipmentLoadout())
    var blocking by mutableStateOf(false)
    private var armourHits = 0
    fun damage(raw: Int): Int {
        if (raw <= 0 || (loadout.shield && blocking)) return 0
        if (!loadout.armour) return raw
        // Armour absorbs every other damage point; toggling it never restores protection.
        val total = armourHits + raw
        val taken = total / 2 - armourHits / 2
        armourHits = total
        return taken
    }
}
internal fun PlayerInventory.addStarterEquipment() { starterEquipment.forEach { add(it) } }
internal fun equipmentDescription(item: ItemType) = when (item) {
    ItemType.Rope -> tr("Equip, then tap a nearby cat, player or enemy. Release whenever you like.")
    ItemType.Gloves -> tr("Two damage per punch. Replaces the rope in your hand slot.")
    ItemType.SpeedBoots -> tr("Run 30% faster. Replaces jump boots.")
    ItemType.JumpBoots -> tr("Jump six blocks high. Replaces speed boots.")
    ItemType.Shield -> tr("Hold Block to stop incoming attacks and arrows.")
    ItemType.Armour -> tr("Absorbs every other damage point. Choose your armour colour below.")
    else -> ""
}

internal fun inventoryHelp(item: ItemType): String = when(item) {
    ItemType.Arrow -> tr("Close the bag, then tap the world to shoot. Each shot uses one arrow.")
    ItemType.ElectricArrow -> tr("Close the bag, then tap to shoot. Deals one damage and paralyzes for two seconds.")
    ItemType.Potion -> tr("Close the bag and press Use to restore health.")
    ItemType.Map -> tr("Close the bag and use the map to see the world overview.")
    ItemType.Block -> tr("Close the bag and press Place to build. Long-press a material in the toolbar to choose its shape.")
    else -> equipmentDescription(item)
}
