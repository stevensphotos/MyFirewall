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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ############################################################################
 * SERVICE: MyVpnService
 * Rôle : Cœur du moteur de filtrage. Gère le tunnel VPN local (VpnService),
 * l'interception des paquets DNS et le filtrage par application (UID).
 * ############################################################################
 */
class MyVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.fw.myfirewall.service.CONNECT"
        const val ACTION_DISCONNECT = "com.fw.myfirewall.service.DISCONNECT"
        private const val CHANNEL_ID = "MyFirewallVpnChannel"
        // Nouveau canal pour les alertes de déconnexion critique
        private const val CHANNEL_ALERT_ID = "MyFirewallAlertChannel"
        private const val NOTIFICATION_ID = 1
        private const val ALERT_NOTIFICATION_ID = 999
        private const val DUMMY_DNS = "10.0.0.1"
        private const val DEFAULT_DNS = "8.8.8.8"
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_PREFIX_LENGTH = 30
        private const val VPN_MTU = 1500

        // [SORTIE ÉTAT] : État global de fonctionnement observé par l'UI
        var isServiceRunning = mutableStateOf(false)
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    private var isUrlFilterActive = false
    private val blockedDomains = ConcurrentHashMap.newKeySet<String>()
    private val dnsProxyExecutor = Executors.newFixedThreadPool(2)
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var cachedRules: Map<String, AppRule> = emptyMap()
    private var isObserversRegistered = false

    // Contrôle atomique pour la boucle de traitement des paquets
    private val isRunningAtomic = AtomicBoolean(false)
    private var isUpdatingVpn = false

    // [ENTRÉE PRÉFÉRENCES] : Récupère le serveur DNS configuré dans SettingsViewModel
    private fun getDnsServer(): String {
        val prefs = getSharedPreferences("MyFirewallPrefs", MODE_PRIVATE)
        return prefs.getString("dns_primary", DEFAULT_DNS) ?: DEFAULT_DNS
    }

    /**
     * ROUTINE : onStartCommand
     * Point d'entrée des commandes envoyées par les ViewModels ou le système.
     * [ENTRÉE INTENT] : Actions Connect, Disconnect ou ReloadRules.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> connect()
            ACTION_DISCONNECT -> disconnect()
            "com.fw.myfirewall.ACTION_RELOAD_RULES" -> {
                serviceScope.launch(Dispatchers.IO) {
                    try {
                        reloadUrlFilterRules()
                    } catch (e: Exception) {
                        Log.e("MyVpnService", "Reload rules failed", e)
                    }
                }
            }
        }
        return START_STICKY
    }

    /**
     * ROUTINE : startPacketForwarding
     * Gère la boucle de lecture/écriture du descripteur de fichier VPN.
     * [ENTRÉE TUNNEL] : Flux de données brutes via ParcelFileDescriptor.
     * [SORTIE TUNNEL] : Réinjection des réponses DNS forgées (NXDOMAIN).
     */
    private fun startPacketForwarding(pfd: ParcelFileDescriptor) {
        vpnThread?.interrupt()
        vpnThread = Thread {
            val inputStream = FileInputStream(pfd.fileDescriptor)
            val outputStream = FileOutputStream(pfd.fileDescriptor)
            // Buffer de taille optimale pour le tunnel
            val packet = ByteBuffer.allocate(Short.MAX_VALUE.toInt())

            try {
                // Utilisation de l'AtomicBoolean pour un contrôle de flux sans surconsommation CPU
                while (isRunningAtomic.get() && !Thread.interrupted()) {
                    val readBytes = inputStream.read(packet.array())

                    if (readBytes > 0) {
                        packet.limit(readBytes)
                        packet.rewind()

                        val firstByte = packet.get(0).toInt()
                        val ipVersion = (firstByte shr 4) and 0x0F

                        // Analyse du protocole IP (v4 uniquement ici)
                        if (ipVersion == 4) {
                            val protocol = packet.get(9).toInt()
                            // Extraction du port destination (DNS = 53)
                            val destPort = ((packet.get(22).toInt() and 0xFF) shl 8) or
                                    (packet.get(23).toInt() and 0xFF)

                            // Interception DNS (Port 53, UDP 17)
                            if (protocol == 17 && destPort == 53) {
                                val domain = DnsParser.extractDomain(packet)
                                if (domain.isNotEmpty()) {
                                    // [LOGIQUE FILTRAGE] : Vérification contre la ConcurrentHashMap
                                    if (blockedDomains.contains(domain)) {
                                        // [SORTIE] : Forge d'une réponse de blocage (NXDOMAIN)
                                        val response = DnsParser.createNxDomainResponse(packet, readBytes)
                                        outputStream.write(response)
                                    } else {
                                        // [SORTIE PROXY] : Transfert vers le résolveur réel
                                        val queryData = packet.array().copyOf(readBytes)
                                        dnsProxyExecutor.execute {
                                            proxyDnsQuery(queryData, outputStream)
                                        }
                                    }
                                }
                            }
                        }
                    } else if (readBytes <= 0) {
                        // PATCH CPU : Évite la boucle infinie à vide (consommation batterie)
                        Thread.sleep(10)
                    }
                    packet.clear()
                }
            } catch (e: Exception) {
                if (isRunningAtomic.get()) {
                    Log.w("MyVpnService", "VpnThread interrupted or closed")
                }
            } finally {
                try {
                    inputStream.close()
                    outputStream.close()
                } catch (_: Exception) { }
            }
        }.apply {
            // Utilisation de NORM_PRIORITY pour permettre au système de gérer l'énergie
            priority = Thread.NORM_PRIORITY
            name = "VpnPacketThread"
            start()
        }
    }

    /**
     * ROUTINE : proxyDnsQuery
     * Relais DNS standard. Reçoit une requête du tunnel, la résout via Internet,
     * et réinjecte la réponse dans le tunnel VPN.
     * [ENTRÉE RÉSEAU] : Réponse UDP du serveur DNS externe.
     */
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

            // [SORTIE TUNNEL] : Injection sécurisée par verrou synchronisé
            synchronized(tunnelOutput) {
                tunnelOutput.write(fullResponse)
            }
            socket.close()
        } catch (e: Exception) {
            // Silence pour éviter les pics CPU sur timeout réseau
        }
    }

    /**
     * ROUTINE : reloadUrlFilterRules
     * Synchronise la liste des domaines bloqués en mémoire.
     * [ENTRÉE PRÉFÉRENCES] : État des switchs Advertising, Social, etc.
     * [ENTRÉE ROOM DB] : Charge la liste de domaines depuis BlockRuleDao.
     */
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
                // [SORTIE INTERNE] : Reconfiguration du moteur VPN
                updateVpn()
            } catch (e: Exception) {
                Log.e("MyVpnService", "Failed to reload rules", e)
            }
        }
    }

    /**
     * ROUTINE : connect
     * Initialisation du service Foreground.
     * [SORTIE NOTIFICATION] : Affiche la notification persistante de filtrage.
     */
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

        isRunningAtomic.set(true)
        isServiceRunning.value = true
        reloadUrlFilterRules()
        startObservers()
    }

    /**
     * ROUTINE : startObservers
     * Écoute les changements dynamiques du système et de la base de données.
     * [ENTRÉE RÉSEAU] : Écoute les changements Wi-Fi/Mobile via ConnectivityManager.
     * [ENTRÉE ROOM DB] : Flow dynamique sur les règles d'applications (AppRule).
     */
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
                // [SORTIE INTERNE] : Rafraîchit les exclusions d'applications
                updateVpn()
            }
        }
        isObserversRegistered = true
    }

    /**
     * ROUTINE : updateVpn
     * Logique de construction et de reconstruction de l'interface TUN.
     * [SORTIE SYSTÈME] : Établissement du tunnel via Builder.establish().
     */
    private fun updateVpn() {
        if (!isServiceRunning.value || isUpdatingVpn) return

        serviceScope.launch(Dispatchers.IO) {
            isUpdatingVpn = true
            try {
                // Délai préventif pour la synchronisation système Android
                delay(1000)

                if (prepare(this@MyVpnService) != null) {
                    isUpdatingVpn = false
                    return@launch
                }

                val builder = Builder()
                builder.setSession(getString(R.string.app_name))
                builder.addAddress(VPN_ADDRESS, VPN_PREFIX_LENGTH)
                builder.setMtu(VPN_MTU)

                // CONFIGURATION MOTEUR URL (DNS REDIRECT)
                if (isUrlFilterActive) {
                    builder.addDnsServer(DUMMY_DNS)
                    builder.addRoute(DUMMY_DNS, 32)
                } else {
                    // CONFIGURATION MOTEUR APP (PER-UID BLOCKING)
                    builder.addRoute("0.0.0.0", 0)
                    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                    val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

                    // [ENTRÉE SYSTÈME] : Parcours de toutes les applications installées
                    val installedApps = packageManager.getInstalledPackages(0)
                    for (app in installedApps) {
                        if (app.packageName == packageName) continue
                        val rule = cachedRules[app.packageName]
                        val allowed = if (rule != null) {
                            if (isWifi) rule.wifiAllowed else rule.mobileAllowed
                        } else true

                        // [SORTIE CONFIG] : Autorise l'app à sortir du tunnel si elle n'est pas bloquée
                        if (allowed) {
                            try {
                                builder.addDisallowedApplication(app.packageName)
                            } catch (e: Exception) {}
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    try {
                        val oldInterface = vpnInterface
                        val newInterface = builder.establish()
                        if (newInterface != null) {
                            oldInterface?.close()
                            vpnInterface = newInterface
                            // [DÉMARRAGE ROUTINE] : Lance la boucle de forwarding paquets
                            startPacketForwarding(newInterface)
                        }
                    } catch (e: Exception) {
                        Log.e("MyVpnService", "Establish VPN failed", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("MyVpnService", "Update failed", e)
            } finally {
                isUpdatingVpn = false
            }
        }
    }

    /**
     * ROUTINE : disconnect
     * Nettoyage complet et arrêt des threads.
     * [SORTIE SYSTÈME] : Fermeture du descripteur TUN et arrêt de la notification.
     */
    private fun disconnect() {
        isRunningAtomic.set(false)
        isServiceRunning.value = false

        networkCallback?.let {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            try { cm.unregisterNetworkCallback(it) } catch (e: Exception) {}
        }

        vpnThread?.interrupt()
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e("MyVpnService", "Error closing interface", e)
        }
        vpnInterface = null
        stopSelf()
    }

        /**
     * ROUTINE : onRevoke     * Appelée par Android quand un autre VPN démarre ou que la permission est retirée.
     * [SORTIE NOTIFICATION] : Informe l'utilisateur que la protection est désactivée.
     */
    override fun onRevoke() {
        // 1. Envoyer une notification d'alerte immédiate (le tunnel est déjà coupé par Android)
        sendRevokeNotification()

        // 2. Nettoyer les ressources et arrêter le service
        disconnect()
        super.onRevoke()
    }

    /**
     * ROUTINE : sendRevokeNotification
     * Crée une notification d'alerte haute priorité indiquant que MyFirewall n'est plus prioritaire.
     */
    private fun sendRevokeNotification() {
        val manager = getSystemService(NotificationManager::class.java)

        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ALERT_ID)
            .setContentTitle(getString(R.string.notification_revoked_title))
            .setContentText(getString(R.string.notification_revoked_content))
            .setSmallIcon(R.drawable.notification_firewall)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager?.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)

        // Canal Standard (Protection Active - Importance Basse pour discrétion)
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
        }

        // Nouveau Canal d'Alerte (Désactivation forcée - Importance Haute)
        val alertChannel = NotificationChannel(
            CHANNEL_ALERT_ID,
            getString(R.string.notification_revoked_title),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notification_content)
            enableVibration(true)
        }

        manager?.createNotificationChannel(serviceChannel)
        manager?.createNotificationChannel(alertChannel)
    }

    override fun onDestroy() {
        serviceJob.cancel()
        dnsProxyExecutor.shutdown()
        disconnect()
        super.onDestroy()
    }
}
