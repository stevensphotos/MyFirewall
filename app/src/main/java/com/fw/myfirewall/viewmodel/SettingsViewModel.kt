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
import kotlinx.coroutines.delay
import com.fw.myfirewall.service.HostsFilterService




// ############################################################################
// INFORMATION SUR LA LICENCE ET LE DÉPÔT
// ############################################################################
private const val APP_LICENSE = "GPLv3"
private const val REPO_URL = "https://github.com/votre_pseudo/MyFirewall"
fun getLicenseInfo(): String = APP_LICENSE
fun getRepoUrl(): String = REPO_URL

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs =
        application.getSharedPreferences("MyFirewallPrefs", Context.MODE_PRIVATE)
    private val hostsGenerator: HostsGenerator

    // ############################################################################
    // 1. [SORTIES STATEFLOW] : ÉTATS DES FILTRES (Observés par SettingsScreen)
    // ############################################################################
    private val _isUrlFilterEnabled = MutableStateFlow(false)
    val isUrlFilterEnabled: StateFlow<Boolean> = _isUrlFilterEnabled.asStateFlow()

    private val _isAppFilterEnabled = MutableStateFlow(false)
    val isAppFilterEnabled: StateFlow<Boolean> = _isAppFilterEnabled.asStateFlow()

    private val _isRootEnabled = MutableStateFlow(false)
    val isRootEnabled: StateFlow<Boolean> = _isRootEnabled.asStateFlow()

    // ############################################################################
    // 2. [SORTIES STATEFLOW] : ÉTATS SYSTÈME ET UI (Observés par SettingsScreen)
    // ############################################################################
    private val _isStartOnBootEnabled = MutableStateFlow(false)
    val isStartOnBootEnabled: StateFlow<Boolean> = _isStartOnBootEnabled.asStateFlow()

    // État du verrouillage de l'application
    private val _isAppLockEnabled = MutableStateFlow(false)
    val isAppLockEnabled: StateFlow<Boolean> = _isAppLockEnabled.asStateFlow()

    private val _isPureBlackEnabled = MutableStateFlow(false)
    val isPureBlackEnabled: StateFlow<Boolean> = _isPureBlackEnabled.asStateFlow()

    private val _isDynamicColorEnabled = MutableStateFlow(true)
    val isDynamicColorEnabled: StateFlow<Boolean> = _isDynamicColorEnabled.asStateFlow()

    private val _themeMode = MutableStateFlow("system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _languageCode = MutableStateFlow("system")
    val languageCode: StateFlow<String> = _languageCode.asStateFlow()

    // ############################################################################
    // 3. [SORTIES STATEFLOW] : ÉTATS DES PERMISSIONS
    // ############################################################################
    private val _isUsageStatsPermissionGranted = MutableStateFlow(false)
    val isUsageStatsPermissionGranted: StateFlow<Boolean> =
        _isUsageStatsPermissionGranted.asStateFlow()
    private val _hasNotificationPermission = MutableStateFlow(false)
    val hasNotificationPermission: StateFlow<Boolean> = _hasNotificationPermission.asStateFlow()

    // ############################################################################
    // 4. [ENTRÉES/SORTIES] : GESTION DES PERMISSIONS
    // ############################################################################
    fun updateUsageStatsStatus(context: Context) {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val status = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            _hasNotificationPermission.value = (status == PackageManager.PERMISSION_GRANTED)
        } else {
            _hasNotificationPermission.value = true
        }
    }

    // ############################################################################
    // 4.1. [ENTRÉES UI] : GESTION DE LA LANGUE ET DU THÈME
    // ############################################################################

    /**
     * Met à jour la langue de l'application.
     * [SORTIE] : Notifie tous les observateurs (Pager, Titres) via languageCode StateFlow.
     */
    fun setLanguage(languageCode: String) {
        _languageCode.value = languageCode
        viewModelScope.launch(Dispatchers.IO) {
            sharedPrefs.edit().putString("language_code", languageCode).apply()
        }
    }

    /**
     * Met à jour le mode de thème.
     */
    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        viewModelScope.launch(Dispatchers.IO) {
            sharedPrefs.edit().putString("theme_mode", mode).apply()
        }
    }

    /**
     * Bascule l'état du mode Noir Pur (OLED).
     */
    fun togglePureBlack(enabled: Boolean) {
        _isPureBlackEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            sharedPrefs.edit().putBoolean("pure_black_enabled", enabled).apply()
        }
    }

    /**
     * Bascule l'état des couleurs dynamiques (Material You).
     */
    fun toggleDynamicColor(enabled: Boolean) {
        _isDynamicColorEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            sharedPrefs.edit().putBoolean("dynamic_color_enabled", enabled).apply()
        }
    }


    // ############################################################################
    // 5. [SORTIES STATEFLOW] : ÉTATS ROOT
    // ############################################################################
    private val _rootCheckResult = MutableStateFlow<Boolean?>(null)
    val rootCheckResult: StateFlow<Boolean?> = _rootCheckResult.asStateFlow()

    private val _isRootPossible = MutableStateFlow(RootUtils.isDeviceRooted())
    val isRootPossible: StateFlow<Boolean> = _isRootPossible.asStateFlow()

    // ############################################################################
    // 6. [ÉCOUTEUR DE PERSISTANCE] : Synchronisation SharedPreferences -> UI
    // ############################################################################
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        viewModelScope.launch {
            when (key) {
                "url_filter_enabled" -> _isUrlFilterEnabled.value = prefs.getBoolean(key, false)
                "app_filter_enabled" -> _isAppFilterEnabled.value = prefs.getBoolean(key, false)
                "root_enabled" -> _isRootEnabled.value = prefs.getBoolean(key, false)
                "start_on_boot" -> _isStartOnBootEnabled.value = prefs.getBoolean(key, false)
                "app_lock_enabled" -> _isAppLockEnabled.value = prefs.getBoolean(key, false)
                "pure_black_enabled" -> _isPureBlackEnabled.value = prefs.getBoolean(key, false)
                "dynamic_color_enabled" -> _isDynamicColorEnabled.value =
                    prefs.getBoolean(key, true)

                "theme_mode" -> _themeMode.value = prefs.getString(key, "system") ?: "system"
                "language_code" -> _languageCode.value = prefs.getString(key, "system") ?: "system"
            }
        }
    }

    // ############################################################################
    // 7. INITIALISATION DU VIEWMODEL
    // ############################################################################
    init {
        val db = AppDatabase.getDatabase(application)
        hostsGenerator = HostsGenerator(db.blockRuleDao(), application)

        _isUrlFilterEnabled.value = sharedPrefs.getBoolean("url_filter_enabled", false)
        _isAppFilterEnabled.value = sharedPrefs.getBoolean("app_filter_enabled", false)
        _isRootEnabled.value = sharedPrefs.getBoolean("root_enabled", false)
        _isStartOnBootEnabled.value = sharedPrefs.getBoolean("start_on_boot", false)
        _isAppLockEnabled.value = sharedPrefs.getBoolean("app_lock_enabled", false)
        _isPureBlackEnabled.value = sharedPrefs.getBoolean("pure_black_enabled", false)
        _isDynamicColorEnabled.value = sharedPrefs.getBoolean("dynamic_color_enabled", true)
        _themeMode.value = sharedPrefs.getString("theme_mode", "system") ?: "system"
        _languageCode.value = sharedPrefs.getString("language_code", "system") ?: "system"

        sharedPrefs.registerOnSharedPreferenceChangeListener(prefListener)

        if (_isRootEnabled.value) {
            checkRoot(manualToggle = false)
        }
    }

    // ############################################################################
    // 8. [ENTRÉES UI] : COMMANDES DE MODIFICATION
    // ############################################################################

    /**
     * ENTRÉE UI : Bascule le verrouillage biométrique.
     * SORTIE PERSISTANCE : Met à jour sharedPrefs.
     */
    fun toggleAppLock(enabled: Boolean) {
        _isAppLockEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            sharedPrefs.edit().putBoolean("app_lock_enabled", enabled).apply()
        }
    }

    // ############################################################################
    // 9. [SORTIES SYSTÈME] : DÉCLENCHEURS DE SERVICE VPN
    // ############################################################################

    private fun notifyVpnRulesChanged() {
        val intent = Intent(getApplication(), MyVpnService::class.java)
        intent.action = "com.fw.myfirewall.ACTION_RELOAD_RULES"
        getApplication<Application>().startService(intent)
    }

    private fun startVpnService() {
        if (_isUrlFilterEnabled.value || _isAppFilterEnabled.value) {
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

    // ############################################################################
    // 10. [ENTRÉES UI / LOGIQUE] : GESTION DU FIREWALL
    // ############################################################################

    fun checkAndDisableStartOnBoot() {
        if (!_isAppFilterEnabled.value && !_isUrlFilterEnabled.value) {
            if (_isStartOnBootEnabled.value) {
                _isStartOnBootEnabled.value = false
                viewModelScope.launch(Dispatchers.IO) {
                    sharedPrefs.edit().putBoolean("start_on_boot", false).apply()
                }
            }
        }
    }

    /**
     * ENTRÉE UI : Bascule le filtrage d'URL (DNS).
     * SORTIE : Gère l'exclusivité, met à jour SharedPreferences et redémarre les services.
     */
    fun toggleUrlFilter(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                // 1. Gérer l'exclusivité avec le filtrage Root (Hosts)
                if (_isRootEnabled.value) {
                    _isRootEnabled.value = false
                    viewModelScope.launch(Dispatchers.IO) {
                        sharedPrefs.edit().putBoolean("root_enabled", false).apply()
                        hostsGenerator.generateDefaultHostsFile()

                        // [SORTIE SYSTÈME] : Arrêt du service Hosts pour supprimer sa notification
                        val stopHostsIntent = Intent(getApplication(), HostsFilterService::class.java)
                        getApplication<Application>().stopService(stopHostsIntent)
                    }
                }

                // 2. Gérer l'exclusivité avec le filtrage d'Applications (UID)
                val wasAppEnabled = _isAppFilterEnabled.value
                if (wasAppEnabled) {
                    _isAppFilterEnabled.value = false
                    viewModelScope.launch(Dispatchers.IO) {
                        sharedPrefs.edit().putBoolean("app_filter_enabled", false).apply()
                    }
                    // Arrêt pour nettoyer le routage par UID avant le mode DNS
                    stopVpnService()
                    delay(400)
                }

                _isUrlFilterEnabled.value = true
                viewModelScope.launch(Dispatchers.IO) {
                    sharedPrefs.edit().putBoolean("url_filter_enabled", true).apply()
                }

                // Safety delay for system settings propagation
                delay(300)
                startVpnService()
            } else {
                _isUrlFilterEnabled.value = false
                viewModelScope.launch(Dispatchers.IO) {
                    sharedPrefs.edit().putBoolean("url_filter_enabled", false).apply()
                }
                checkAndDisableStartOnBoot()
                // Stop service if no other VPN filter is active
                if (!_isAppFilterEnabled.value) {
                    stopVpnService()
                } else {
                    notifyVpnRulesChanged()
                }
            }
        }
    }

    /**
     * ENTRÉE UI : Bascule le filtrage par Application.
     * SORTIE : Gère l'exclusivité DNS, met à jour SharedPreferences et redémarre les services.
     */
    fun toggleAppFilter(enabled: Boolean) {
        viewModelScope.launch {
            val wasUrlEnabled = _isUrlFilterEnabled.value

            if (enabled && wasUrlEnabled) {
                // Handle exclusivity: disable URL filter state
                _isUrlFilterEnabled.value = false
                viewModelScope.launch(Dispatchers.IO) {
                    sharedPrefs.edit().putBoolean("url_filter_enabled", false).apply()
                }
                // Arrêt pour nettoyer le routage DNS avant le mode UID
                stopVpnService()
                delay(400)
            }

            // 2. MISE À JOUR DE L'ÉTAT UI IMMÉDIATE
            _isAppFilterEnabled.value = enabled

            // 3. SAUVEGARDE ASYNCHRONE (apply au lieu de commit)
            viewModelScope.launch(Dispatchers.IO) {
                sharedPrefs.edit().putBoolean("app_filter_enabled", enabled).apply()
            }

            // 4. DÉCLENCHEMENT DU SERVICE AVEC UN LÉGER DÉLAI DE SÉCURITÉ
            if (enabled) {
                // Safety delay for system settings propagation
                delay(300)
                startVpnService()
            } else {
                checkAndDisableStartOnBoot()
                // Stop service if no other VPN filter is active
                if (!_isUrlFilterEnabled.value) {
                    stopVpnService()
                } else {
                    notifyVpnRulesChanged()
                }
            }
        }
    }


    /**
     * Toggles the Root-based hosts filter.
     * Mutually exclusive with VPN filters to prevent routing conflicts.
     * Automatically disables "Start on Boot" if switching to Root mode.
     */
    fun toggleRoot(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                // 1. Handle exclusivity: If any VPN filter is active, shut it down
                if (_isUrlFilterEnabled.value || _isAppFilterEnabled.value) {
                    _isUrlFilterEnabled.value = false
                    _isAppFilterEnabled.value = false

                    viewModelScope.launch(Dispatchers.IO) {
                        sharedPrefs.edit()
                            .putBoolean("url_filter_enabled", false)
                            .putBoolean("app_filter_enabled", false)
                            .apply()
                    }
                    stopVpnService()
                    delay(400)
                }

                // 2. Logic condition: Disable "Start on Boot" when switching to Root mode
                // as the VPN service is no longer the primary engine.
                if (_isStartOnBootEnabled.value) {
                    _isStartOnBootEnabled.value = false
                    viewModelScope.launch(Dispatchers.IO) {
                        sharedPrefs.edit().putBoolean("start_on_boot", false).apply()
                    }
                }

                // 3. checkRoot will call hostsGenerator.generateDefaultHostsFile() internally
                checkRoot(manualToggle = true)
            } else {
                _isRootEnabled.value = false
                viewModelScope.launch(Dispatchers.IO) {
                    sharedPrefs.edit().putBoolean("root_enabled", false).apply()
                    hostsGenerator.generateDefaultHostsFile()
                    // ARRÊT DE LA NOTIFICATION
                    HostsFilterService.stop(getApplication())
                }
            }
        }
    }


    private fun checkRoot(manualToggle: Boolean) {
        viewModelScope.launch {
            val hasRoot = withContext(Dispatchers.IO) { RootUtils.isRootAvailable() }

            // 1. Update the UI feedback flow
            if (manualToggle) {
                _rootCheckResult.value = hasRoot
            }

            // 2. Only enable logical state if physical root was actually obtained
            _isRootEnabled.value = hasRoot

            // 3. Persist the state and apply the physical file change
            withContext(Dispatchers.IO) {
                sharedPrefs.edit().putBoolean("root_enabled", hasRoot).apply()

                if (hasRoot) {
                    hostsGenerator.generateHostsFile()
                    // DÉMARRAGE DE LA NOTIFICATION
                    HostsFilterService.start(getApplication())
                } else {
                    hostsGenerator.generateDefaultHostsFile()
                    // ARRÊT DE LA NOTIFICATION
                    HostsFilterService.stop(getApplication())
                }
            }
        }
    }


    fun toggleStartOnBoot(enabled: Boolean, context: Context) {
        viewModelScope.launch {
            if (enabled && !_isUrlFilterEnabled.value && !_isAppFilterEnabled.value) {
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
                sharedPrefs.edit().putBoolean("start_on_boot", enabled).apply()
            }
        }
    }

    // --- OTHER UI SETTINGS ---
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
        sharedPrefs.edit().putString("dns_primary", primary).putString("dns_secondary", secondary).apply()
    }

    fun getDnsPrimary(): String = sharedPrefs.getString("dns_primary", "8.8.8.8") ?: "8.8.8.8"
    fun getDnsSecondary(): String = sharedPrefs.getString("dns_secondary", "8.8.4.4") ?: "8.8.4.4"
    fun refreshRootStatus() {
        _isRootPossible.value = RootUtils.isDeviceRooted()
    }

    override fun onCleared() {
        super.onCleared()
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }
}
