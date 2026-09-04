package com.mokoopsing.welcomeplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UsbEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val device = intent.getParcelableExtraCompat<UsbDevice>(UsbManager.EXTRA_DEVICE)
        val details = buildString {
            append(timestampFormat.format(Date()))
            append(" action=").append(intent.action)
            append(" device=").append(device?.deviceName ?: "unknown")
            device?.let {
                append(" vendorId=").append(it.vendorId)
                append(" productId=").append(it.productId)
                append(" manufacturer=").append(it.manufacturerName ?: "unknown")
                append(" product=").append(it.productName ?: "unknown")
                append(" class=").append(it.deviceClass)
                append(" subclass=").append(it.deviceSubclass)
                append(" protocol=").append(it.deviceProtocol)
            }
        }
        Log.i(TAG, details)
        context.openFileOutput(LOG_FILE, Context.MODE_APPEND).bufferedWriter().use {
            it.appendLine(details)
        }
        context.sendBroadcast(
            Intent(ACTION_USB_LOG_UPDATED)
                .setPackage(context.packageName)
                .putExtra(EXTRA_LOG_LINE, details)
        )
    }

    private inline fun <reified T> Intent.getParcelableExtraCompat(name: String): T? =
        if (android.os.Build.VERSION.SDK_INT >= 33) getParcelableExtra(name, T::class.java)
        else @Suppress("DEPRECATION") getParcelableExtra(name)

    companion object {
        const val ACTION_USB_LOG_UPDATED = "com.mokoopsing.welcomeplayer.USB_LOG_UPDATED"
        const val EXTRA_LOG_LINE = "log_line"
        const val LOG_FILE = "usb-events.log"
        private const val TAG = "WelcomePlayerUsb"
        private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ", Locale.US)
    }
}
