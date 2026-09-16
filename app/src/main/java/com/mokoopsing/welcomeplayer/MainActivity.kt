package com.mokoopsing.welcomeplayer

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var logTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var playButton: PlayControlView
    private lateinit var progressBar: SeekBar
    private var isManualPlaying = false
    private var hasManualPlaybackStarted = false
    private val logLines = ArrayDeque<String>()

    private val usbLogReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbEventReceiver.ACTION_USB_LOG_UPDATED ->
                    intent.getStringExtra(UsbEventReceiver.EXTRA_LOG_LINE)?.let { appendLog(it) }
                UsbEventReceiver.ACTION_CARLIFE_CONNECTED ->
                    statusTextView.text = STATUS_CONNECTED
                WelcomePlaybackService.ACTION_PLAYBACK_PROGRESS ->
                    updatePlaybackProgress(intent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildContentView()
        ContextCompat.startForegroundService(
            this,
            Intent(this, WelcomePlaybackService::class.java)
        )
    }

    private fun buildContentView() {
        loadLogLines()
        logTextView = TextView(this).apply {
            textSize = 14f
            setTextIsSelectable(true)
            setPadding(24, 16, 24, 16)
            text = logLines.joinToString("\n")
        }
        val logScrollView = ScrollView(this).apply {
            addView(logTextView)
        }
        statusTextView = TextView(this).apply {
            text = STATUS_WAITING
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
            visibility = View.GONE
            isEnabled = false
        }
        val playbackControls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
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
    }

    private fun startPlayback(action: String) {
        ContextCompat.startForegroundService(
            this,
            Intent(this, WelcomePlaybackService::class.java).setAction(action)
        )
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            usbLogReceiver,
            IntentFilter().apply {
                addAction(UsbEventReceiver.ACTION_USB_LOG_UPDATED)
                addAction(UsbEventReceiver.ACTION_CARLIFE_CONNECTED)
                addAction(WelcomePlaybackService.ACTION_PLAYBACK_PROGRESS)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        refreshConnectionStatus()
        scrollLogToBottom()
    }

    private fun refreshConnectionStatus() {
        statusTextView.text = if (UsbEventReceiver.isCarLifeCurrentlyConnected(this)) {
            STATUS_CONNECTED
        } else {
            STATUS_WAITING
        }
    }

    override fun onStop() {
        unregisterReceiver(usbLogReceiver)
        super.onStop()
    }

    private fun appendLog(line: String) {
        logLines.addLast(line)
        while (logLines.size > MAX_LOG_LINES) logLines.removeFirst()
        logTextView.text = logLines.joinToString("\n")
        scrollLogToBottom()
    }

    private fun scrollLogToBottom() {
        logTextView.post {
            (logTextView.parent as? ScrollView)?.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun toggleManualPlayback() {
        val action = when {
            isManualPlaying -> WelcomePlaybackService.ACTION_PAUSE
            hasManualPlaybackStarted -> WelcomePlaybackService.ACTION_RESUME
            else -> WelcomePlaybackService.ACTION_PLAY_MANUAL
        }
        startPlayback(action)
        hasManualPlaybackStarted = true
        progressBar.visibility = View.VISIBLE
    }

    private fun updatePlaybackProgress(intent: Intent) {
        val error = intent.getStringExtra(WelcomePlaybackService.EXTRA_ERROR)
        if (error != null) {
            hasManualPlaybackStarted = false
            setPlayingState(false)
            playButton.isEnabled = true
            progressBar.visibility = View.GONE
            progressBar.progress = 0
            statusTextView.text = "声启旅程\n\n$error\n\nUSB 连接事件"
            return
        }
        if (intent.hasExtra(WelcomePlaybackService.EXTRA_AUTOMATIC)) {
            val automatic = intent.getBooleanExtra(WelcomePlaybackService.EXTRA_AUTOMATIC, false)
            playButton.isEnabled = !automatic
            if (!intent.hasExtra(WelcomePlaybackService.EXTRA_ERROR)) {
                progressBar.visibility = View.VISIBLE
            }
        }
        if (intent.getBooleanExtra(WelcomePlaybackService.EXTRA_COMPLETED, false)) {
            hasManualPlaybackStarted = false
            setPlayingState(false)
            playButton.isEnabled = true
            progressBar.visibility = View.GONE
            progressBar.progress = 0
        }
        val duration = intent.getLongExtra(WelcomePlaybackService.EXTRA_DURATION, 0L)
        val position = intent.getLongExtra(WelcomePlaybackService.EXTRA_POSITION, 0L)
        if (duration > 0L) {
            progressBar.progress = ((position * 1000L) / duration).toInt().coerceIn(0, 1000)
        }
        setPlayingState(intent.getBooleanExtra(WelcomePlaybackService.EXTRA_PLAYING, false))
    }

    private fun setPlayingState(playing: Boolean) {
        if (isManualPlaying != playing) isManualPlaying = playing
        if (playButton.isPlaying != playing) playButton.isPlaying = playing
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private class PlayControlView(context: Context) : View(context) {
        private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var playIconPath: Path? = null
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

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            playIconPath = Path().apply {
                moveTo(w * 0.38f, h * 0.31f)
                lineTo(w * 0.70f, h * 0.50f)
                lineTo(w * 0.38f, h * 0.69f)
                close()
            }
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
                playIconPath?.let { canvas.drawPath(it, iconPaint) }
            }
        }
    }

    private fun loadLogLines() {
        logLines.clear()
        try {
            openFileInput(UsbEventReceiver.LOG_FILE).bufferedReader().useLines { lines ->
                lines.forEach {
                    logLines.addLast(it)
                    while (logLines.size > MAX_LOG_LINES) logLines.removeFirst()
                }
            }
        } catch (_: java.io.FileNotFoundException) {
            logLines.addLast("暂无 USB 连接事件")
        }
        if (logLines.isEmpty()) logLines.addLast("暂无 USB 连接事件")
    }

    private fun exportLogs() {
        val logFile = File(filesDir, UsbEventReceiver.LOG_FILE)
        val shareIntent = if (logFile.exists()) {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", logFile)
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "声启旅程 USB 日志")
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "声启旅程 USB 日志")
                putExtra(Intent.EXTRA_TEXT, logTextView.text.toString())
            }
        }
        startActivity(Intent.createChooser(shareIntent, "导出声启旅程 USB 日志"))
    }

    private fun clearLogs() {
        deleteFile(UsbEventReceiver.LOG_FILE)
        loadLogLines()
        logTextView.text = logLines.joinToString("\n")
    }

    private companion object {
        const val MAX_LOG_LINES = 500
        const val STATUS_WAITING = "声启旅程\n\n等待车机连接…\n\nUSB 连接事件"
        const val STATUS_CONNECTED = "声启旅程\n\n已连接车机\n\nUSB 连接事件"
    }
}
