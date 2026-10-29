package dev.jamescullimore.dontgotobed

import androidx.compose.runtime.*
import platform.Foundation.*

@Composable internal actual fun rememberWorldSaveFiles(slot:Int):WorldSaveFiles = remember(slot) {
    IosWorldSaveFiles((NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory,NSUserDomainMask,true).first() as String)+"/DontGoToBed",slot)
}
internal class IosWorldSaveFiles(private val directory:String,private val slot:Int):WorldSaveFiles {
    private fun path(backup:Boolean)=directory+if(backup)"/exploration-world-$slot.previous" else "/exploration-world-$slot.save"
    override fun read(backup:Boolean):String? {
        if(!NSFileManager.defaultManager.fileExistsAtPath(path(backup)))return null
        return NSString.stringWithContentsOfFile(path(backup),NSUTF8StringEncoding,null) ?: error("Cannot read world save")
    }
    private fun replace(backup:Boolean,text:String) {
        check(NSFileManager.defaultManager.createDirectoryAtPath(directory,true,null,null))
        check((text as NSString).writeToFile(path(backup),true,NSUTF8StringEncoding,null))
    }
    override fun write(text:String,previous:String?) { if(previous!=null)replace(true,previous);replace(false,text) }
}
