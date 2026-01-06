package com.fw.myfirewall.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.fw.myfirewall.service.MyVpnService
import com.fw.myfirewall.service.HostsFilterService // Import ajouté

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("MyFirewallPrefs", Context.MODE_PRIVATE)
            val startOnBoot = prefs.getBoolean("start_on_boot", false)
            val isRootEnabled = prefs.getBoolean("root_enabled", false)

            // 1. DÉMARRAGE DU FILTRAGE ROOT (HOSTS)
            // Le fichier hosts est persistant, on lance le service pour la notification
            if (isRootEnabled) {
                HostsFilterService.start(context)
            }

            // 2. DÉMARRAGE DU VPN (PARE-FEU / DNS VPN)
            if (startOnBoot) {
                // Prepare VPN (usually returns null if permission was already granted)
                // However, on boot, we can't show a dialog. If permission is not granted, we can't start.
                val prepareIntent = VpnService.prepare(context)
                if (prepareIntent == null) {
                    val serviceIntent = Intent(context, MyVpnService::class.java).apply {
                        action = MyVpnService.ACTION_CONNECT
                    }
                    context.startForegroundService(serviceIntent)
                }
            }
        }
    }
}
