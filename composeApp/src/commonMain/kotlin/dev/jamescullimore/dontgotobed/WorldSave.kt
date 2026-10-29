package dev.jamescullimore.dontgotobed

import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface WorldSaveFiles {
    fun read(backup:Boolean=false):String?
    /** Atomically replace the current file; retain the previous valid contents separately. */
    fun write(text:String,previous:String?)
}
@Composable internal expect fun rememberWorldSaveFiles(slot:Int):WorldSaveFiles

internal data class SavedWorld(val config:WorldConfig,val position:RopePoint,val axis:WorldAxis,val hp:Int,
    val inventory:List<InventorySlot>,val equipment:EquipmentLoadout,val dog:PetDog?,val blocks:List<Block>)

internal object WorldSaveCodec {
    private fun checksum(s:String):String { var h=2166136261u;for(c in s) h=(h xor c.code.toUInt())*16777619u;return h.toString(16) }
    fun encode(s:SavedWorld):String {
        val c=s.config
        val body=listOf("DGTB_WORLD_2",listOf(c.horizontalSize,c.height,c.initialZombies,c.initialSkeletons,c.nearbyEnemyRows,c.nearbyTerrainRows,if(c.unlimited)1 else 0,c.portalSeed).joinToString(","),
            s.position.encode(),"${s.axis.ordinal},${s.hp}",s.inventory.joinToString("/") { "${it.type?.name ?: "_"},${it.count},${it.material?.name ?: "_"}" },s.equipment.encode(),
            s.dog?.let { "${it.point.encode()},${if(it.owner>=0)1 else 0}" } ?: "_",WorldReplication.encodeBlocks(s.blocks.map { it.id to it })).joinToString("\n")
        return checksum(body)+"\n"+body
    }
    fun decode(text:String):SavedWorld {
        require(text.length in 1..64_000_000)
        val split=text.indexOf('\n');require(split>0);val body=text.substring(split+1)
        require(text.substring(0,split)==checksum(body))
        val p=body.split('\n');require(p.size==8 && p[0] in listOf("DGTB_WORLD_1","DGTB_WORLD_2"))
        val c=p[1].split(',').map(String::toInt);require(c.size==(if(p[0]=="DGTB_WORLD_1")7 else 8) && c[0]==ExplorationWorld.SIZE && c[1]==54 && c[2] in 0..12 && c[3] in 0..12 && c[4] in 0..2 && c[5] in 0..2 && c[6] in 0..1)
        val config=WorldConfig(c[0],c[1],c[2],c[3],c[4],c[5],true,c[6]==1,c.getOrElse(7){0})
        fun point(s:String)=RopePoint.decode(s).also { require(it.lat.isFinite() && it.lon.isFinite() && it.row.isFinite() && it.lat in 0f..<c[0].toFloat() && it.lon in 0f..<c[0].toFloat() && it.row in 0f..(c[1]+10).toFloat()) }
        val position=point(p[2]);val a=p[3].split(',').map(String::toInt);require(a.size==2 && a[0] in 0..1 && a[1] in 0..5)
        val bag=p[4].split('/').map { line -> val f=line.split(',');require(f.size==3);val type=if(f[0]=="_")null else ItemType.valueOf(f[0]);val material=if(f[2]=="_")null else BlockMaterial.valueOf(f[2]);val count=f[1].toInt();require(count in 0..20 && (type!=null || count==0) && ((type==ItemType.Block)==(material!=null)) && (type?.isEquipment!=true || count<=1));InventorySlot(type,count,material) }
        require(bag.size==14)
        val dog=if(p[6]=="_")null else p[6].split(',').let { require(it.size==4 && it[3] in listOf("0","1"));PetDog(point(it.take(3).joinToString(",")),if(it[3]=="1")0 else -1) }
        val blocks=WorldReplication.decodeBlocks(p[7],c[0],c[1],c[0]).map { requireNotNull(it.second) };require(blocks.size<=400_000 && blocks.map { it.id }.toSet().size==blocks.size)
        return SavedWorld(config,position,WorldAxis.entries[a[0]],a[1],bag,EquipmentLoadout.decode(p[5]),dog,blocks)
    }
}

internal class WorldSaveRepository(private val files:WorldSaveFiles) {
    private var lastGood:String?=null
    fun exists()=files.read()!=null || files.read(true)!=null
    fun load():SavedWorld {
        for(backup in listOf(false,true)) {
            val result=runCatching { val text=requireNotNull(files.read(backup));WorldSaveCodec.decode(text).also { lastGood=text } }
            if(result.isSuccess)return result.getOrThrow()
        }
        error("No readable saved world")
    }
    fun save(snapshot:SavedWorld) {
        if(lastGood==null)runCatching { load() }
        val text=WorldSaveCodec.encode(snapshot)
        files.write(text,lastGood)
        lastGood=text
    }
}

/** A process-lifetime writer survives screen disposal; snapshots contain no mutable game entities. */
internal class WorldSaveController(private val files:WorldSaveFiles) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private val lock=Mutex()
    private val repository=WorldSaveRepository(files)
    private var nextVersion=0L
    private var writtenVersion=0L
    var status by mutableStateOf("");private set
    suspend fun exists()=withContext(Dispatchers.Default) { lock.withLock { repository.exists() } }
    suspend fun load():SavedWorld=withContext(Dispatchers.Default) { lock.withLock { repository.load() } }
    var capture:(()->SavedWorld?)?=null
    fun saveNow():Job? = capture?.invoke()?.let(::save)
    fun save(snapshot:SavedWorld):Job {
        val version=++nextVersion
        status="Saving world…"
        return scope.launch {
            val result=runCatching { lock.withLock {
                if(version>writtenVersion) { repository.save(snapshot);writtenVersion=version }
            } }
            withContext(Dispatchers.Main) { if(version==nextVersion) status=if(result.isSuccess) "World saved" else "Could not save world. Please try again." }
        }
    }
}
