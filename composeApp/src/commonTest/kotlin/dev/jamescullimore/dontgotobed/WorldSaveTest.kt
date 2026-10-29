package dev.jamescullimore.dontgotobed
import kotlin.test.*

class WorldSaveTest {
    private fun sample():SavedWorld {
        val bag=PlayerInventory(14);bag.add(ItemType.Block,12,BlockMaterial.Wood);bag.add(ItemType.Map);bag.add(ItemType.Gloves)
        val map=TileMap(576,54,exploration=true);map.setActiveSlice(WorldAxis.Longitude,100,60)
        map.placePiece(60,6,BlockMaterial.Wood,BlockShape.Square)
        return SavedWorld(WorldConfig(horizontalSize=576,exploration=true,unlimited=true),RopePoint(100f,60f,9f),WorldAxis.Longitude,4,bag.slots.toList(),EquipmentLoadout(hand=ItemType.Gloves),PetDog(RopePoint(99f,60f,9f),0),map.networkBlocks())
    }
    @Test fun roundTripPreservesWorldEditsHostInventoryEquipmentAndGrace() {
        val source=sample();val decoded=WorldSaveCodec.decode(WorldSaveCodec.encode(source))
        assertEquals(source,decoded)
        val map=TileMap(576,54,exploration=true)
        map.applyNetworkBlocks(decoded.blocks.map { it.id to it },true)
        assertTrue(map.getVoxel(100,60,6));assertFalse(map.getVoxel(100,90,6))
        val bag=PlayerInventory(14);bag.restore(decoded.inventory);assertEquals(source.inventory,bag.slots.toList())
    }
    @Test fun portalSeedPersistsAndLegacySavesKeepTheirLayout() {
        val source=sample().let { it.copy(config=it.config.copy(portalSeed=7123)) }
        val decoded=WorldSaveCodec.decode(WorldSaveCodec.encode(source))
        assertEquals(source.config,decoded.config)
        assertEquals(ExplorationWorld.portals(7123),ExplorationWorld.portals(decoded.config.portalSeed))
        val lines=WorldSaveCodec.encode(sample()).substringAfter('\n').split('\n').toMutableList()
        lines[0]="DGTB_WORLD_1";lines[1]=lines[1].substringBeforeLast(',')
        val body=lines.joinToString("\n")
        var hash=2166136261u;for(c in body) hash=(hash xor c.code.toUInt())*16777619u
        assertEquals(sample(),WorldSaveCodec.decode(hash.toString(16)+"\n"+body))
    }

    @Test fun damageRemovalAndPlacementInvalidateCachedTerrainEvenThroughViews() {
        val map=TileMap(48,12);val before=map.saveRevision
        val id=map.placePiece(5,3,BlockMaterial.Wood,BlockShape.Single);assertTrue(map.saveRevision>before)
        val view=map.collisionView();val after=map.saveRevision;view.setHealth(5,3,1);assertTrue(map.saveRevision>after)
        val damaged=map.saveRevision;map.removeBlock(id);assertTrue(map.saveRevision>damaged)
        assertTrue(map.networkBlocks().isEmpty())
    }
    @Test fun incrementalCaptureDoesNotMutatePreviousSnapshotOrMissMining() {
        val map=TileMap(48,12)
        val id=map.placePiece(5,3,BlockMaterial.Wood,BlockShape.Single)
        val first=map.captureSaveBlocks();val original=first.single().health
        map.setHealth(5,3,1);val second=map.captureSaveBlocks()
        assertEquals(original,first.single().health);assertEquals(1,second.single().health)
        map.removeBlock(id);assertTrue(map.captureSaveBlocks().isEmpty())
    }

    @Test fun truncatedCorruptAndFutureFilesAreRejected() {
        val text=WorldSaveCodec.encode(sample())
        assertFails { WorldSaveCodec.decode(text.dropLast(4)) }
        assertFails { WorldSaveCodec.decode(text.replace("DGTB_WORLD_2","DGTB_WORLD_3")) }
        assertFails { WorldSaveCodec.decode("nonsense") }
    }
    @Test fun damagedPrimaryFallsBackAndFailedWriteKeepsLastGoodSave() {
        val files=object:WorldSaveFiles {
            var current:String?=null;var previous:String?=null;var fail=false
            override fun read(backup:Boolean)=if(backup)previous else current
            override fun write(text:String,previous:String?) { if(fail)error("Disk full");this.previous=previous;current=text }
        }
        val repo=WorldSaveRepository(files);val first=sample();repo.save(first)
        val second=first.copy(hp=2);repo.save(second)
        files.current="interrupted write"
        assertEquals(first,WorldSaveRepository(files).load())
        files.current=WorldSaveCodec.encode(second);files.fail=true
        assertFails { repo.save(first.copy(hp=1)) }
        assertEquals(second,WorldSaveRepository(files).load())
    }

}
