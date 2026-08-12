package com.mythosnetwork.mytremote.remote

sealed interface RemoteCommand {
    data class Tap(val x: Float, val y: Float) : RemoteCommand
    data class Swipe(val startX: Float, val startY: Float, val endX: Float, val endY: Float, val durationMs: Long) : RemoteCommand
    data class Text(val value: String) : RemoteCommand
    data object Back : RemoteCommand
    data object Home : RemoteCommand
    data object Recents : RemoteCommand
    data object StopSession : RemoteCommand
}
