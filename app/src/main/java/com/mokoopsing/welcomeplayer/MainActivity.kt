package com.mokoopsing.welcomeplayer

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Button
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.view.View
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private lateinit var logTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var playButton: PlayControlView
    private lateinit var progressBar: SeekBar
    private var isManualPlaying = false
    private var hasManualPlaybackStarted = false

    private val usbLogReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.getStringExtra(UsbEventReceiver.EXTRA_LOG_LINE)?.let {
                appendLog(it)
                if (it.contains("CarLife connection event matched")) {
                    statusTextView.text = "声启旅程\n\n已连接车机\n\nUSB 连接事件"
                }
            }
            if (intent.action == WelcomePlaybackService.ACTION_PLAYBACK_PROGRESS) {
                updatePlaybackProgress(intent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        logTextView = TextView(this).apply {
            textSize = 14f
            setTextIsSelectable(true)
            setPadding(24, 16, 24, 16)
            text = readExistingLogs()
        }
        val logScrollView = ScrollView(this).apply {
            addView(logTextView)
        }
        statusTextView = TextView(this).apply {
            text = "声启旅程\n\n等待车机连接…\n\nUSB 连接事件"
            textSize = 20f
            setPadding(48, 88, 48, 24)
        }
        val exportButton = Button(this).apply {
            text = "导出 USB 日志"
            setOnClickListener { exportLogs() }
        }
        val clearButton = Button(this).apply {
            text = "清理 USB 日志"
            setOnClickListener { clearLogs() }
        }
        playButton = PlayControlView(this).apply {
            contentDescription = "播放或暂停"
            setOnClickListener { toggleManualPlayback() }
        }
        progressBar = SeekBar(this).apply {
            max = 1000
            visibility = android.view.View.GONE
            isEnabled = false
        }
        val playbackControls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            addView(progressBar, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(playButton, LinearLayout.LayoutParams(64.dp(), 64.dp()))
            setPadding(32, 8, 32, 16)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(statusTextView)
            addView(exportButton)
            addView(clearButton)
            addView(
                logScrollView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
            addView(
                playbackControls,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        })

        val serviceIntent = Intent(this, WelcomePlaybackService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun startPlayback(action: String) {
        val intent = Intent(this, WelcomePlaybackService::class.java).setAction(action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            usbLogReceiver,
            IntentFilter().apply {
                addAction(UsbEventReceiver.ACTION_USB_LOG_UPDATED)
                addAction(WelcomePlaybackService.ACTION_PLAYBACK_PROGRESS)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        logTextView.post {
            (logTextView.parent as? ScrollView)?.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    override fun onStop() {
        unregisterReceiver(usbLogReceiver)
        super.onStop()
    }

    private fun appendLog(line: String) {
        if (logTextView.text.isNotEmpty()) logTextView.append("\n")
        logTextView.append(line)
        logTextView.post {
            (logTextView.parent as? ScrollView)?.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun toggleManualPlayback() {
        val action = if (isManualPlaying) {
            WelcomePlaybackService.ACTION_PAUSE
        } else if (hasManualPlaybackStarted) {
            WelcomePlaybackService.ACTION_RESUME
        } else {
            WelcomePlaybackService.ACTION_PLAY_MANUAL
        }
        startPlayback(action)
        hasManualPlaybackStarted = true
        isManualPlaying = !isManualPlaying
        playButton.isPlaying = isManualPlaying
        progressBar.visibility = android.view.View.VISIBLE
    }

    private fun updatePlaybackProgress(intent: Intent) {
        if (intent.hasExtra(WelcomePlaybackService.EXTRA_AUTOMATIC)) {
            val automatic = intent.getBooleanExtra(WelcomePlaybackService.EXTRA_AUTOMATIC, false)
            playButton.isEnabled = !automatic
            progressBar.visibility = android.view.View.VISIBLE
            if (!automatic) {
                isManualPlaying = false
                playButton.isPlaying = false
            }
        }
        if (intent.getBooleanExtra(WelcomePlaybackService.EXTRA_COMPLETED, false)) {
            hasManualPlaybackStarted = false
            isManualPlaying = false
        }
        val duration = intent.getLongExtra(WelcomePlaybackService.EXTRA_DURATION, 0L)
        val position = intent.getLongExtra(WelcomePlaybackService.EXTRA_POSITION, 0L)
        if (duration > 0L) {
            progressBar.progress = ((position * 1000L) / duration).toInt().coerceIn(0, 1000)
        }
        isManualPlaying = intent.getBooleanExtra(WelcomePlaybackService.EXTRA_PLAYING, false)
        playButton.isPlaying = isManualPlaying
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private class PlayControlView(context: Context) : View(context) {
        private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        var isPlaying: Boolean = false
            set(value) {
                field = value
                invalidate()
            }

        init {
            isClickable = true
            circlePaint.color = Color.rgb(35, 35, 35)
            iconPaint.color = Color.WHITE
            iconPaint.style = Paint.Style.FILL
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val centerX = width / 2f
            val centerY = height / 2f
            val radius = (minOf(width, height) / 2f) - 2f
            circlePaint.alpha = if (isEnabled) 255 else 90
            canvas.drawCircle(centerX, centerY, radius, circlePaint)
            iconPaint.alpha = if (isEnabled) 255 else 150
            if (isPlaying) {
                val barWidth = width * 0.13f
                val barHeight = height * 0.34f
                canvas.drawRoundRect(
                    centerX - barWidth - 4f,
                    centerY - barHeight / 2f,
                    centerX - 4f,
                    centerY + barHeight / 2f,
                    2f,
                    2f,
                    iconPaint
                )
                canvas.drawRoundRect(
                    centerX + 4f,
                    centerY - barHeight / 2f,
                    centerX + barWidth + 4f,
                    centerY + barHeight / 2f,
                    2f,
                    2f,
                    iconPaint
                )
            } else {
                val triangle = Path().apply {
                    moveTo(centerX - width * 0.12f, centerY - height * 0.19f)
                    lineTo(centerX + width * 0.20f, centerY)
                    lineTo(centerX - width * 0.12f, centerY + height * 0.19f)
                    close()
                }
                canvas.drawPath(triangle, iconPaint)
            }
        }
    }

    private fun readExistingLogs(): String = try {
        openFileInput(UsbEventReceiver.LOG_FILE).bufferedReader().use { it.readText() }
    } catch (_: java.io.FileNotFoundException) {
        "暂无 USB 连接事件"
    }

    private fun exportLogs() {
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "声启旅程 USB 日志")
                putExtra(Intent.EXTRA_TEXT, logTextView.text.toString())
            },
            "导出声启旅程 USB 日志"
        ))
    }

    private fun clearLogs() {
        deleteFile(UsbEventReceiver.LOG_FILE)
        logTextView.text = "暂无 USB 连接事件"
    }
}
