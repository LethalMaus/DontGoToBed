package dev.jamescullimore.dontgotobed

import kotlin.test.*
import kotlin.time.TimeSource

class CatRoutePerformanceTest {
    @Test fun representativeRoutes() {
        val map = TileMap(192, 54).also { it.seedGroundBand(); it.seedAdventureTerrain() }
        val game = CompanionGame(map)
        val cases = listOf(
            RopePoint(3f, 7f, 3f) to RopePoint(100f, 90f, 3f),
            RopePoint(188f, 190f, 3f) to RopePoint(4f, 2f, 3f),
            RopePoint(50f, 50f, 3f) to RopePoint(65f, 68f, 3f),
            RopePoint(3f, 7f, 3f) to RopePoint(90f, 90f, 3f)
        )
        repeat(3) { run ->
            cases.forEachIndexed { index, (start, goal) ->
                val clock = TimeSource.Monotonic.markNow()
                val route = game.findRoute(start, goal)
                val elapsed = clock.elapsedNow().inWholeMicroseconds
                var digest = 0L
                route.forEach { digest = ((digest * 31 + it.lat.toInt()) * 31 + it.lon.toInt()) * 31 + it.row.toInt() }
                val expected = listOf(0 to 0L, 12 to -6720799879813178335L, 33 to -317076277828852702L, 0 to 0L)
                assertEquals(expected[index].first, route.size)
                assertEquals(expected[index].second, digest)
                println("CAT_ROUTE_OPTIMIZED run=$run case=$index count=${route.size} digest=$digest us=$elapsed")
            }
        }
    }
}
