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
import com.fw.myfirewall.data.BlockRuleCategory
import com.fw.myfirewall.util.DnsParser
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class MyVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.fw.myfirewall.service.CONNECT"
        const val ACTION_DISCONNECT = "com.fw.myfirewall.service.DISCONNECT"
        private const val CHANNEL_ID = "MyFirewallVpnChannel"
        private const val NOTIFICATION_ID = 1
        private const val DUMMY_DNS = "10.0.0.1"
        private const val DEFAULT_DNS = "8.8.8.8"

        private const val VPN_ADDRESS = "10.0.0.2"

        private const val VPN_PREFIX_LENGTH = 30 // Équivalent à 255.255.255.252

        private const val VPN_DNS_SERVER = "10.0.0.1"

        private const val VPN_MTU = 1500
        var isServiceRunning = mutableStateOf(false)
    }

    private fun getDnsServer(): String {
        val prefs = getSharedPreferences("MyFirewallPrefs", MODE_PRIVATE)
        return prefs.getString("dns_primary", DEFAULT_DNS) ?: DEFAULT_DNS
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    private var isUrlFilterActive = false
    private val blockedDomains = ConcurrentHashMap.newKeySet<String>()
    private val dnsProxyExecutor = Executors.newFixedThreadPool(4)

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var cachedRules: Map<String, AppRule> = emptyMap()
    private var isObserversRegistered = false

    // Dans MyVpnService.kt (remplacez le bloc dans onStartCommand au niveau du caret)
    // Dans MyVpnService.kt
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> connect()
            ACTION_DISCONNECT -> disconnect()
            "com.fw.myfirewall.ACTION_RELOAD_RULES" -> {
                // SÉCURISATION : On utilise Dispatchers.IO pour ne pas bloquer le système (ANR)
                // et on ajoute un bloc try-catch global pour capter l'erreur "Update failed"
                serviceScope.launch(Dispatchers.IO) {
                    try {
                        reloadUrlFilterRules()
                    } catch (e: Exception) {
                        Log.e("MyVpnService", "❌ Rules reload failed in Release mode", e)
                    }
                }
            }
        }
        return START_STICKY
    }


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

                            val firstByte = packet.get(0).toInt()
                            val ipVersion = (firstByte shr 4) and 0x0F

                            if (ipVersion == 4) {
                                val protocol = packet.get(9).toInt()
                                val destPort = packet.getShort(22).toInt() and 0xFFFF

                                if (protocol == 17 && destPort == 53) {
                                    val domain = DnsParser.extractDomain(packet)
                                    if (domain.isNotEmpty()) {
                                        if (blockedDomains.contains(domain)) {
                                            Log.i("MyVpnService", "🛑 BLOCKED: $domain")
                                            val response = DnsParser.createNxDomainResponse(packet, readBytes)
                                            outputStream.write(response)
                                        } else {
                                            val queryData = packet.array().copyOf(readBytes)
                                            dnsProxyExecutor.execute {
                                                proxyDnsQuery(queryData, outputStream)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        packet.clear()
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

    private fun proxyDnsQuery(queryPacket: ByteArray, tunnelOutput: FileOutputStream) {
        try {
            val queryBuffer = ByteBuffer.wrap(queryPacket)
            val ipHeaderLen = (queryBuffer.get(0).toInt() and 0x0F) * 4
            val dnsPayloadOffset = ipHeaderLen + 8
            val dnsQueryPayload = queryPacket.copyOfRange(dnsPayloadOffset, queryPacket.size)

            val socket = DatagramSocket()
            socket.soTimeout = 2000
            val realDnsAddr = InetAddress.getByName(getDnsServer())

            val outPacket = DatagramPacket(dnsQueryPayload, dnsQueryPayload.size, realDnsAddr, 53)
            socket.send(outPacket)

            val responseBuf = ByteArray(1024)
            val inPacket = DatagramPacket(responseBuf, responseBuf.size)
            socket.receive(inPacket)

            val dnsResponsePayload = inPacket.data.copyOf(inPacket.length)
            val fullResponse = DnsParser.createResponsePacket(ByteBuffer.wrap(queryPacket), dnsResponsePayload)

            synchronized(tunnelOutput) {
                tunnelOutput.write(fullResponse)
            }
            socket.close()
        } catch (e: Exception) {
            // Log.w("MyVpnService", "DNS Proxy failed: ${e.message}")
        }
    }

    private fun reloadUrlFilterRules() {
        val prefs = getSharedPreferences("MyFirewallPrefs", MODE_PRIVATE)
        isUrlFilterActive = prefs.getBoolean("url_filter_enabled", false)

        val isAdsEnabled = prefs.getBoolean("advertising_enabled", false)
        val isAnnoyanceEnabled = prefs.getBoolean("annoyances_enabled", false)
        val isSocialEnabled = prefs.getBoolean("social_enabled", false)
        val isCustomEnabled = prefs.getBoolean("custom_enabled", false)

        serviceScope.launch(Dispatchers.IO) {
            try {
                if (isUrlFilterActive) {
                    val db = AppDatabase.getDatabase(applicationContext)
                    val rules = db.blockRuleDao().getAllRulesSync()

                    val newBlockedDomains = mutableSetOf<String>()
                    rules.forEach { rule ->
                        val isCategoryEnabled = when (rule.category) {
                            BlockRuleCategory.ADVERTISING -> isAdsEnabled
                            BlockRuleCategory.ANNOYANCES -> isAnnoyanceEnabled
                            BlockRuleCategory.SOCIAL_MEDIA -> isSocialEnabled
                            BlockRuleCategory.CUSTOM -> isCustomEnabled
                        }
                        if (isCategoryEnabled && rule.isEnabled) {
                            newBlockedDomains.add(rule.domain.lowercase().trim())
                        }
                    }

                    blockedDomains.clear()
                    blockedDomains.addAll(newBlockedDomains)
                } else {
                    blockedDomains.clear()
                }

                // Lancer la mise à jour de l'interface VPN
                updateVpn()

            } catch (e: Exception) {
                Log.e("MyVpnService", "Failed to reload rules", e)
            }
        }
    }

    private fun connect() {
        if (isServiceRunning.value) return
        createNotificationChannel()
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setSmallIcon(R.drawable.notification_firewall)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        isServiceRunning.value = true
        reloadUrlFilterRules()
        startObservers()
    }

    private fun startObservers() {
        if (isObserversRegistered) return
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = updateVpn()
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

    /**
     * Met à jour l'interface VPN.
     * Cette méthode est appelée lors des changements de toggles (Wi-Fi/Mobile/App).
     */
    private fun updateVpn() {
        if (!isServiceRunning.value) return

        // Règle d'or : On déporte toute l'analyse réseau et package sur Dispatchers.IO
        serviceScope.launch(Dispatchers.IO) {
            try {
                // PATCH : On laisse un délai de "respiration" au system_server
                // pour stabiliser les permissions avant d'interroger le Builder.
                delay(300)

                // Vérification de la préparation (nécessaire pour le PermissionMonitor)
                if (prepare(this@MyVpnService) != null) return@launch

                val builder = Builder()
                builder.setSession(getString(R.string.app_name))
                builder.addAddress(VPN_ADDRESS, VPN_PREFIX_LENGTH)
                builder.setMtu(VPN_MTU)

                // Configuration du tunnel selon le mode actif
                if (isUrlFilterActive) {
                    builder.addDnsServer(DUMMY_DNS)
                    builder.addRoute(DUMMY_DNS, 32)
                } else {
                    // Mode Application Filter : on ajoute la route par défaut
                    builder.addRoute("0.0.0.0", 0)

                    val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                    val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

                    // Parcours optimisé des applications
                    val installedApps = packageManager.getInstalledPackages(0)
                    for (app in installedApps) {
                        if (app.packageName == packageName) continue

                        val rule = cachedRules[app.packageName]
                        // Si autorisé sur le réseau actuel, on l'exclut du VPN (Direct Access)
                        val isAllowed = rule?.let {
                            if (isWifi) it.wifiAllowed else it.mobileAllowed
                        } ?: true

                        if (isAllowed) {
                            try {
                                builder.addDisallowedApplication(app.packageName)
                            } catch (e: Exception) {
                                // Ignore les erreurs sur les packages systèmes protégés
                            }
                        }
                    }
                }

                // APPEL CRITIQUE : Retour sur le thread Main UNIQUEMENT pour establish()
                // C'est ici que l'erreur PermissionMonitor se produisait.
                withContext(Dispatchers.Main) {
                    try {
                        val oldInterface = vpnInterface
                        val newInterface = builder.establish()

                        if (newInterface != null) {
                            // On ne ferme l'ancienne interface qu'après avoir réussi la nouvelle
                            oldInterface?.close()
                            vpnInterface = newInterface
                            startPacketForwarding(newInterface)
                        } else {
                            Log.e("MyVpnService", "❌ System refused to establish VPN interface")
                        }
                    } catch (e: Exception) {
                        Log.e("MyVpnService", "❌ PermissionMonitor Sync Error during establish", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("MyVpnService", "Update failed", e)
            }
        }
    }
    private fun disconnect() {
        isServiceRunning.value = false

        // Désenregistrer le callback réseau pour éviter les fuites
        networkCallback?.let {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            try {
                cm.unregisterNetworkCallback(it)
            } catch (_: Exception) {
            }
            networkCallback = null
        }
        isObserversRegistered = false

        vpnThread?.interrupt()
        vpnInterface?.close()
        vpnInterface = null
        stopSelf()
    }

    private fun createNotificationChannel() {
        // La vérification de Build.VERSION est supprimée car minSdk = 29
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        serviceJob.cancel()
        dnsProxyExecutor.shutdown()
        disconnect()
        super.onDestroy()
    }
}
