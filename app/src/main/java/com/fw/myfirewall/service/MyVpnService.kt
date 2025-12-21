package com.fw.myfirewall.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import com.fw.myfirewall.util.DnsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap


class MyVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.fw.myfirewall.service.CONNECT"
        const val ACTION_DISCONNECT = "com.fw.myfirewall.service.DISCONNECT"
        private const val CHANNEL_ID = "MyFirewallVpnChannel"
        private const val NOTIFICATION_ID = 1
        
        var isServiceRunning = mutableStateOf(false)
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    private var isUrlFilterActive = false
    private val blockedDomains = ConcurrentHashMap.newKeySet<String>()

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
            "com.fw.myfirewall.ACTION_RELOAD_RULES" -> reloadUrlFilterRules()

        }
        return START_STICKY
    }

    /**
     * Démarre la boucle de lecture des paquets IP
     */
    private fun startPacketForwarding(pfd: ParcelFileDescriptor) {
        vpnThread?.interrupt() // Interrompt l'ancien thread
        vpnThread = Thread {
            // Utilisation des flux liés au FileDescriptor passé en paramètre
            val inputStream = FileInputStream(pfd.fileDescriptor)
            val outputStream = FileOutputStream(pfd.fileDescriptor)
            val packet = ByteBuffer.allocate(32767)

            try {
                // On vérifie l'interruption du thread ET l'état du service
                while (!Thread.interrupted() && isServiceRunning.value) {
                    try {
                        val readBytes = inputStream.read(packet.array())
                        if (readBytes > 0) {
                            packet.limit(readBytes)
                            packet.rewind()

                            // Logique de filtrage DNS (IPv4 + UDP + Port 53)
                            var shouldDrop = false
                            if (isUrlFilterActive && readBytes >= 28) {
                                val ipVersion = (packet.get(0).toInt() shr 4) and 0x0F
                                val protocol = packet.get(9).toInt()

                                if (ipVersion == 4 && protocol == 17) { // IPv4 & UDP
                                    val destPort = packet.getShort(22).toInt() and 0xFFFF
                                    // LOG DE TEST :
                                    Log.d("VpnDebug", "Paquet UDP vers port : $destPort")


                                    if (destPort == 53) {
                                        val domain = DnsParser.extractDomain(packet)
                                        if (blockedDomains.contains(domain)) {
                                            Log.i("MyVpnService", "BLOQUÉ : $domain")
                                            shouldDrop = true
                                        }
                                    }
                                }
                            }

                            if (!shouldDrop) {
                                outputStream.write(packet.array(), 0, readBytes)
                            }
                            packet.clear()
                        }
                    } catch (e: IOException) {
                        // C'EST ICI QUE L'ON GÈRE EBADF :
                        // Si le thread est interrompu ou le service arrêté,
                        // l'erreur de lecture est normale (interface fermée).
                        if (Thread.interrupted() || !isServiceRunning.value) break

                        // Si l'erreur contient EBADF, on sort de la boucle proprement
                        if (e.message?.contains("EBADF") == true) break

                        throw e // Relance les autres erreurs IO réelles
                    }
                }
            } catch (e: Exception) {
                // Log seulement si le service est encore censé tourner
                if (isServiceRunning.value && !Thread.interrupted()) {
                    Log.e("MyVpnService", "Error in packet loop", e)
                }
            } finally {
                // Fermeture sécurisée de l'interface associée à CE thread
                try {
                    pfd.close()
                } catch (ignored: Exception) {
                }
            }
        }.apply {
            name = "VpnPacketThread"
            start()
        }
    }


    /**
     * Charge les domaines depuis les préférences ou la DB
     */
    private fun reloadUrlFilterRules() {
        val prefs = getSharedPreferences("MyFirewallPrefs", MODE_PRIVATE)
        isUrlFilterActive = prefs.getBoolean("url_filter_enabled", false)

        serviceScope.launch(Dispatchers.IO) {
            try {
                if (isUrlFilterActive) {
                    val db = AppDatabase.getDatabase(applicationContext)
                    val rules = db.blockRuleDao().getAllRulesSync()

                    val newBlockedDomains = mutableSetOf<String>()

                    rules.forEach { rule ->
                        if (rule.isEnabled) {
                            newBlockedDomains.add(rule.domain)
                            blockedDomains.add(rule.domain.lowercase().trim())
                        }
                    }

                    blockedDomains.clear()
                    blockedDomains.addAll(newBlockedDomains)

                    Log.d(
                        "MyVpnService",
                        "Filtrage URL actif : ${blockedDomains.size} domaines chargés"
                    )
                } else {
                    blockedDomains.clear()
                    Log.d("MyVpnService", "Filtrage URL désactivé : cache vidé")
                }
            } catch (e: Exception) {
                Log.e("MyVpnService", "Erreur lors du rechargement des règles", e)
                blockedDomains.clear()
            }
        }
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
                .setSmallIcon(R.drawable.notification_firewall)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()

            startForeground(NOTIFICATION_ID, notification)
            isServiceRunning.value = true
            reloadUrlFilterRules()
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
                builder.addDnsServer("10.0.0.1") // Adresse fictive que le VPN va intercepter

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

                val oldInterface = vpnInterface
                vpnInterface = builder.establish()

                if (vpnInterface != null) {
                    Log.i("MyVpnService", "VPN Re-established successfully")
                    oldInterface?.close()
                    startPacketForwarding(vpnInterface!!)
                } else {
                    Log.e("MyVpnService", "Failed to establish VPN interface")
                }

            } catch (e: Exception) {
                Log.e("MyVpnService", "Exception in updateVpn()", e)
            }
        }
    }

    private fun disconnect() {
        Log.i("MyVpnService", "Disconnecting VPN")
        try {
            isServiceRunning.value = false
            vpnThread?.interrupt() // Arrête la boucle de lecture des paquets

            if (isObserversRegistered) {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                networkCallback?.let { cm.unregisterNetworkCallback(it) }
                isObserversRegistered = false
            }

            vpnInterface?.close()
            vpnInterface = null
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
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        disconnect()
    }
}