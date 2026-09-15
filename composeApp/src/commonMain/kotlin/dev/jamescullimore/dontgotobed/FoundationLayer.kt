package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor

@Composable
internal fun FoundationLayer(rows: Int, blockSize: Dp, cameraX: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cell = blockSize.toPx()
        val top = size.height - rows * cell
        val shift = ((cameraX.toPx() % cell) + cell) % cell
        val firstColumn = floor(cameraX.toPx() / cell).toInt()
        drawRect(Color(0xFF69432D), Offset(0f, top), Size(size.width, rows * cell))
        for (row in 0 until rows) {
            for (col in -1..ceil(size.width / cell).toInt()) {
                val x = col * cell - shift
                val y = top + row * cell
                drawRect(if ((firstColumn + col + row) % 2 == 0) Color(0xFF795238) else Color(0xFF70492F),
                    Offset(x + 1.dp.toPx(), y + 1.dp.toPx()), Size(cell - 2.dp.toPx(), cell - 2.dp.toPx()))
                drawLine(Color(0xFF906443), Offset(x + 2.dp.toPx(), y + 2.dp.toPx()),
                    Offset(x + cell - 2.dp.toPx(), y + 2.dp.toPx()), strokeWidth = 1.dp.toPx())
            }
        }
    }
}
