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
        private const val REAL_DNS = "9.9.9.9"

        var isServiceRunning = mutableStateOf(false)
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> connect()
            ACTION_DISCONNECT -> disconnect()
            "com.fw.myfirewall.ACTION_RELOAD_RULES" -> reloadUrlFilterRules()
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
            val realDnsAddr = InetAddress.getByName(REAL_DNS)
            
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

        // Read category states to filter DB results
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
                    Log.d("MyVpnService", "Reloaded: ${blockedDomains.size} domains active across enabled categories")
                } else {
                    blockedDomains.clear()
                }
                
                // Refresh VPN configuration if mode changed
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

    private fun updateVpn() {
        if (!isServiceRunning.value) return

        serviceScope.launch {
            try {
                delay(500)
                if (prepare(this@MyVpnService) != null) return@launch

                val builder = Builder()
                builder.setSession("MyFirewall")
                builder.addAddress("10.0.0.2", 32)
                builder.setMtu(1500)

                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

                if (isUrlFilterActive) {
                    builder.addDnsServer(DUMMY_DNS)
                    builder.addRoute(DUMMY_DNS, 32)
                } else {
                    builder.addRoute("0.0.0.0", 0)
                    val installedApps = withContext(Dispatchers.IO) {
                        packageManager.getInstalledPackages(0)
                    }
                    for (app in installedApps) {
                        if (app.packageName == packageName) continue
                        val rule = cachedRules[app.packageName]
                        val allowed = if (rule != null) (if (isWifi) rule.wifiAllowed else rule.mobileAllowed) else true
                        if (allowed) {
                            try { builder.addDisallowedApplication(app.packageName) } catch (_: Exception) {}
                        }
                    }
                }

                val oldInterface = vpnInterface
                vpnInterface = builder.establish()
                oldInterface?.close()
                vpnInterface?.let { startPacketForwarding(it) }

            } catch (e: Exception) {
                Log.e("MyVpnService", "Update failed", e)
            }
        }
    }

    private fun disconnect() {
        isServiceRunning.value = false
        vpnThread?.interrupt()
        vpnInterface?.close()
        vpnInterface = null
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Firewall", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceJob.cancel()
        dnsProxyExecutor.shutdown()
        disconnect()
        super.onDestroy()
    }
}
