package com.fw.myfirewall.service

import android.R.attr.version
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
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.getSystemService
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
                            packet.rewind() // Position à 0 pour lecture

                            var shouldDrop = false

                            // Filtrage uniquement si le filtrage d'URL est actif
                            if (isUrlFilterActive) {
                                val firstByte = packet.get(0).toInt()
                                val ipVersion = (firstByte shr 4) and 0x0F

                                if (ipVersion == 4 && readBytes >= 28) {
                                    val protocol = packet.get(9).toInt()
                                    if (protocol == 17) { // UDP
                                        val destPort = packet.getShort(22).toInt() and 0xFFFF
                                        if (destPort == 53) {
                                            val domain = DnsParser.extractDomain(packet)
                                            if (domain.isNotEmpty() && blockedDomains.contains(
                                                    domain
                                                )
                                            ) {
                                                Log.i("MyVpnService", "🛑 BLOQUÉ (v4): $domain")
                                                shouldDrop = true
                                            }
                                        } else if (destPort == 853) {
                                            shouldDrop = true // Bloquer DNS chiffré
                                        }
                                    }
                                } else if (ipVersion == 6 && readBytes >= 48) {
                                    val nextHeader = packet.get(6).toInt()
                                    if (nextHeader == 17) { // UDP
                                        val destPort = packet.getShort(42).toInt() and 0xFFFF
                                        if (destPort == 53) {
                                            val domain = DnsParser.extractDomain(packet)
                                            if (domain.isNotEmpty() && blockedDomains.contains(
                                                    domain
                                                )
                                            ) {
                                                Log.i("MyVpnService", "🛑 BLOQUÉ (v6): $domain")
                                                shouldDrop = true
                                            }
                                        } else if (destPort == 853) {
                                            shouldDrop = true
                                        }
                                    }
                                }
                            }

                            // LOGIQUE CRUCIALE : Si on ne drop pas, on renvoie EXACTEMENT le paquet original
                            if (!shouldDrop) {
                                packet.rewind() // S'assurer que le curseur est revenu au début de l'IP Header
                                outputStream.write(packet.array(), 0, readBytes)
                            }

                            packet.clear()
                        }
                    } catch (e: IOException) {
                        if (Thread.interrupted() || !isServiceRunning.value) break
                        if (e.message?.contains("EBADF") == true) break
                        throw e
                    }
                }
            } catch (e: Exception) {
                if (isServiceRunning.value && !Thread.interrupted()) {
                    Log.e("MyVpnService", "Error in packet loop", e)
                }
            } finally {
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
                            newBlockedDomains.add(rule.domain.lowercase().trim())
                        }
                    }

                    blockedDomains.clear()
                    blockedDomains.addAll(newBlockedDomains)
                    newBlockedDomains.add("google.com")

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

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
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
                // Vérifier si une configuration VPN est déjà en cours de préparation par le système
                if (prepare(this@MyVpnService) != null) return@launch

                val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                val isMobile = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

                val builder = Builder()
                builder.setSession("MyFirewall")
                builder.setMtu(1500)
                builder.addAddress("10.0.0.2", 32)

                // --- STRATÉGIE DE ROUTAGE ---

                if (isUrlFilterActive) {
                    /**
                     * MODE FILTRAGE URL ACTIF :
                     * On doit capturer le trafic de TOUTES les applications pour voir leurs requêtes DNS.
                     * Si on utilisait addDisallowedApplication ici, les applis autorisées contourneraient
                     * le VPN et leurs requêtes DNS (port 53) seraient invisibles.
                     */
                    builder.addDnsServer("10.0.0.1") // Interception DNS forcée
                    builder.addRoute("0.0.0.0", 0)   // Capturer tout le trafic IPv4
                    builder.addRoute("::", 0)        // Capturer tout le trafic IPv6

                    Log.d("MyVpnService", "Routing: Global capture mode (URL Filter ON)")
                } else {
                    /**
                     * MODE FILTRAGE APPLICATIONS UNIQUEMENT :
                     */
                    builder.addRoute("0.0.0.0", 0)
                    val pm = packageManager
                    val installedApps = withContext(Dispatchers.IO) { pm.getInstalledPackages(0) }

                    for (app in installedApps) {
                        if (app.packageName == packageName) continue
                        val rule = cachedRules[app.packageName]
                        val allowed = if (rule != null) {
                            if (isWifi) rule.wifiAllowed else rule.mobileAllowed
                        } else true

                        if (allowed) {
                            try {
                                builder.addDisallowedApplication(app.packageName)
                            } catch (e: Exception) {
                                Log.w("MyVpnService", "Failed to exclude ${app.packageName}", e)
                            }
                        }
                    }
                }

                val oldInterface = vpnInterface
                vpnInterface = builder.establish()

                if (vpnInterface != null) {
                    oldInterface?.close()
                    startPacketForwarding(vpnInterface!!)
                    Log.i("MyVpnService", "VPN established (tun0)")
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
        } catch (e: Exception) {
            Log.e("MyVpnService", "Failed to disconnect VPN", e)
        }
    }

    private fun createNotificationChannel() {
        // Les canaux de notification sont nécessaires uniquement sur API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "MyFirewall VPN Service"
            val descriptionText = "Affiche le statut de protection du pare-feu"
            val importance = NotificationManager.IMPORTANCE_LOW

            // CHANNEL_ID est bien reconnu car nous sommes à l'intérieur de la classe
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            // Enregistrer le canal auprès du système
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        disconnect()
    }
} // <--- CETTE ACCOLADE FERME LA CLASSE MyVpnService

