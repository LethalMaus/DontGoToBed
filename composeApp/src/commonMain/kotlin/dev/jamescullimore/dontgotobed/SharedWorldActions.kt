package dev.jamescullimore.dontgotobed

import kotlin.math.abs
import kotlin.math.floor

/** Reliable command receipts prevent retries from placing, collecting or rewarding twice. */
class ActionReceipts {
    private val receipts = mutableMapOf<Long, String>()
    fun resolve(id: Long, apply: () -> String): String = receipts.getOrPut(id, apply)
}

/** A client action is expressed in world space, independent of the host's camera plane. */
data class WorldAction(
    val id: Long, val verb: String, val axis: WorldAxis,
    val latitude: Int, val longitude: Int, val height: Int, val arguments: List<String>
) {
    val col get() = (if (axis == WorldAxis.Latitude) latitude else longitude) / 1000
    val row get() = height / 1000
    fun encode() = (listOf("ACTION", id, verb, axis.ordinal, latitude, longitude, height) + arguments).joinToString(";")
    fun nearKnownPosition(position: WorldLocation, map: TileMap): Boolean {
        fun distance(a: Int, b: Int): Int { val d = abs(map.wrap(a) - map.wrap(b)); return minOf(d, map.width - d) }
        return distance(latitude / 1000, position.latitude) <= 5 && distance(longitude / 1000, position.longitude) <= 5 && abs(row - position.row) <= 8
    }
    fun nearTarget(col: Int, row: Int, map: TileMap): Boolean {
        val d = abs(map.wrap(this.col) - map.wrap(col))
        return minOf(d, map.width - d) <= 6 && abs(this.row - row) <= 6
    }
    companion object {
        fun decode(payload: String): WorldAction {
            val p = payload.split(';')
            require(p.size >= 7 && p[0] == "ACTION")
            return WorldAction(p[1].toLong(), p[2], WorldAxis.entries[p[3].toInt()], p[4].toInt(), p[5].toInt(), p[6].toInt(), p.drop(7))
        }
    }
}

/** Executes shared-world commands once, on the host, using the requesting player's plane. */
class SharedWorldAuthority(
    private val tileMap: TileMap,
    private val vm: GameViewModel,
    private val unitPx: Float,
    private val playerWidthPx: Float,
    private val playerHeightPx: Float,
    private val activeWorldAxis: WorldAxis,
    private val knownPosition: (Int) -> WorldLocation?,
    private val positions: () -> List<WorldLocation>,
    private val bed: () -> HuntBed? = { null },
    private val hitBed: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    private val onWorldChanged: () -> Unit = {}
) {
    fun apply(actor: Int, action: WorldAction): String {
        val known = knownPosition(actor) ?: return "false;_;_"
        if (!action.nearKnownPosition(known, tileMap)) return "false;_;_"
        val view = tileMap.collisionView().apply {
            setActiveSlice(action.axis, action.latitude / 1000, action.longitude / 1000)
        }
        val args = action.arguments
        fun success(type: ItemType? = null, material: BlockMaterial? = null) = "true;${type?.name ?: "_"};${material?.name ?: "_"}"
        fun entities() = vm.worldEntities(activeWorldAxis, unitPx)
        fun occupies(e: NetworkEntity, col: Int, row: Int): Boolean =
            view.wrap(e.other(action.axis) / 1000) == view.activeSlice &&
                (e.along(action.axis) / 1000..floor(e.along(action.axis) / 1000f + 1.999f).toInt()).any { view.wrap(it) == view.wrap(col) } &&
                row in e.height / 1000 until e.height / 1000 + 3
        when (action.verb) {
            "TAKE" -> {
                val id = args[0].toLong()
                val e = entities().firstOrNull { it.kind == "P" && it.id == id } ?: return "false;_;_"
                val col = e.along(action.axis) / 1000
                val row = e.height / 1000
                if (view.wrap(e.other(action.axis) / 1000) != view.activeSlice ||
                    (0..2).none { view.wrap(action.col + it) == col } || row !in action.row - 1..action.row + 3) return "false;_;_"
                vm.removePotionSnapshot(id)
                return success(e.item)
            }
            "FIRE" -> {
                vm.fireRemoteArrow(action, activeWorldAxis, args[0].toInt() / 1000f * unitPx,
                    args[1].toInt() / 1000f * unitPx, unitPx, playerWidthPx, playerHeightPx, tileMap.width * unitPx)
                return success()
            }
            "HIT", "PLACE" -> {
                val col = args[0].toInt()
                val row = args[1].toInt()
                if (!action.nearTarget(col, row, tileMap) || !tileMap.inY(row)) return "false;_;_"
                if (action.verb == "HIT") {
                    val direction = Direction.entries[args[2].toInt()]
                    val x = (if (action.axis == WorldAxis.Latitude) action.latitude else action.longitude) / 1000f
                    val y = action.height / 1000f
                    val candidates = MeleeTargeting.candidates(floor(x).toInt(), floor(x + 1.999f).toInt(), floor(y).toInt(), floor(y + 2.999f).toInt(), direction)
                    if (candidates.none { view.wrap(it.c) == view.wrap(col) && it.r == row }) return "false;_;_"
                    val lat = if (action.axis == WorldAxis.Latitude) col else view.activeSlice
                    val lon = if (action.axis == WorldAxis.Longitude) col else view.activeSlice
                    if (bed()?.contains(tileMap, lat, lon, row) == true) { hitBed(lat, lon, row, actor); return success() }
                    view.damagePieceAt(col, row, 10)?.let { damage ->
                        onWorldChanged()
                        return if (damage.destroyed) success(ItemType.Block, damage.material) else success()
                    }
                    val enemy = entities().firstOrNull { it.kind in listOf("Z", "S") && occupies(it, col, row) } ?: return "false;_;_"
                    if (enemy.kind == "Z") vm.damageZombie(enemy.id, unitPx) else vm.damageSkeleton(enemy.id, unitPx)
                    return success()
                }
                val material = BlockMaterial.entries[args[2].toInt()]
                val shape = BlockShape.entries[args[3].toInt()]
                // Check every player's volume, including the host, on this world plane.
                val positions = positions()
                if (positions.any { p -> p.intersects(action.axis, view.activeSlice, view.width) &&
                    (0 until shape.width).any { dx -> (0..1).any { view.wrap(col + dx) == view.wrap(p.along(action.axis) + it) } } &&
                    row < p.row + 3 && row + shape.height > p.row }) return "false;_;_"
                if (view.placePiece(col, row, material, shape) < 0) return "false;_;_"
                onWorldChanged()
                return success()
            }
        }
        return "false;_;_"
    }

}
