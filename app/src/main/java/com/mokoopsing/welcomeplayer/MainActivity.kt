package com.mokoopsing.welcomeplayer

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(TextView(this).apply {
            text = "WelcomePlayer\n\n正在播放欢迎音频…\n\nUSB 事件日志：\nfiles/usb-events.log"
            textSize = 20f
            setPadding(48, 48, 48, 48)
        })

        val serviceIntent = Intent(this, WelcomePlaybackService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
