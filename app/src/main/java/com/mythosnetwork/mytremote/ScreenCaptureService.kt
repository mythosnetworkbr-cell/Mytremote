package com.mythosnetwork.mytremote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder

class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "mythos_remote_capture"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channelId, "Mythøs Remote", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Mythøs Remote ativo")
            .setContentText("A tela está sendo compartilhada com autorização.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
        startForeground(1001, notification)

        val resultCode = intent?.getIntExtra("resultCode", 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>("data")
        if (data != null) {
            val managerProjection = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = managerProjection.getMediaProjection(resultCode, data)
            // Próxima etapa: VirtualDisplay + encoder H.264 + WebRTC.
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        projection?.stop()
        projection = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
