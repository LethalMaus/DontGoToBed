package dev.jamescullimore.dontgotobed

import kotlin.test.*

class ReleasePolishTest {
    @Test fun repeatedPlayerShocksCannotExtendParalysisOrRemoveRecoveryWindow() {
        val game=ShockGame();game.apply("P",1)
        repeat(19) {game.tick(100);game.apply("P",1)}
        assertEquals(100,game.remaining[ShockTarget("P",1)])
        game.tick(100);assertFalse(game.active("P",1))
        repeat(9) {game.apply("P",1);game.tick(100);assertFalse(game.active("P",1))}
        game.apply("P",1);assertFalse(game.active("P",1))
        game.tick(100);game.apply("P",1);assertTrue(game.active("P",1))
    }
    @Test fun recoveryIsIndependentPerPlayerAndClearResetsIt() {
        val game=ShockGame();game.apply("P",1);repeat(20) {game.tick(100)}
        game.apply("P",2);assertTrue(game.active("P",2));assertFalse(game.active("P",1))
        game.clear();game.apply("P",1);assertTrue(game.active("P",1))
    }
    @Test fun attacksRecoverAndRemoteAnimationCannotRemainStuck() {
        val motion=CombatMotion();motion.start(0);assertTrue(motion.active(0))
        motion.tick(239);assertTrue(motion.active(0));motion.tick(1);assertFalse(motion.active(0))
        motion.tick(80);assertTrue(motion.remaining.isEmpty())
        motion.receive(4,99999);assertEquals(320,motion.remaining[4]);motion.tick(320)
        assertFalse(motion.active(4));motion.receive(4,-20);assertTrue(motion.remaining.isEmpty())
    }
    @Test fun glovesBootsAndArmourKeepTheirTradeoffs() {
        var gear=EquipmentLoadout().toggle(ItemType.Rope).toggle(ItemType.Gloves)
        assertEquals(ItemType.Gloves,gear.hand);assertFalse(gear.equipped(ItemType.Rope));assertEquals(2,gear.punchDamage)
        gear=gear.toggle(ItemType.SpeedBoots).toggle(ItemType.JumpBoots)
        assertEquals(1f,gear.speedMultiplier);assertEquals(6f,gear.jumpHeight)
        val equipment=PlayerEquipment();equipment.loadout=gear.copy(armour=true,shield=true)
        equipment.blocking=true;assertEquals(0,equipment.damage(2))
        equipment.blocking=false;assertEquals(1,equipment.damage(2))
        assertEquals(0,equipment.damage(1));equipment.loadout=equipment.loadout.copy(armour=false)
        equipment.loadout=equipment.loadout.copy(armour=true);assertEquals(1,equipment.damage(1))
    }
}
