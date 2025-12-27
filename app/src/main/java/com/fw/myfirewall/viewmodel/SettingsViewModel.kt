package com.fw.myfirewall.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fw.myfirewall.data.AppDatabase
import com.fw.myfirewall.service.MyVpnService
import com.fw.myfirewall.util.HostsGenerator
import com.fw.myfirewall.util.RootUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.Manifest
import android.widget.Toast
import com.fw.myfirewall.R



// APPLICATION LICENCE
private const val APP_LICENSE = "GPLv3"
private const val REPO_URL = "https://github.com/votre_pseudo/MyFirewall"
fun getLicenseInfo(): String = APP_LICENSE
fun getRepoUrl(): String = REPO_URL


class SettingsViewModel(application: Application) : AndroidViewModel(application) {




    private val sharedPrefs =
        application.getSharedPreferences("MyFirewallPrefs", Context.MODE_PRIVATE)
    private val hostsGenerator: HostsGenerator

    // --- FILTERS STATES ---
    private val _isUrlFilterEnabled = MutableStateFlow(false)
    val isUrlFilterEnabled: StateFlow<Boolean> = _isUrlFilterEnabled.asStateFlow()

    private val _isAppFilterEnabled = MutableStateFlow(false)
    val isAppFilterEnabled: StateFlow<Boolean> = _isAppFilterEnabled.asStateFlow()

    private val _isRootEnabled = MutableStateFlow(false)
    val isRootEnabled: StateFlow<Boolean> = _isRootEnabled.asStateFlow()

    // --- SYSTEM STATES ---
    private val _isStartOnBootEnabled = MutableStateFlow(false)
    val isStartOnBootEnabled: StateFlow<Boolean> = _isStartOnBootEnabled.asStateFlow()

    private val _isPureBlackEnabled = MutableStateFlow(false)
    val isPureBlackEnabled: StateFlow<Boolean> = _isPureBlackEnabled.asStateFlow()

    private val _isDynamicColorEnabled = MutableStateFlow(true)
    val isDynamicColorEnabled: StateFlow<Boolean> = _isDynamicColorEnabled.asStateFlow()

    private val _themeMode = MutableStateFlow("system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _languageCode = MutableStateFlow("system")
    val languageCode: StateFlow<String> = _languageCode.asStateFlow()

    // --- PERMISSIONS STATES ---
    private val _isUsageStatsPermissionGranted = MutableStateFlow(false)
    val isUsageStatsPermissionGranted: StateFlow<Boolean> =
        _isUsageStatsPermissionGranted.asStateFlow()
    private val _hasNotificationPermission = MutableStateFlow(false)
    val hasNotificationPermission: StateFlow<Boolean> = _hasNotificationPermission.asStateFlow()

    // --- PERMISSIONS UPDATERS ---
    fun updateUsageStatsStatus(context: Context) {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        // Suppression de la vérification Build.VERSION : unsafeCheckOpNoThrow est natif en API 29
        val mode = appOps.unsafeCheckOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        _isUsageStatsPermissionGranted.value = (mode == android.app.AppOpsManager.MODE_ALLOWED)
    }

    fun updateNotificationPermission(granted: Boolean) {
        _hasNotificationPermission.value = granted
    }

    fun checkInitialPermissions(context: Context) {
        updateUsageStatsStatus(context)

        // TIRAMISU est l'API 33. On garde la vérification car 33 > 29.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val status = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            _hasNotificationPermission.value = (status == PackageManager.PERMISSION_GRANTED)
        } else {
            // Pour les versions entre 29 et 32, les notifications sont autorisées par défaut
            _hasNotificationPermission.value = true
        }
    }


    // --- ROOT STATES ---



    // --- ROOT STATES ---
    private val _rootCheckResult = MutableStateFlow<Boolean?>(null)
    val rootCheckResult: StateFlow<Boolean?> = _rootCheckResult.asStateFlow()

    private val _isRootPossible = MutableStateFlow(RootUtils.isDeviceRooted())
    val isRootPossible: StateFlow<Boolean> = _isRootPossible.asStateFlow()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        viewModelScope.launch {
            when (key) {
                "url_filter_enabled" -> _isUrlFilterEnabled.value = prefs.getBoolean(key, false)
                "app_filter_enabled" -> _isAppFilterEnabled.value = prefs.getBoolean(key, false)
                "root_enabled" -> _isRootEnabled.value = prefs.getBoolean(key, false)
                "start_on_boot" -> _isStartOnBootEnabled.value = prefs.getBoolean(key, false)
                "pure_black_enabled" -> _isPureBlackEnabled.value = prefs.getBoolean(key, false)
                "dynamic_color_enabled" -> _isDynamicColorEnabled.value =
                    prefs.getBoolean(key, true)

                "theme_mode" -> _themeMode.value = prefs.getString(key, "system") ?: "system"
                "language_code" -> _languageCode.value = prefs.getString(key, "system") ?: "system"
            }
        }
    }

    init {
        val db = AppDatabase.getDatabase(application)
        hostsGenerator = HostsGenerator(db.blockRuleDao(), application)

        // Init values from SharedPreferences
        _isUrlFilterEnabled.value = sharedPrefs.getBoolean("url_filter_enabled", false)
        _isAppFilterEnabled.value = sharedPrefs.getBoolean("app_filter_enabled", false)
        _isRootEnabled.value = sharedPrefs.getBoolean("root_enabled", false)
        _isStartOnBootEnabled.value = sharedPrefs.getBoolean("start_on_boot", false)
        _isPureBlackEnabled.value = sharedPrefs.getBoolean("pure_black_enabled", false)
        _isDynamicColorEnabled.value = sharedPrefs.getBoolean("dynamic_color_enabled", true)
        _themeMode.value = sharedPrefs.getString("theme_mode", "system") ?: "system"
        _languageCode.value = sharedPrefs.getString("language_code", "system") ?: "system"

        sharedPrefs.registerOnSharedPreferenceChangeListener(prefListener)

        if (_isRootEnabled.value) {
            checkRoot(manualToggle = false)
        }
    }



    private fun notifyVpnRulesChanged() {
        val intent = Intent(getApplication(), MyVpnService::class.java)
        intent.action = "com.fw.myfirewall.ACTION_RELOAD_RULES"
        getApplication<Application>().startService(intent)
    }

    private fun startVpnService() {
        // Apply the <not> condition (XOR logic) to ensure only one is active
        // and we only trigger start if at least one is intended to be on.
        if (_isUrlFilterEnabled.value != _isAppFilterEnabled.value) {
            val intent = Intent(getApplication(), MyVpnService::class.java)
            intent.action = MyVpnService.ACTION_CONNECT
            getApplication<Application>().startForegroundService(intent)
        }
    }

    private fun stopVpnService() {
        val intent = Intent(getApplication(), MyVpnService::class.java)
        intent.action = MyVpnService.ACTION_DISCONNECT
        getApplication<Application>().startService(intent)
    }

    /**
     * Toggles the VPN-based URL filter.
     * Mutually exclusive with Applications Filter and Hosts-based filter.
     */
    fun toggleUrlFilter(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                // 1. Handle mutual exclusivity with Hosts-based filter (unchanged)
                val rootEnabled = sharedPrefs.getBoolean("root_enabled", false)
                if (rootEnabled) {
                    withContext(Dispatchers.IO) {
                        sharedPrefs.edit().apply {
                            putBoolean("root_enabled", false)
                            putBoolean("advertising_enabled", false)
                            putBoolean("annoyances_enabled", false)
                            putBoolean("social_enabled", false)
                            putBoolean("custom_enabled", false)
                        }.commit()
                        hostsGenerator.generateDefaultHostsFile()
                    }
                    _isRootEnabled.value = false
                }

                // 2. Handle mutual exclusivity with Applications Filter (NEW <not> condition)
                if (_isAppFilterEnabled.value) {
                    _isAppFilterEnabled.value = false
                    withContext(Dispatchers.IO) {
                        sharedPrefs.edit().putBoolean("app_filter_enabled", false).commit()
                    }
                }

                _isUrlFilterEnabled.value = true
                withContext(Dispatchers.IO) {
                    sharedPrefs.edit().putBoolean("url_filter_enabled", true).commit()
                }

                startVpnService()
                notifyVpnRulesChanged()
            } else {
                _isUrlFilterEnabled.value = false
                withContext(Dispatchers.IO) {
                    sharedPrefs.edit().putBoolean("url_filter_enabled", false).commit()
                }

                if (!_isAppFilterEnabled.value) {
                    stopVpnService()
                } else {
                    notifyVpnRulesChanged()
                }
            }
        }
    }

    /**
     * Toggles the Application-based filter.
     * Mutually exclusive with VPN-based URL filter.
     */
    fun toggleAppFilter(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                // 1. Handle mutual exclusivity with URL Filter (VPN Based) (NEW <not> condition)
                if (_isUrlFilterEnabled.value) {
                    _isUrlFilterEnabled.value = false
                    withContext(Dispatchers.IO) {
                        sharedPrefs.edit().putBoolean("url_filter_enabled", false).commit()
                    }
                }
            }

            _isAppFilterEnabled.value = enabled
            withContext(Dispatchers.IO) {
                sharedPrefs.edit().putBoolean("app_filter_enabled", enabled).commit()
            }

            if (enabled) {
                startVpnService()
            } else {
                if (!_isUrlFilterEnabled.value) {
                    stopVpnService()
                } else {
                    notifyVpnRulesChanged()
                }
            }
        }
    }

    /**
     * Toggles the Hosts-based URL filter (Requires Root).
     */
    fun toggleRoot(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                // If VPN-based URL filter is active, turn it off
                if (_isUrlFilterEnabled.value) {
                    _isUrlFilterEnabled.value = false
                    withContext(Dispatchers.IO) {
                        sharedPrefs.edit().putBoolean("url_filter_enabled", false).commit()
                    }
                    
                    // Update VPN state (stop if no App filter, otherwise just reload)
                    if (!_isAppFilterEnabled.value) {
                        stopVpnService()
                    } else {
                        notifyVpnRulesChanged()
                    }
                }
                checkRoot(manualToggle = true)
            } else {
                _isRootEnabled.value = false
                withContext(Dispatchers.IO) {
                    sharedPrefs.edit().putBoolean("root_enabled", false).commit()
                    hostsGenerator.generateDefaultHostsFile()
                }
            }
        }
    }

    private fun checkRoot(manualToggle: Boolean) {
        viewModelScope.launch {
            val hasRoot = withContext(Dispatchers.IO) {
                RootUtils.isRootAvailable()
            }

            if (manualToggle) {
                _rootCheckResult.value = hasRoot
            }

            _isRootEnabled.value = hasRoot
            sharedPrefs.edit().putBoolean("root_enabled", hasRoot).apply()

            if (hasRoot) {
                withContext(Dispatchers.IO) {
                    hostsGenerator.generateHostsFile()
                }
            }
        }
    }


    /**
     * Toggles Start on Boot.
     * Restricted: Cannot be enabled if both URL Filter and App Filter are disabled.
     */
    fun toggleStartOnBoot(enabled: Boolean, context: Context) {
        viewModelScope.launch {
            if (enabled && !_isUrlFilterEnabled.value && !_isAppFilterEnabled.value) {
                // Règle de rigueur : Notification à l'utilisateur via ressource strings.xml
                _rootCheckResult.value =
                    false // On peut réutiliser un canal de feedback ou un Toast dédié
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.error_start_on_boot_disabled),
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@launch
            }

            _isStartOnBootEnabled.value = enabled
            withContext(Dispatchers.IO) {
                sharedPrefs.edit().putBoolean("start_on_boot", enabled).commit()
            }
        }
    }

    fun togglePureBlack(enabled: Boolean) {
        _isPureBlackEnabled.value = enabled
        sharedPrefs.edit().putBoolean("pure_black_enabled", enabled).apply()
    }

    fun toggleDynamicColor(enabled: Boolean) {
        _isDynamicColorEnabled.value = enabled
        sharedPrefs.edit().putBoolean("dynamic_color_enabled", enabled).apply()
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        sharedPrefs.edit().putString("theme_mode", mode).apply()
    }

    fun setLanguage(code: String) {
        _languageCode.value = code
        sharedPrefs.edit().putString("language_code", code).apply()
    }

    fun resetRootCheckResult() {
        _rootCheckResult.value = null
    }

    fun setDnsServer(primary: String, secondary: String) {
        sharedPrefs.edit()
            .putString("dns_primary", primary)
            .putString("dns_secondary", secondary)
            .apply()
    }

    fun getDnsPrimary(): String {
        return sharedPrefs.getString("dns_primary", "8.8.8.8") ?: "8.8.8.8"
    }

    fun getDnsSecondary(): String {
        return sharedPrefs.getString("dns_secondary", "8.8.4.4") ?: "8.8.4.4"
    }

    fun refreshRootStatus() {
        _isRootPossible.value = RootUtils.isDeviceRooted()
    }

    override fun onCleared() {
        super.onCleared()
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }
}
