package com.mokoopsing.welcomeplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.SilenceMediaSource
import java.io.File

class WelcomePlaybackService : Service() {
    private var player: ExoPlayer? = null
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            player?.let { exo ->
                sendPlaybackProgress(exo.isPlaying, exo.currentPosition, exo.duration)
                if (exo.isPlaying) {
                    progressHandler.postDelayed(this, PROGRESS_INTERVAL_MS)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
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
        if (files.isEmpty()) {
            sendPlaybackError(
                if (dir == null) "未找到音频目录" else "音频目录为空：${dir.absolutePath}"
            )
            return
        }

        player = ExoPlayer.Builder(this).build().also { exo ->
            val mediaSourceFactory = DefaultMediaSourceFactory(this)
            val mediaSources = files.flatMapIndexed { index, file ->
                buildList {
                    add(mediaSourceFactory.createMediaSource(MediaItem.fromUri(android.net.Uri.fromFile(file))))
                    if (index < files.lastIndex) add(SilenceMediaSource(500_000L))
                }
            }
            exo.setMediaSources(mediaSources)
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        sendPlaybackProgress(
                            false,
                            exo.duration,
                            exo.duration,
                            automatic = false,
                            completed = true
                        )
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        progressHandler.removeCallbacks(progressRunnable)
                        progressHandler.post(progressRunnable)
                    }
                }
            })
            exo.prepare()
            exo.play()
            progressHandler.removeCallbacks(progressRunnable)
            progressHandler.post(progressRunnable)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "声启旅程播放",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle("声启旅程")
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
        when (intent?.action) {
            ACTION_PLAY_CARLIFE -> {
                sendPlaybackProgress(false, 0L, 0L, automatic = true)
                releasePlayer()
                startPlayback()
            }
            ACTION_PLAY_MANUAL -> {
                sendPlaybackProgress(false, 0L, 0L, automatic = false)
                releasePlayer()
                startPlayback()
            }
            ACTION_PAUSE -> {
                player?.pause()
                player?.let { sendPlaybackProgress(false, it.currentPosition, it.duration) }
            }
            ACTION_RESUME -> {
                player?.play()
                player?.let {
                    sendPlaybackProgress(true, it.currentPosition, it.duration)
                    progressHandler.removeCallbacks(progressRunnable)
                    progressHandler.post(progressRunnable)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY_CARLIFE = "com.mokoopsing.welcomeplayer.PLAY_CARLIFE"
        const val ACTION_PLAY_MANUAL = "com.mokoopsing.welcomeplayer.PLAY_MANUAL"
        const val ACTION_PAUSE = "com.mokoopsing.welcomeplayer.PAUSE"
        const val ACTION_RESUME = "com.mokoopsing.welcomeplayer.RESUME"
        const val ACTION_PLAYBACK_PROGRESS = "com.mokoopsing.welcomeplayer.PLAYBACK_PROGRESS"
        const val EXTRA_PLAYING = "playing"
        const val EXTRA_AUTOMATIC = "automatic"
        const val EXTRA_POSITION = "position"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_COMPLETED = "completed"
        const val EXTRA_ERROR = "error"
        private const val CHANNEL_ID = "welcomeplayer_playback"
        private const val NOTIFICATION_ID = 1001
        private const val PROGRESS_INTERVAL_MS = 500L
        private val SUPPORTED_EXTENSIONS = setOf("mp3", "wav", "m4a", "aac", "ogg", "flac")
    }

    private fun releasePlayer() {
        progressHandler.removeCallbacks(progressRunnable)
        player?.release()
        player = null
    }

    private fun sendPlaybackProgress(
        playing: Boolean,
        position: Long,
        duration: Long,
        automatic: Boolean? = null,
        completed: Boolean = false
    ) {
        val progressIntent = Intent(ACTION_PLAYBACK_PROGRESS).setPackage(packageName)
            .putExtra(EXTRA_PLAYING, playing)
            .putExtra(EXTRA_POSITION, position)
            .putExtra(EXTRA_DURATION, duration)
            .putExtra(EXTRA_COMPLETED, completed)
        automatic?.let { progressIntent.putExtra(EXTRA_AUTOMATIC, it) }
        sendBroadcast(progressIntent)
    }

    private fun sendPlaybackError(message: String) {
        sendBroadcast(
            Intent(ACTION_PLAYBACK_PROGRESS).setPackage(packageName)
                .putExtra(EXTRA_PLAYING, false)
                .putExtra(EXTRA_POSITION, 0L)
                .putExtra(EXTRA_DURATION, 0L)
                .putExtra(EXTRA_COMPLETED, false)
                .putExtra(EXTRA_ERROR, message)
        )
    }
}
