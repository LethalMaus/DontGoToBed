package dev.jamescullimore.dontgotobed

import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkeletonPulseTest {
    @Test fun invisibleNativeAlphaDoesNotRequestAFilter() {
        for (amount in listOf(0f, 0.00001f, 0.001f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertNull(skeletonPulseColor(amount), "amount=$amount")
        }
        assertEquals(1, assertNotNull(skeletonPulseColor(0.002f)).toArgb() ushr 24)
        assertEquals(255, assertNotNull(skeletonPulseColor(1f)).toArgb() ushr 24)
    }

    @Test fun completeArcherAimCycleNeverPassesTransparentColorToSkia() {
        var previouslyUnsafeFrames = 0
        for (millis in 0 until 3000) {
            val p = (millis.toFloat() / 3000).toDouble()
            val amount = ((1 - cos(2 * PI * (3 * p + 6 * p * p))) / 2).toFloat()
            val color = skeletonPulseColor(amount)
            if (color != null) assertTrue((color.toArgb() ushr 24) > 0)
            else if (amount > 0f) previouslyUnsafeFrames++
        }
        assertTrue(previouslyUnsafeFrames > 0, "Exercise positive floats rounded to transparent native alpha")
    }
}
