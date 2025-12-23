package com.fw.myfirewall.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.TrafficStats
import android.content.pm.ApplicationInfo
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.fw.myfirewall.data.AppDatabase
import com.fw.myfirewall.data.BlockListConfig
import com.fw.myfirewall.data.BlockListImporter
import com.fw.myfirewall.data.BlockRuleCategory
import com.fw.myfirewall.service.MyVpnService
import com.fw.myfirewall.util.HostsGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.log
import kotlin.math.pow

class StatusViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val database = AppDatabase.getDatabase(application)
    private val dao = database.blockRuleDao()
    private val workManager = WorkManager.getInstance(application)
    private val sharedPrefs =
        application.getSharedPreferences("MyFirewallPrefs", Context.MODE_PRIVATE)
    private val statsRepository = NetworkStatsRepository(application)
    private val hostsGenerator = HostsGenerator(dao, application)


    val advertisingCount = dao.countByCategory(BlockRuleCategory.ADVERTISING)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val annoyancesCount = dao.countByCategory(BlockRuleCategory.ANNOYANCES)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val socialCount = dao.countByCategory(BlockRuleCategory.SOCIAL_MEDIA)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val customCount = dao.countByCategory(BlockRuleCategory.CUSTOM)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _isAdvertisingEnabled =
        MutableStateFlow(sharedPrefs.getBoolean("advertising_enabled", false))
    val isAdvertisingEnabled = _isAdvertisingEnabled.asStateFlow()

    private val _isAnnoyancesEnabled =
        MutableStateFlow(sharedPrefs.getBoolean("annoyances_enabled", false))
    val isAnnoyancesEnabled = _isAnnoyancesEnabled.asStateFlow()

    private val _userAppsCount = MutableStateFlow(0)
    val userAppsCount = _userAppsCount.asStateFlow()

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

    private val _downloadSpeed = MutableStateFlow(0L)
    val downloadSpeed = _downloadSpeed.asStateFlow()

    private val _uploadSpeed = MutableStateFlow(0L)
    val uploadSpeed = _uploadSpeed.asStateFlow()

    private val _speedMode = MutableStateFlow("Total") // "Total", "Wi-Fi", "Mobile"
    val speedMode = _speedMode.asStateFlow()

    private var speedUpdateJob: Job? = null

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
        startSpeedMonitoring()
        getFilteredAppsCount()
    }

    // --- QUICK SETTINGS TOGGLES ---

    fun toggleAdvertising() {
        val newState = !_isAdvertisingEnabled.value
        Log.d("StatusViewModel", "Toggling Advertising to $newState")
        _isAdvertisingEnabled.value = newState
        sharedPrefs.edit().putBoolean("advertising_enabled", newState).apply()
        handleRuleToggle(newState, BlockRuleCategory.ADVERTISING, BlockListConfig.URL_ADVERTISING)
    }

    fun toggleAnnoyances() {
        val newState = !_isAnnoyancesEnabled.value
        Log.d("StatusViewModel", "Toggling Annoyances to $newState")
        _isAnnoyancesEnabled.value = newState
        sharedPrefs.edit().putBoolean("annoyances_enabled", newState).apply()
        handleRuleToggle(newState, BlockRuleCategory.ANNOYANCES, BlockListConfig.URL_ANNOYANCES)
    }

    fun toggleSocial() {
        val newState = !_isSocialEnabled.value
        Log.d("StatusViewModel", "Toggling Social to $newState")
        _isSocialEnabled.value = newState
        sharedPrefs.edit().putBoolean("social_enabled", newState).apply()
        handleRuleToggle(newState, BlockRuleCategory.SOCIAL_MEDIA, BlockListConfig.URL_SOCIAL)
    }

    fun toggleCustom() {
        val newState = !_isCustomEnabled.value
        Log.d("StatusViewModel", "Toggling Custom to $newState")
        _isCustomEnabled.value = newState
        sharedPrefs.edit().putBoolean("custom_enabled", newState).apply()
        viewModelScope.launch {
            hostsGenerator.generateHostsFile()
            notifyVpnRulesChanged()
        }
    }

    private fun handleRuleToggle(enabled: Boolean, category: BlockRuleCategory, url: String) {
        viewModelScope.launch {
            if (enabled) {
                val count = withContext(Dispatchers.IO) {
                    dao.countByCategory(category).first()
                }

                if (count == 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Downloading block list...", Toast.LENGTH_SHORT).show()
                    }
                    val imported = withContext(Dispatchers.IO) {
                        BlockListImporter.importFromUrl(context, url, category)
                    }
                    withContext(Dispatchers.Main) {
                        if (imported > 0) {
                            Toast.makeText(context, "Imported $imported rules", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to import rules", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            withContext(Dispatchers.IO) {
                hostsGenerator.generateHostsFile()
            }
            notifyVpnRulesChanged()
        }
    }

    private fun notifyVpnRulesChanged() {
        val intent = Intent(context, MyVpnService::class.java)
        intent.action = "com.fw.myfirewall.ACTION_RELOAD_RULES"
        context.startService(intent)
    }

    private fun startSpeedMonitoring() {
        speedUpdateJob?.cancel()
        speedUpdateJob = viewModelScope.launch {
            var lastTotalRx = TrafficStats.getTotalRxBytes()
            var lastTotalTx = TrafficStats.getTotalTxBytes()
            var lastMobileRx = TrafficStats.getMobileRxBytes()
            var lastMobileTx = TrafficStats.getMobileTxBytes()

            while (isActive) {
                delay(1000)

                val curTotalRx = TrafficStats.getTotalRxBytes()
                val curTotalTx = TrafficStats.getTotalTxBytes()
                val curMobileRx = TrafficStats.getMobileRxBytes()
                val curMobileTx = TrafficStats.getMobileTxBytes()

                val diffTotalRx = if (lastTotalRx >= 0) curTotalRx - lastTotalRx else 0
                val diffTotalTx = if (lastTotalTx >= 0) curTotalTx - lastTotalTx else 0
                val diffMobileRx = if (lastMobileRx >= 0) curMobileRx - lastMobileRx else 0
                val diffMobileTx = if (lastMobileTx >= 0) curMobileTx - lastMobileTx else 0

                val diffWifiRx = (diffTotalRx - diffMobileRx).coerceAtLeast(0)
                val diffWifiTx = (diffTotalTx - diffMobileTx).coerceAtLeast(0)

                when (_speedMode.value) {
                    "Total" -> {
                        _downloadSpeed.value = diffTotalRx.coerceAtLeast(0)
                        _uploadSpeed.value = diffTotalTx.coerceAtLeast(0)
                    }
                    "Wi-Fi" -> {
                        _downloadSpeed.value = diffWifiRx
                        _uploadSpeed.value = diffWifiTx
                    }
                    "Mobile" -> {
                        _downloadSpeed.value = diffMobileRx.coerceAtLeast(0)
                        _uploadSpeed.value = diffMobileTx.coerceAtLeast(0)
                    }
                }

                lastTotalRx = curTotalRx
                lastTotalTx = curTotalTx
                lastMobileRx = curMobileRx
                lastMobileTx = curMobileTx
            }
        }
    }

    fun toggleSpeedMode() {
        _speedMode.value = when (_speedMode.value) {
            "Total" -> "Wi-Fi"
            "Wi-Fi" -> "Mobile"
            else -> "Total"
        }
    }

    override fun onCleared() {
        super.onCleared()
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        speedUpdateJob?.cancel()
    }

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
                } else "Pending..."
            } else "Not scheduled"
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Loading...")

    private fun formatDuration(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    fun refreshNetworkStats() {
        viewModelScope.launch {
            if (hasUsageStatsPermission(getApplication())) {
                _wifiUsage.value = statsRepository.getWifiDataUsage()
                _mobileUsage.value = statsRepository.getMobileDataUsage()
                _hasUsagePermission.value = true
            } else {
                _wifiUsage.value = 0
                _mobileUsage.value = getBootDataUsage()
                _hasUsagePermission.value = false
            }
        }
    }

    fun getBootDataUsage(): Long {
        return TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()
    }

    fun getFilteredAppsCount() {
        viewModelScope.launch(Dispatchers.Default) {
            val pm = getApplication<Application>().packageManager
            val allApps = pm.getInstalledPackages(0)
            val userCount = allApps.count { pkg ->
                val appInfo = pkg.applicationInfo
                appInfo != null && (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
            }
            _userAppsCount.value = userCount
        }
    }

    fun formatDataSize(bytes: Long): String {
        if (bytes < 0) return "N/A"
        if (bytes == 0L) return "0 B"
        val items = arrayOf("B", "KB", "MB", "GB", "TB")
        val i = (log(bytes.toDouble(), 1024.0)).toInt()
        val index = i.coerceIn(0, items.size - 1)
        return String.format(Locale.getDefault(), "%.2f %s", bytes / 1024.0.pow(index.toDouble()), items[index])
    }

    fun formatSpeed(bytesPerSec: Long): String {
        return formatDataSize(bytesPerSec) + "/s"
    }
}
