package dev.jamescullimore.dontgotobed

/** Unwrapped cells in the aimed band, nearest first. Empty bands never redirect a swing. */
object MeleeTargeting {
    fun candidates(left: Int, right: Int, bottom: Int, top: Int, facing: Direction): List<Cell> {
        val middle = (bottom + top) / 2
        val sideRows = (bottom..top).sortedBy { kotlin.math.abs(it - middle) }
        val upperColumns = when (facing) {
            Direction.UpRight, Direction.DownRight -> (left..right).reversed()
            else -> (left..right).toList()
        }
        return buildList {
            for (distance in 1..2) {
                when (facing) {
                    Direction.Left -> sideRows.forEach { add(Cell(left - distance, it)) }
                    Direction.Right -> sideRows.forEach { add(Cell(right + distance, it)) }
                    Direction.LeftUp -> add(Cell(left - distance, top))
                    Direction.LeftDown -> add(Cell(left - distance, bottom))
                    Direction.RightUp -> add(Cell(right + distance, top))
                    Direction.RightDown -> add(Cell(right + distance, bottom))
                    Direction.UpLeft, Direction.UpRight -> upperColumns.forEach { add(Cell(it, top + distance)) }
                    Direction.DownLeft, Direction.DownRight -> upperColumns.forEach { add(Cell(it, bottom - distance)) }
                }
            }
        }
    }

    fun select(candidates: List<Cell>, hasTarget: (Cell) -> Boolean): Cell =
        candidates.firstOrNull(hasTarget) ?: candidates.first()
}
