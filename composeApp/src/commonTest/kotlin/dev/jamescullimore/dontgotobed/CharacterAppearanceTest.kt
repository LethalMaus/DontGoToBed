package dev.jamescullimore.dontgotobed

import kotlin.test.*

class CharacterAppearanceTest {
    @Test fun themedOutfitsRoundTripThroughPreferencesAndMultiplayer() {
        val values = mutableMapOf<String, Int>()
        val store = object : SettingsStore {
            override fun readInt(key: String, default: Int) = values[key] ?: default
            override fun writeInt(key: String, value: Int) { values[key] = value }
        }
        for (player in Player.entries) for (outfit in outfitsFor(player)) {
            store.saveOutfit(player, outfit)
            assertEquals(outfit, store.loadOutfit(player))
            assertEquals(outfit, outfitFromPlayerState("0;0;0;true;0;5;${outfit.wireId}".split(';')))
        }
        // Preserve previously saved Original / Adventure values when adding new choices.
        values["outfit_Leo"] = 1
        assertEquals(PlayerOutfit.Adventure, store.loadOutfit(Player.Leo))
        values["outfit_Ian"] = PlayerOutfit.Mma.preferenceId
        assertEquals(PlayerOutfit.Original, store.loadOutfit(Player.Ian))
        assertEquals(PlayerOutfit.Original, validOutfit(Player.Papa, PlayerOutfit.Rock))
    }

    @Test fun everySelectableThemedOutfitHasThreeDistinctAtlasFrames() {
        for (player in Player.entries) for (outfit in outfitsFor(player)) {
            if (outfit == PlayerOutfit.Original || outfit == PlayerOutfit.Adventure) continue
            val sheet = assertNotNull(outfitSheet(player, outfit))
            assertEquals(3, sheet.frames.distinct().size)
            sheet.frames.forEach { frame ->
                assertTrue(frame.x >= 0 && frame.y >= 0)
                assertTrue(frame.width > 0 && frame.height > 0)
                assertTrue(frame.x + frame.width <= 2172 && frame.y + frame.height <= 724)
            }
            assertEquals(1, sheet.frames.map { it.y + it.height }.distinct().size)
        }
        assertNull(outfitSheet(Player.Ian, PlayerOutfit.Rock))
    }

    @Test fun oldAndUnknownMultiplayerOutfitsKeepOriginalArt() {
        val legacy = "10000;10000;40000;true;1;5".split(';')
        assertEquals(PlayerOutfit.Original, outfitFromPlayerState(legacy))
        assertEquals(PlayerOutfit.Adventure, outfitFromPlayerState(legacy + "adventure"))
        assertEquals(PlayerOutfit.Original, outfitFromPlayerState(legacy + "unrecognised"))
        assertEquals(PlayerOutfit.Original, outfitFromPlayerState(emptyList()))
    }
    @Test fun outfitsAreSavedIndependentlyForEachCharacter() {
        val values = mutableMapOf<String, Int>()
        val store = object : SettingsStore {
            override fun readInt(key: String, default: Int) = values[key] ?: default
            override fun writeInt(key: String, value: Int) { values[key] = value }
        }
        store.saveOutfit(Player.Leo, PlayerOutfit.Adventure)
        store.saveOutfit(Player.Papa, PlayerOutfit.Adventure)
        assertEquals(PlayerOutfit.Adventure, store.loadOutfit(Player.Leo))
        assertEquals(PlayerOutfit.Original, store.loadOutfit(Player.Ian))
        store.saveOutfit(Player.Leo, PlayerOutfit.Original)
        assertEquals(PlayerOutfit.Original, store.loadOutfit(Player.Leo))
        assertEquals(PlayerOutfit.Adventure, store.loadOutfit(Player.Papa))
        values["outfit_Ian"] = Int.MAX_VALUE
        assertEquals(PlayerOutfit.Original, store.loadOutfit(Player.Ian))
    }
}
