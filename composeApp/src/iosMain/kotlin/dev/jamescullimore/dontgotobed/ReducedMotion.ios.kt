package dev.jamescullimore.dontgotobed

import androidx.compose.runtime.*
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification

@Composable
actual fun rememberSystemReducedMotion(): Boolean {
    var reduced by remember { mutableStateOf(UIAccessibilityIsReduceMotionEnabled()) }
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val observer = center.addObserverForName(UIAccessibilityReduceMotionStatusDidChangeNotification,
            null, NSOperationQueue.mainQueue) { reduced = UIAccessibilityIsReduceMotionEnabled() }
        onDispose { center.removeObserver(observer) }
    }
    return reduced
}
