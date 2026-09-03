package com.mokoopsing.welcomeplayer

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.SilenceMediaSource
import java.io.File

class MainActivity : Activity() {
    private var player: ExoPlayer? = null
    private var statusTextView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        statusTextView = TextView(this).apply {
            text = "WelcomePlayer\n\n正在播放欢迎音频…"
            textSize = 20f
            setPadding(48, 48, 48, 48)
        }
        setContentView(statusTextView)

        playWelcomeAudio()
    }

    private fun playWelcomeAudio() {
        val dirCandidates = listOfNotNull(
            getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.resolve("WelcomePlayer"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "WelcomePlayer")
        ).distinctBy { it.absolutePath }

        val dir = dirCandidates.firstOrNull { it.exists() }
            ?: dirCandidates.firstOrNull()

        if (dir != null && !dir.exists()) {
            dir.mkdirs()
        }

        val files = dir?.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in setOf("mp3", "wav", "m4a", "aac", "ogg", "flac") }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

        if (files.isEmpty()) {
            statusTextView?.text = buildString {
                append("WelcomePlayer\n\n没有找到音频文件。\n\n")
                append("请把 MP3/WAV 等文件放到：\n")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    append("Android/data/")
                    append(packageName)
                    append("/files/Music/WelcomePlayer/\n")
                } else {
                    append("Music/WelcomePlayer/\n")
                }
            }
            return
        }

        player = ExoPlayer.Builder(this).build().also { exo ->
            val mediaSourceFactory = DefaultMediaSourceFactory(this@MainActivity)
            val mediaSources = files.flatMapIndexed { index, file ->
                buildList {
                    add(mediaSourceFactory.createMediaSource(MediaItem.fromUri(android.net.Uri.fromFile(file))))
                    if (index < files.lastIndex) {
                        add(SilenceMediaSource(500_000L))
                    }
                }
            }
            exo.setMediaSources(mediaSources)
            exo.prepare()
            exo.play()
            exo.addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == androidx.media3.common.Player.STATE_ENDED) {
                        exo.release()
                        player = null
                        finish()
                    }
                }
            })
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
