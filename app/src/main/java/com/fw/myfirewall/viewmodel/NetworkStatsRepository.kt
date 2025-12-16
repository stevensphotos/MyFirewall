package com.fw.myfirewall.viewmodel

import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.RemoteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class NetworkStatsRepository(private val context: Context) {

    private val networkStatsManager =
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager

    // Récupère la consommation Mobile depuis le début du mois (ou boot)
    suspend fun getMobileDataUsage(): Long = withContext(Dispatchers.IO) {
        getUsage(ConnectivityManager.TYPE_MOBILE)
    }

    // Récupère la consommation Wi-Fi depuis le début du mois (ou boot)
    suspend fun getWifiDataUsage(): Long = withContext(Dispatchers.IO) {
        getUsage(ConnectivityManager.TYPE_WIFI)
    }

    private fun getUsage(networkType: Int): Long {
        var totalBytes = 0L

        // Définir la plage de temps (Ex: Depuis le début du mois)
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis // Début du mois actuel

        try {
            // Pour le mobile, on a besoin du SubscriberID, mais null fonctionne souvent pour la SIM par défaut sur les versions récentes
            // Pour le WiFi, le subscriberId est ignoré (on met null)
            val subscriberId: String? = null

            val bucket = networkStatsManager.querySummaryForDevice(
                networkType,
                subscriberId,
                startTime,
                endTime
            )

            totalBytes = bucket.rxBytes + bucket.txBytes // Réception + Envoi

        } catch (e: RemoteException) {
            e.printStackTrace()
        } catch (e: SecurityException) {
            // L'utilisateur n'a pas donné la permission PACKAGE_USAGE_STATS
            e.printStackTrace()
            return -1L // Code pour dire "Permission manquante"
        }

        return totalBytes
    }
}
