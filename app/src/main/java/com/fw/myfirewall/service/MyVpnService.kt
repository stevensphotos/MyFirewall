package com.fw.myfirewall.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import com.fw.myfirewall.MainActivity
import com.fw.myfirewall.R
import com.fw.myfirewall.data.AppDatabase
import com.fw.myfirewall.data.AppRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.fw.myfirewall.service.CONNECT"
        const val ACTION_DISCONNECT = "com.fw.myfirewall.service.DISCONNECT"
        private const val CHANNEL_ID = "MyFirewallVpnChannel"
        private const val NOTIFICATION_ID = 1
        
        var isServiceRunning = mutableStateOf(false)
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var cachedRules: Map<String, AppRule> = emptyMap()
    private var isObserversRegistered = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MyVpnService", "onStartCommand: ${intent?.action}")
        when (intent?.action) {
            ACTION_CONNECT -> connect()
            ACTION_DISCONNECT -> disconnect()
        }
        return START_STICKY
    }

    private fun connect() {
        if (isServiceRunning.value) {
            Log.i("MyVpnService", "Service already running")
            return
        }
        
        Log.i("MyVpnService", "Starting VPN Service")
        try {
            createNotificationChannel()
            val pendingIntent = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_content))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()

            startForeground(NOTIFICATION_ID, notification)
            isServiceRunning.value = true
            
            startObservers()
            
        } catch (e: Exception) {
            Log.e("MyVpnService", "Error in connect", e)
            disconnect()
        }
    }

    private fun startObservers() {
        if (isObserversRegistered) return
        
        // Watch for Network Changes
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d("MyVpnService", "Network available, updating VPN")
                updateVpn()
            }
            override fun onLost(network: Network) {
                Log.d("MyVpnService", "Network lost, updating VPN")
                updateVpn()
            }
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                 // Optionally update on capability change (e.g. transport change)
                 updateVpn()
            }
        }
        try {
            cm.registerDefaultNetworkCallback(networkCallback!!)
        } catch (e: Exception) {
            Log.e("MyVpnService", "Failed to register network callback", e)
        }

        // Watch for Database Rules Changes
        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.appRuleDao().getAllRules().collect { rules ->
                Log.d("MyVpnService", "Rules updated, updating VPN")
                cachedRules = rules.associateBy { it.packageName }
                updateVpn()
            }
        }
        
        isObserversRegistered = true
    }

    private fun updateVpn() {
        if (!isServiceRunning.value) return

        serviceScope.launch {
            try {
                if (VpnService.prepare(this@MyVpnService) != null) {
                    Log.e("MyVpnService", "VPN not prepared (revoked?)")
                    disconnect()
                    return@launch
                }

                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val activeNetwork = cm.activeNetwork
                val caps = cm.getNetworkCapabilities(activeNetwork)
                val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                val isMobile = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
                
                Log.i("MyVpnService", "Updating VPN config. Wifi: $isWifi, Mobile: $isMobile")

                val builder = Builder()
                builder.setSession(getString(R.string.vpn_session_name))
                builder.addAddress("10.0.0.2", 32)
                builder.addRoute("0.0.0.0", 0)
                builder.setMtu(1500)

                val pm = packageManager
                val installedApps = withContext(Dispatchers.IO) {
                    pm.getInstalledPackages(0)
                }

                for (app in installedApps) {
                    val pkg = app.packageName
                    if (pkg == packageName) continue

                    val rule = cachedRules[pkg]
                    var allowed = true // Default allowed

                    if (rule != null) {
                        if (isWifi) {
                            allowed = rule.wifiAllowed
                        } else if (isMobile) {
                            allowed = rule.mobileAllowed
                        }
                    }

                    if (allowed) {
                        try {
                            builder.addDisallowedApplication(pkg)
                        } catch (e: Exception) {
                            Log.w("MyVpnService", "Failed to exclude $pkg", e)
                        }
                    } else {
                        Log.d("MyVpnService", "Blocking $pkg")
                    }
                }

                // establish() closes the old interface if it exists and returns a new one
                // synchronized access might be better but here we are on Main thread (via serviceScope)
                val oldInterface = vpnInterface
                vpnInterface = builder.establish()
                
                if (vpnInterface != null) {
                    Log.i("MyVpnService", "VPN Re-established successfully")
                    oldInterface?.close()
                } else {
                    Log.e("MyVpnService", "Failed to establish VPN")
                    // Don't stop self immediately on update failure, maybe transient?
                    // But if interface is null, we are not filtering.
                }

            } catch (e: Exception) {
                Log.e("MyVpnService", "Exception in updateVpn()", e)
            }
        }
    }

    private fun disconnect() {
        Log.i("MyVpnService", "Disconnecting VPN")
        try {
            if (isObserversRegistered) {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                networkCallback?.let { cm.unregisterNetworkCallback(it) }
                isObserversRegistered = false
            }
            
            vpnInterface?.close()
            vpnInterface = null
            isServiceRunning.value = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (e: Exception) {
            Log.e("MyVpnService", "Failed to disconnect VPN", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel() // Cancels the scope and rule collector
        disconnect()
    }
}
