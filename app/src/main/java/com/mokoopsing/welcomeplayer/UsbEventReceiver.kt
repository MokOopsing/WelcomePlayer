package com.mokoopsing.welcomeplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UsbEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val device = intent.getParcelableExtraCompat<UsbDevice>(UsbManager.EXTRA_DEVICE)
        val accessory = intent.getParcelableExtraCompat<UsbAccessory>(UsbManager.EXTRA_ACCESSORY)
        val details = buildString {
            append(timestampFormat.format(Date()))
            append(" action=").append(intent.action)
            if (intent.action == ACTION_USB_STATE) {
                append(" connected=").append(intent.getBooleanExtra(EXTRA_CONNECTED, false))
                append(" configured=").append(intent.getBooleanExtra(EXTRA_CONFIGURED, false))
                append(" mtp=").append(intent.getBooleanExtra(EXTRA_MTP, false))
                append(" ptp=").append(intent.getBooleanExtra(EXTRA_PTP, false))
                append(" adb=").append(intent.getBooleanExtra(EXTRA_ADB, false))
            } else if (accessory != null) {
                append(" accessory=")
                append("manufacturer=").append(accessory.manufacturer ?: "unknown")
                append(" model=").append(accessory.model ?: "unknown")
                append(" description=").append(accessory.description ?: "unknown")
                append(" version=").append(accessory.version ?: "unknown")
                append(" uri=").append(accessory.uri ?: "unknown")
                append(" serial=").append(accessory.serial ?: "unknown")
            } else {
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
            intent.extras?.keySet()?.sorted()?.forEach { key ->
                if (key !in setOf(EXTRA_CONNECTED, EXTRA_CONFIGURED, EXTRA_MTP, EXTRA_PTP, EXTRA_ADB,
                        UsbManager.EXTRA_DEVICE, UsbManager.EXTRA_ACCESSORY)) {
                    append(" extra_").append(key).append("=").append(intent.extras?.get(key))
                }
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
        const val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"
        const val ACTION_USB_DEVICE_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED"
        const val ACTION_USB_DEVICE_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED"
        const val ACTION_USB_ACCESSORY_ATTACHED = "android.hardware.usb.action.USB_ACCESSORY_ATTACHED"
        const val ACTION_USB_ACCESSORY_DETACHED = "android.hardware.usb.action.USB_ACCESSORY_DETACHED"
        const val ACTION_USB_LOG_UPDATED = "com.mokoopsing.welcomeplayer.USB_LOG_UPDATED"
        const val EXTRA_LOG_LINE = "log_line"
        const val LOG_FILE = "usb-events.log"
        private const val EXTRA_CONNECTED = "connected"
        private const val EXTRA_CONFIGURED = "configured"
        private const val EXTRA_MTP = "mtp"
        private const val EXTRA_PTP = "ptp"
        private const val EXTRA_ADB = "adb"
        private const val TAG = "WelcomePlayerUsb"
        private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ", Locale.US)
    }
}
