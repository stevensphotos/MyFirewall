package com.fw.myfirewall.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fw.myfirewall.data.AppDatabase
import com.fw.myfirewall.util.HostsGenerator
import com.fw.myfirewall.util.RootUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val sharedPrefs = application.getSharedPreferences("MyFirewallPrefs", Context.MODE_PRIVATE)
    private val hostsGenerator: HostsGenerator

    private val _isUrlFilterEnabled = MutableStateFlow(false)
    val isUrlFilterEnabled: StateFlow<Boolean> = _isUrlFilterEnabled.asStateFlow()

    private val _isRootEnabled = MutableStateFlow(false)
    val isRootEnabled: StateFlow<Boolean> = _isRootEnabled.asStateFlow()

    private val _isStartOnBootEnabled = MutableStateFlow(false)
    val isStartOnBootEnabled: StateFlow<Boolean> = _isStartOnBootEnabled.asStateFlow()

    private val _isPureBlackEnabled = MutableStateFlow(false)
    val isPureBlackEnabled: StateFlow<Boolean> = _isPureBlackEnabled.asStateFlow()

    private val _isDynamicColorEnabled = MutableStateFlow(true)
    val isDynamicColorEnabled: StateFlow<Boolean> = _isDynamicColorEnabled.asStateFlow()

    private val _themeMode = MutableStateFlow("system") // system, light, dark
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _languageCode = MutableStateFlow("system") // system, en, fr
    val languageCode: StateFlow<String> = _languageCode.asStateFlow()

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
                "dynamic_color_enabled" -> _isDynamicColorEnabled.value = prefs.getBoolean(key, true)
                "theme_mode" -> _themeMode.value = prefs.getString(key, "system") ?: "system"
                "language_code" -> _languageCode.value = prefs.getString(key, "system") ?: "system"
            }
        }
    }

    init {
        val db = AppDatabase.getDatabase(application)
        hostsGenerator = HostsGenerator(db.blockRuleDao(), application)

        _isUrlFilterEnabled.value = sharedPrefs.getBoolean("url_filter_enabled", false)
        _isStartOnBootEnabled.value = sharedPrefs.getBoolean("start_on_boot", false)
        _isRootEnabled.value = sharedPrefs.getBoolean("root_enabled", false)
        _isPureBlackEnabled.value = sharedPrefs.getBoolean("pure_black_enabled", false)
        _isDynamicColorEnabled.value = sharedPrefs.getBoolean("dynamic_color_enabled", true)
        _themeMode.value = sharedPrefs.getString("theme_mode", "system") ?: "system"
        _languageCode.value = sharedPrefs.getString("language_code", "system") ?: "system"

        sharedPrefs.registerOnSharedPreferenceChangeListener(prefListener)

        // Verify root status on init if it was previously enabled
        val rootEnabled = sharedPrefs.getBoolean("root_enabled", false)
        if (rootEnabled) {
            checkRoot(manualToggle = false)
        }
    }

    fun toggleUrlFilter(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                // Étape 1 : Si le mode Root est actif, on doit le purger d'abord
                if (_isRootEnabled.value) {

                    // On utilise withContext(Dispatchers.IO) pour bloquer la coroutine
                    // jusqu'à ce que l'écriture disque ET la génération du fichier soient finies.
                    withContext(Dispatchers.IO) {
                        // a) On écrit les préférences de désactivation de façon synchrone (.commit())
                        val success = sharedPrefs.edit().apply {
                            putBoolean("advertising_enabled", false)
                            putBoolean("annoyances_enabled", false)
                            putBoolean("social_enabled", false)
                            putBoolean("custom_enabled", false)
                            putBoolean("root_enabled", false)
                        }.commit()

                        if (success) {
                            // b) On génère le fichier Hosts maintenant que les prefs sont à false
                            // Cette fonction doit être bloquante (suspendue)
                            hostsGenerator.generateDefaultHostsFile()
                        }
                    }

                    // Étape 2 : Mettre à jour l'état UI seulement après la purge physique
                    _isRootEnabled.value = false
                }

                // Étape 3 : Activer enfin le filtre VPN
                // On utilise commit() ici aussi pour garantir que le service VPN
                // lira la bonne valeur s'il démarre instantanément
                withContext(Dispatchers.IO) {
                    sharedPrefs.edit().putBoolean("url_filter_enabled", true).commit()
                }
                _isUrlFilterEnabled.value = true

            } else {
                // Désactivation simple
                withContext(Dispatchers.IO) {
                    sharedPrefs.edit().putBoolean("url_filter_enabled", false).commit()
                }
                _isUrlFilterEnabled.value = false
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

    fun toggleRoot(enabled: Boolean) {
        if (enabled) {
            checkRoot(manualToggle = true)
        } else {
            _isRootEnabled.value = false
            sharedPrefs.edit().putBoolean("root_enabled", false).apply()
            // Regenerate to clear hosts file when root filter is turned off
            viewModelScope.launch {
                hostsGenerator.generateHostsFile()
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
                hostsGenerator.generateHostsFile()
            }
        }
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
