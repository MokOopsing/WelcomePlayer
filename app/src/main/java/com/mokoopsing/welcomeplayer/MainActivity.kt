package com.mokoopsing.welcomeplayer

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private lateinit var logTextView: TextView

    private val usbLogReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.getStringExtra(UsbEventReceiver.EXTRA_LOG_LINE)?.let { appendLog(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        logTextView = TextView(this).apply {
            textSize = 14f
            setPadding(24, 16, 24, 16)
            text = readExistingLogs()
        }
        val logScrollView = ScrollView(this).apply {
            addView(logTextView)
        }
        val statusTextView = TextView(this).apply {
            text = "WelcomePlayer\n\n正在播放欢迎音频…\n\nUSB 连接事件"
            textSize = 20f
            setPadding(48, 48, 48, 24)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(statusTextView)
            addView(
                logScrollView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
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

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            usbLogReceiver,
            IntentFilter(UsbEventReceiver.ACTION_USB_LOG_UPDATED),
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

    private fun readExistingLogs(): String = try {
        openFileInput(UsbEventReceiver.LOG_FILE).bufferedReader().use { it.readText() }
    } catch (_: java.io.FileNotFoundException) {
        "暂无 USB 连接事件"
    }
}
