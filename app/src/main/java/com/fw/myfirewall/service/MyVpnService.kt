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
import kotlinx.coroutines.delay
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
        vpnThread?.interrupt()
        vpnThread = Thread {
            val inputStream = FileInputStream(pfd.fileDescriptor)
            val outputStream = FileOutputStream(pfd.fileDescriptor)
            val packet = ByteBuffer.allocate(32767)

            try {
                while (!Thread.interrupted() && isServiceRunning.value) {
                    try {
                        val readBytes = inputStream.read(packet.array())
                        if (readBytes > 0) {
                            packet.limit(readBytes)
                            packet.rewind()

                            var shouldDrop = false

                            if (isUrlFilterActive && readBytes >= 28) {
                                val firstByte = packet.get(0).toInt()
                                val ipVersion = (firstByte shr 4) and 0x0F

                                if (ipVersion == 4) {
                                    val protocol = packet.get(9).toInt()
                                    if (protocol == 17 || protocol == 6) {
                                        val destPort = packet.getShort(22).toInt() and 0xFFFF
                                        if (destPort == 853) {
                                            shouldDrop = true
                                        } else if (destPort == 53) {
                                            val domain = DnsParser.extractDomain(packet)
                                            if (domain.isNotEmpty() && blockedDomains.contains(domain)) {
                                                Log.i("MyVpnService", "🛑 BLOQUÉ (v4): $domain")
                                                shouldDrop = true
                                            }
                                        }
                                    }
                                } else if (ipVersion == 6 && readBytes >= 48) {
                                    val nextHeader = packet.get(6).toInt()
                                    if (nextHeader == 17 || nextHeader == 6) {
                                        val destPort = packet.getShort(42).toInt() and 0xFFFF
                                        if (destPort == 853) {
                                            shouldDrop = true
                                        } else if (destPort == 53) {
                                            val domain = DnsParser.extractDomain(packet)
                                            if (domain.isNotEmpty() && blockedDomains.contains(domain)) {
                                                Log.i("MyVpnService", "🛑 BLOQUÉ (v6): $domain")
                                                shouldDrop = true
                                            }
                                        }
                                    }
                                }
                            }

                            if (!shouldDrop) {
                                packet.rewind()
                                outputStream.write(packet.array(), 0, readBytes)
                            }
                            packet.clear()
                        }
                    } catch (e: IOException) {
                        if (Thread.interrupted() || !isServiceRunning.value) break
                        if (e.message?.contains("EBADF") == true) break
                    }
                }
            } catch (e: Exception) {
                Log.e("MyVpnService", "Error in packet loop", e)
            } finally {
                try { pfd.close() } catch (ignored: Exception) {}
            }
        }.apply {
            name = "VpnPacketThread"
            start()
        }
    }

    private fun reloadUrlFilterRules() {
        val prefs = getSharedPreferences("MyFirewallPrefs", MODE_PRIVATE)
        isUrlFilterActive = prefs.getBoolean("url_filter_enabled", false)

        serviceScope.launch(Dispatchers.IO) {
            try {
                if (isUrlFilterActive) {
                    val db = AppDatabase.getDatabase(applicationContext)
                    val rules = db.blockRuleDao().getAllRulesSync()

                    // On crée la liste locale à partir de la DB
                    val domainsFromDb = mutableSetOf<String>()
                    rules.forEach { rule ->
                        if (rule.isEnabled) {
                            domainsFromDb.add(rule.domain.lowercase().trim())
                        }
                    }

                    if (domainsFromDb.isEmpty()) {
                        // Si la DB est vide, on désactive le filtrage pour économiser les ressources
                        isUrlFilterActive = false
                        blockedDomains.clear()
                        Log.d("MyVpnService", "Liste vide en DB : Traitement DNS mis en veille")
                    } else {
                        // On met à jour le set thread-safe utilisé par la boucle de paquets
                        blockedDomains.clear()
                        blockedDomains.addAll(domainsFromDb)
                        Log.d("MyVpnService", "Filtrage URL actif : ${blockedDomains.size} domaines chargés")
                    }
                } else {
                    blockedDomains.clear()
                }

                // Si l'état a changé (ex: passage de actif à inactif), on met à jour le VPN
                updateVpn()

            } catch (e: Exception) {
                Log.e("MyVpnService", "Erreur lors du rechargement des règles", e)
            }
        }
    }


    private fun connect() {
        if (isServiceRunning.value) return
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
            disconnect()
        }
    }

    private fun startObservers() {
        if (isObserversRegistered) return
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = updateVpn()
            override fun onLost(network: Network) = updateVpn()
            override fun onCapabilitiesChanged(n: Network, c: NetworkCapabilities) = updateVpn()
        }
        cm.registerDefaultNetworkCallback(networkCallback!!)

        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.appRuleDao().getAllRules().collect { rules ->
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
                // 1. Délai de sécurité pour le nettoyage système
                delay(1500)

                // 2. Vérification des permissions VPN
                if (prepare(this@MyVpnService) != null) return@launch

                // 1. Initialisation du Builder (Unique)
                val builder = Builder()
                builder.setSession("MyFirewall")
                builder.setMtu(1500)

                // On utilise un petit sous-réseau interne pour l'interface VPN
                builder.addAddress("10.255.255.1", 30)

                // 2. Détermination du réseau actuel (Wifi vs Mobile)
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

                // 3. Configuration des routes et du filtrage
                if (isUrlFilterActive) {
                    // MODE FILTRAGE URL : On capture TOUT pour inspecter le DNS
                    // On utilise de vrais serveurs DNS pour que le système valide la connexion
                    builder.addDnsServer("8.8.8.8")
                    builder.addDnsServer("2001:4860:4860::8888")

                    builder.addRoute("0.0.0.0", 0)
                    builder.addRoute("::", 0)

                    Log.d("MyVpnService", "Routing: Global Mode (URL Filter ON)")
                } else {
                    // MODE FILTRAGE APPS : Split-tunneling
                    builder.addRoute("0.0.0.0", 0)

                    val installedApps = withContext(Dispatchers.IO) {
                        packageManager.getInstalledPackages(0)
                    }

                    for (app in installedApps) {
                        if (app.packageName == packageName) continue
                        val rule = cachedRules[app.packageName]

                        val allowed = if (rule != null) {
                            if (isWifi) rule.wifiAllowed else rule.mobileAllowed
                        } else true

                        // Si l'application est autorisée, elle bypass le VPN pour la performance
                        if (allowed) {
                            try {
                                builder.addDisallowedApplication(app.packageName)
                            } catch (e: Exception) {
                                Log.w("MyVpnService", "Failed to exclude ${app.packageName}", e)
                            }
                        }
                    }
                }

                // 4. Établissement de l'interface
                val oldInterface = vpnInterface
                vpnInterface = builder.establish()

                if (vpnInterface != null) {
                    oldInterface?.close()
                    startPacketForwarding(vpnInterface!!)
                    Log.i("MyVpnService", "VPN Interface established: tun0")
                }

            } catch (e: Exception) {
                Log.e("MyVpnService", "Exception in updateVpn", e)
            }
        }
    }


    private fun disconnect() {
        isServiceRunning.value = false
        vpnThread?.interrupt()
        try {
            vpnInterface?.close()
            vpnInterface = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (e: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "MyFirewall VPN Service"
            val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        disconnect()
    }
}
