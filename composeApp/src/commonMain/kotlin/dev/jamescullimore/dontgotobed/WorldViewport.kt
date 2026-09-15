package dev.jamescullimore.dontgotobed

/** Display-only foundation below world row zero; never serialized or mineable. */
object WorldViewport {
    const val BLOCK_CELLS = 3
    fun foundationRows(widthDp: Float, heightDp: Float): Int =
        if (minOf(widthDp, heightDp) >= 600f) 3 else 1

    fun screenToWorldY(viewHeightPx: Float, screenYPx: Float, foundationPx: Float): Float =
        viewHeightPx - foundationPx - screenYPx
}
