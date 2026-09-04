package com.mokoopsing.welcomeplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.IntentFilter
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.SilenceMediaSource
import java.io.File

class WelcomePlaybackService : Service() {
    private var player: ExoPlayer? = null
    private val usbStateReceiver = UsbEventReceiver()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        ContextCompat.registerReceiver(
            this,
            usbStateReceiver,
            IntentFilter().apply {
                addAction(UsbEventReceiver.ACTION_USB_STATE)
                addAction(UsbEventReceiver.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbEventReceiver.ACTION_USB_DEVICE_DETACHED)
                addAction(UsbEventReceiver.ACTION_USB_ACCESSORY_ATTACHED)
                addAction(UsbEventReceiver.ACTION_USB_ACCESSORY_DETACHED)
            },
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun startPlayback() {
        val dirCandidates = listOfNotNull(
            getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.resolve("WelcomePlayer"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "WelcomePlayer")
        ).distinctBy { it.absolutePath }
        val dir = dirCandidates.firstOrNull { it.exists() } ?: dirCandidates.firstOrNull()
        if (dir != null && !dir.exists()) dir.mkdirs()

        val files = dir?.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in SUPPORTED_EXTENSIONS }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
        if (files.isEmpty()) return

        player = ExoPlayer.Builder(this).build().also { exo ->
            val mediaSourceFactory = DefaultMediaSourceFactory(this)
            val mediaSources = files.flatMapIndexed { index, file ->
                buildList {
                    add(mediaSourceFactory.createMediaSource(MediaItem.fromUri(android.net.Uri.fromFile(file))))
                    if (index < files.lastIndex) add(SilenceMediaSource(500_000L))
                }
            }
            exo.setMediaSources(mediaSources)
            exo.prepare()
            exo.play()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WelcomePlayer 播放",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle("WelcomePlayer")
        .setContentText("欢迎音频播放服务正在运行")
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .setOngoing(true)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        player?.release()
        player = null
        startPlayback()
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(usbStateReceiver)
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY_CARLIFE = "com.mokoopsing.welcomeplayer.PLAY_CARLIFE"
        private const val CHANNEL_ID = "welcomeplayer_playback"
        private const val NOTIFICATION_ID = 1001
        private val SUPPORTED_EXTENSIONS = setOf("mp3", "wav", "m4a", "aac", "ogg", "flac")
    }
}
