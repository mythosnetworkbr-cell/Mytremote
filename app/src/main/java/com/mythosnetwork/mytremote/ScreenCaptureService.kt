package com.mythosnetwork.mytremote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.DataOutputStream
import java.net.Socket
import java.util.concurrent.Executors

class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var socket: Socket? = null
    private var output: DataOutputStream? = null
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var sending = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "mythos_remote_capture"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channelId, "Mythøs Remote", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Mythøs Remote ativo")
            .setContentText("A tela está sendo compartilhada com autorização.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            @Suppress("DEPRECATION") startForeground(1001, notification)
        }

        val resultCode = intent?.getIntExtra("resultCode", 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent?.getParcelableExtra("data")
        }
        val viewerIp = intent?.getStringExtra("viewerIp").orEmpty()
        if (data != null) {
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = projectionManager.getMediaProjection(resultCode, data)
            startCapture(viewerIp)
        }
        return START_NOT_STICKY
    }

    private fun startCapture(viewerIp: String) {
        val windowManager = getSystemService(WindowManager::class.java)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION") windowManager.defaultDisplay.getRealMetrics(metrics)
        val width = minOf(metrics.widthPixels, 720)
        val height = (metrics.heightPixels.toFloat() * width / metrics.widthPixels).toInt().coerceAtLeast(1)
        val density = metrics.densityDpi
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopCapture() }
        }, null)
        virtualDisplay = projection?.createVirtualDisplay(
            "MythosRemote", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )
        if (viewerIp.isBlank()) return
        executor.execute {
            try {
                socket = Socket(viewerIp, ScreenViewerServer.PORT)
                output = DataOutputStream(socket!!.getOutputStream())
                sending = true
                while (sending) {
                    val image = imageReader?.acquireLatestImage()
                    if (image == null) { Thread.sleep(60); continue }
                    image.use {
                        val plane = it.planes[0]
                        val buffer = plane.buffer
                        val pixelStride = plane.pixelStride
                        val rowPadding = plane.rowStride - pixelStride * it.width
                        val bitmapWidth = it.width + rowPadding / pixelStride
                        val raw = Bitmap.createBitmap(bitmapWidth, it.height, Bitmap.Config.ARGB_8888)
                        raw.copyPixelsFromBuffer(buffer)
                        val bitmap = Bitmap.createBitmap(raw, 0, 0, it.width, it.height)
                        raw.recycle()
                        val compressed = java.io.ByteArrayOutputStream().use { bos ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 55, bos)
                            bitmap.recycle()
                            bos.toByteArray()
                        }
                        output?.writeInt(compressed.size)
                        output?.write(compressed)
                        output?.flush()
                    }
                    Thread.sleep(100)
                }
            } catch (_: Exception) { } finally { stopSocket() }
        }
    }

    private fun stopSocket() {
        sending = false
        try { output?.flush() } catch (_: Exception) { }
        try { socket?.close() } catch (_: Exception) { }
        output = null
        socket = null
    }

    private fun stopCapture() {
        sending = false
        stopSocket()
        try { virtualDisplay?.release() } catch (_: Exception) { }
        virtualDisplay = null
        try { imageReader?.close() } catch (_: Exception) { }
        imageReader = null
        try { projection?.stop() } catch (_: Exception) { }
        projection = null
    }

    override fun onDestroy() {
        stopCapture()
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
