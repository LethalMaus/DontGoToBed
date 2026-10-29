package dev.jamescullimore.dontgotobed

/** Presentation coordinates only: zoom must never rescale simulation or network positions. */
object WorldCamera {
    val zoomLevels = listOf(0.75f, 1f, 1.5f)

    fun bottom(playerBottom: Float, viewportHeight: Float, worldHeight: Float, zoom: Float): Float {
        require(zoom > 0f)
        val visibleHeight = viewportHeight.coerceAtLeast(0f) / zoom
        return (playerBottom - visibleHeight * 0.38f)
            .coerceIn(0f, (worldHeight - visibleHeight).coerceAtLeast(0f))
    }

    fun worldY(screenY: Float, viewportHeight: Float, cameraBottom: Float, zoom: Float): Float =
        (viewportHeight - screenY) / zoom + cameraBottom

    fun worldX(screenX: Float, viewportWidth: Float, playerLeft: Float, playerWidth: Float, zoom: Float): Float =
        screenX / zoom + playerLeft - (viewportWidth / zoom - playerWidth) / 2f
}
