package com.fw.myfirewall.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.TrafficStats
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.fw.myfirewall.data.AppDatabase
import com.fw.myfirewall.data.BlockRuleCategory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
class StatusViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).blockRuleDao()
    private val workManager = WorkManager.getInstance(application)
    private val sharedPrefs = application.getSharedPreferences("MyFirewallPrefs", Context.MODE_PRIVATE)
    private val statsRepository = NetworkStatsRepository(application)

    val advertisingCount = dao.countByCategory(BlockRuleCategory.ADVERTISING)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val annoyancesCount = dao.countByCategory(BlockRuleCategory.ANNOYANCES)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val socialCount = dao.countByCategory(BlockRuleCategory.SOCIAL_MEDIA)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val customCount = dao.countByCategory(BlockRuleCategory.CUSTOM)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _isAdvertisingEnabled = MutableStateFlow(sharedPrefs.getBoolean("advertising_enabled", false))
    val isAdvertisingEnabled = _isAdvertisingEnabled.asStateFlow()

    private val _isAnnoyancesEnabled = MutableStateFlow(sharedPrefs.getBoolean("annoyances_enabled", false))
    val isAnnoyancesEnabled = _isAnnoyancesEnabled.asStateFlow()

    private val _isSocialEnabled = MutableStateFlow(sharedPrefs.getBoolean("social_enabled", false))
    val isSocialEnabled = _isSocialEnabled.asStateFlow()

    private val _isCustomEnabled = MutableStateFlow(sharedPrefs.getBoolean("custom_enabled", false))
    val isCustomEnabled = _isCustomEnabled.asStateFlow()

    private val _wifiUsage = MutableStateFlow(0L)
    val wifiUsage = _wifiUsage.asStateFlow()

    private val _mobileUsage = MutableStateFlow(0L)
    val mobileUsage = _mobileUsage.asStateFlow()
    
    private val _hasUsagePermission = MutableStateFlow(hasUsageStatsPermission(application))
    val hasUsagePermission = _hasUsagePermission.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            "advertising_enabled" -> _isAdvertisingEnabled.value = prefs.getBoolean(key, false)
            "annoyances_enabled" -> _isAnnoyancesEnabled.value = prefs.getBoolean(key, false)
            "social_enabled" -> _isSocialEnabled.value = prefs.getBoolean(key, false)
            "custom_enabled" -> _isCustomEnabled.value = prefs.getBoolean(key, false)
        }
    }

    init {
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        refreshNetworkStats()
    }

    override fun onCleared() {
        super.onCleared()
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    // Ticker that emits every minute
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000)
        }
    }

    val nextUpdateLabel = workManager.getWorkInfosForUniqueWorkFlow("blocklist_update")
        .combine(ticker) { workInfos, currentTime ->
            val info = workInfos.firstOrNull()
            if (info != null && info.state == WorkInfo.State.ENQUEUED) {
                val nextTime = info.nextScheduleTimeMillis
                if (nextTime > currentTime) {
                    val diff = nextTime - currentTime
                    formatDuration(diff)
                } else {
                    "Pending..."
                }
            } else {
                "Not scheduled"
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Loading...")

    private fun formatDuration(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        return if (hours > 0) {
            "${hours}h ${minutes}m"
        } else {
            "${minutes}m"
        }
    }



// ...

    fun refreshNetworkStats() {
        viewModelScope.launch {
            if (hasUsageStatsPermission(getApplication())) {
                // Cas normal : Permission accordée, on prend les stats mensuelles
                _wifiUsage.value = statsRepository.getWifiDataUsage()
                _mobileUsage.value = statsRepository.getMobileDataUsage()
                _hasUsagePermission.value = true
            } else {
                // Cas Fallback : Pas de permission, on prend les stats depuis le boot (TrafficStats)
                // TrafficStats ne sépare pas facilement Wifi/Mobile sans UID,
                // donc on met tout dans "Mobile" ou on affiche un total global.
                // Pour l'exemple, on met tout dans Mobile pour l'affichage :
                val totalBoot = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()

                _wifiUsage.value = 0 // On ne peut pas savoir
                _mobileUsage.value = totalBoot

                _hasUsagePermission.value = false
            }
        }
    }

    fun getBootDataUsage(): Long {
        // Returns total bytes (Mobile + Wifi) since boot
        return TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()
    }
    // Fonction utilitaire pour formater en Go/Mo
    fun formatDataSize(bytes: Long): String {
        if (bytes < 0) return "Permission needed"
        if (bytes == 0L) return "0 Mo"
        val items = arrayOf("o", "Ko", "Mo", "Go", "To")
        val i = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        return String.format("%.2f %s", bytes / Math.pow(1024.0, i.toDouble()), items[i])
    }
}
