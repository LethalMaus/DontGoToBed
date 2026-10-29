package dev.jamescullimore.dontgotobed

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import android.util.AtomicFile
import java.io.File

@Composable internal actual fun rememberWorldSaveFiles(slot:Int):WorldSaveFiles {
    val context=LocalContext.current.applicationContext
    return remember(context,slot) { object:WorldSaveFiles {
        private val current=AtomicFile(File(context.filesDir,"exploration-world-$slot.save"))
        private val backup=AtomicFile(File(context.filesDir,"exploration-world-$slot.previous"))
        override fun read(backup:Boolean):String? {
            val file=if(backup)this.backup else current
            return try { file.openRead().use { it.readBytes().toString(Charsets.UTF_8) } } catch(_:java.io.FileNotFoundException) { null }
        }
        private fun replace(file:AtomicFile,text:String) {
            val output=file.startWrite()
            try { output.write(text.toByteArray(Charsets.UTF_8));file.finishWrite(output) }
            catch(e:Exception) { file.failWrite(output);throw e }
        }
        override fun write(text:String,previous:String?) { if(previous!=null)replace(backup,previous);replace(current,text) }
    } }
}
