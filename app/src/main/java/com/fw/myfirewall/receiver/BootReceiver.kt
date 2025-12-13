package com.fw.myfirewall.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.fw.myfirewall.service.MyVpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("MyFirewallPrefs", Context.MODE_PRIVATE)
            val startOnBoot = prefs.getBoolean("start_on_boot", false)

            if (startOnBoot) {
                // Prepare VPN (usually returns null if permission was already granted)
                // However, on boot, we can't show a dialog. If permission is not granted, we can't start.
                val prepareIntent = VpnService.prepare(context)
                if (prepareIntent == null) {
                    val serviceIntent = Intent(context, MyVpnService::class.java)
                    serviceIntent.action = MyVpnService.ACTION_CONNECT
                    context.startForegroundService(serviceIntent)
                }
            }
        }
    }
}
