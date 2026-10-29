package dev.jamescullimore.dontgotobed

import kotlin.test.*
import platform.Foundation.*

class WorldSaveFilesTest {
    @Test fun nativeAtomicFilesRetainIndependentSlotsAndPreviousCopy() {
        val directory=NSTemporaryDirectory()+"dgtb-save-test-"+nanoTime()
        try {
            val one=IosWorldSaveFiles(directory,1);val two=IosWorldSaveFiles(directory,2)
            assertNull(one.read())
            one.write("first — 世界",null)
            one.write("second",one.read())
            two.write("other world",null)
            assertEquals("second",IosWorldSaveFiles(directory,1).read())
            assertEquals("first — 世界",one.read(true))
            assertEquals("other world",two.read())
            assertNull(IosWorldSaveFiles(directory,3).read())
        } finally { NSFileManager.defaultManager.removeItemAtPath(directory,null) }
    }
}
