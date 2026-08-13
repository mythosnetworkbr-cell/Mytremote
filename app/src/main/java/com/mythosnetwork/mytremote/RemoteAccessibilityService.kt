package com.mythosnetwork.mytremote

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/** Android requires the owner to enable this service explicitly. */
class RemoteAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile private var instance: RemoteAccessibilityService? = null

        fun isEnabled(): Boolean = instance != null

        fun back(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_BACK) == true
        fun home(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_HOME) == true
        fun recents(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_RECENTS) == true

        fun tap(x: Float, y: Float, durationMs: Long = 80L): Boolean {
            val service = instance ?: return false
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            return service.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
        }

        fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 350L): Boolean {
            val service = instance ?: return false
            val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            return service.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }
}
