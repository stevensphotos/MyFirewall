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
     * Active le filtrage via VPN.
     * Si le mode ROOT était actif, il est purgé avant.
     */
    fun toggleUrlFilter(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                // Étape 1 : Si le mode Root est actif, on doit le purger d'abord
                if (_isRootEnabled.value) {
                    withContext(Dispatchers.IO) {
                        // Désactivation synchrone de toutes les règles liées au Root
                        sharedPrefs.edit().apply {
                            putBoolean("advertising_enabled", false)
                            putBoolean("annoyances_enabled", false)
                            putBoolean("social_enabled", false)
                            putBoolean("custom_enabled", false)
                            putBoolean("root_enabled", false)
                        }.commit()

                        // Étape 2 : Purge physique du fichier Hosts (Reset vers localhost)
                        hostsGenerator.generateDefaultHostsFile()
                    }
                    _isRootEnabled.value = false
                }

                // Étape 3 : Activation du moteur VPN
                withContext(Dispatchers.IO) {
                    sharedPrefs.edit().putBoolean("url_filter_enabled", true).commit()
                }
                _isUrlFilterEnabled.value = true

            } else {
                // Désactivation simple du VPN
                withContext(Dispatchers.IO) {
                    sharedPrefs.edit().putBoolean("url_filter_enabled", false).commit()
                }
                _isUrlFilterEnabled.value = false
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
                // Étape 1 : Si le VPN est actif, on le coupe physiquement
                if (_isUrlFilterEnabled.value) {
                    // a) Mise à jour des préférences (Synchrone)
                    withContext(Dispatchers.IO) {
                        sharedPrefs.edit().putBoolean("url_filter_enabled", false).commit()
                    }

                    // b) Mise à jour de l'état UI
                    _isUrlFilterEnabled.value = false

                    // c) ARRÊT PHYSIQUE DU SERVICE VPN
                    val intent =
                        Intent(getApplication(), MyVpnService::class.java)
                    intent.action = MyVpnService.ACTION_DISCONNECT
                    getApplication<Application>().startService(intent)
                }

                // Étape 2 : Lancer la vérification de permissions et l'activation Root
                checkRoot(manualToggle = true)
            } else {
                // ... reste de votre code de désactivation Root
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