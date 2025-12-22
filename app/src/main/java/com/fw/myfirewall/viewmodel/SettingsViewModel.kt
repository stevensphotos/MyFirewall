package com.fw.myfirewall.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val sharedPrefs =
        application.getSharedPreferences("MyFirewallPrefs", Context.MODE_PRIVATE)
    private val hostsGenerator: HostsGenerator

    // --- ÉTATS DES FILTRES ---
    private val _isUrlFilterEnabled = MutableStateFlow(false)
    val isUrlFilterEnabled: StateFlow<Boolean> = _isUrlFilterEnabled.asStateFlow()

    private val _isRootEnabled = MutableStateFlow(false)
    val isRootEnabled: StateFlow<Boolean> = _isRootEnabled.asStateFlow()

    // --- ÉTATS SYSTÈME ---
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

    // --- ÉTATS ROOT ---
    private val _rootCheckResult = MutableStateFlow<Boolean?>(null)
    val rootCheckResult: StateFlow<Boolean?> = _rootCheckResult.asStateFlow()

    private val _isRootPossible = MutableStateFlow(RootUtils.isDeviceRooted())
    val isRootPossible: StateFlow<Boolean> = _isRootPossible.asStateFlow()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        viewModelScope.launch {
            when (key) {
                "url_filter_enabled" -> _isUrlFilterEnabled.value = prefs.getBoolean(key, false)
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

        // Initialisation des valeurs depuis les SharedPreferences
        _isUrlFilterEnabled.value = sharedPrefs.getBoolean("url_filter_enabled", false)
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

    // ############################################################################
    // LOGIQUE DE COMMUTATION DES FILTRES (MUTUELLE EXCLUSIVE)
    // ############################################################################

    /**
     * Notifie le service VPN que les règles de filtrage ont changé.
     */
    private fun notifyVpnRulesChanged() {
        val intent = Intent(getApplication(), MyVpnService::class.java)
        intent.action = "com.fw.myfirewall.ACTION_RELOAD_RULES"
        getApplication<Application>().startService(intent)
    }


    /**
     * Cette fonction centrale décide si le VPN doit tourner ou s'arrêter
     */
    private fun syncVpnServiceState() {
        val isAppFilterActive =
            sharedPrefs.getBoolean("app_filter_enabled", false) // Votre toggle existant
        val isUrlFilterActive = _isUrlFilterEnabled.value

        val intent = Intent(getApplication(), MyVpnService::class.java)

        if (isAppFilterActive || isUrlFilterActive) {
            // Au moins un filtre est actif -> On lance/maintient le VPN
            intent.action = MyVpnService.ACTION_CONNECT
            getApplication<Application>().startForegroundService(intent)
        } else {
            // Les deux sont éteints -> On coupe tout
            intent.action = MyVpnService.ACTION_DISCONNECT
            getApplication<Application>().startService(intent)
        }
    }

    /**
     * Active le filtrage via VPN.
     * Si le mode ROOT était actif, il est purgé avant.
     */
    fun toggleUrlFilter(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                // 1. Gérer l'exclusion avec le ROOT (Logic from URL_Filter_change_logic.txt)
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
            }

            // 2. Mettre à jour la préférence URL Filter
            _isUrlFilterEnabled.value = enabled
            withContext(Dispatchers.IO) {
                sharedPrefs.edit().putBoolean("url_filter_enabled", enabled).commit()
            }

            // 3. Synchroniser le service (Démarrer ou Arrêter selon l'état global)
            syncVpnServiceState()
            
            // 4. Notifier le service si actif
            if (enabled) {
                notifyVpnRulesChanged()
            }
        }
    }

    /**
     * Active le filtrage via Root (Iptables/Hosts).
     * Si le mode VPN était actif, il est coupé.
     */
    fun toggleRoot(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                // New rule: when toggle {URL Filter (VPN Based)} is active.
                // If user switch to ON the Toggle {URL Filter (Hosts Based)}
                // Then set toggle {URL Filter (VPN Based)} to off
                if (_isUrlFilterEnabled.value) {
                    // a) Mise à jour des préférences (Synchrone)
                    withContext(Dispatchers.IO) {
                        sharedPrefs.edit().putBoolean("url_filter_enabled", false).commit()
                    }

                    // b) Mise à jour de l'état UI
                    _isUrlFilterEnabled.value = false

                    // c) Synchroniser le service (va couper le VPN si plus aucun filtre)
                    syncVpnServiceState()
                }

                // Étape 2 : Lancer la vérification de permissions et l'activation Root
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

    // ############################################################################
    // LOGIQUE SYSTÈME ET UI
    // ############################################################################

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

    fun toggleStartOnBoot(enabled: Boolean) {
        _isStartOnBootEnabled.value = enabled
        sharedPrefs.edit().putBoolean("start_on_boot", enabled).apply()
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

    fun refreshRootStatus() {
        _isRootPossible.value = RootUtils.isDeviceRooted()
    }

    override fun onCleared() {
        super.onCleared()
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }
}
