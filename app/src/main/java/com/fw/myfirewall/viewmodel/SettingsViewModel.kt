package com.fw.myfirewall.viewmodel

import android.app.Application
import android.content.Context
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
    private val _isUrlFilterEnabled = MutableStateFlow(false)
    val isUrlFilterEnabled: StateFlow<Boolean> = _isUrlFilterEnabled.asStateFlow()
    fun toggleUrlFilter(enabled: Boolean) {
        viewModelScope.launch {
            // Save to DataStore/Prefs here
            _isUrlFilterEnabled.value = enabled

            // Trigger logic to start/stop the actual URL filtering engine
            if (enabled) {
                // startUrlFilter()
            } else {
                // stopUrlFilter()
            }
        }
    }


    private val sharedPrefs = application.getSharedPreferences("MyFirewallPrefs", Context.MODE_PRIVATE)
    private val hostsGenerator: HostsGenerator

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

    init {
        val db = AppDatabase.getDatabase(application)
        hostsGenerator = HostsGenerator(db.blockRuleDao(), application)

        _isStartOnBootEnabled.value = sharedPrefs.getBoolean("start_on_boot", false)
        _isRootEnabled.value = sharedPrefs.getBoolean("root_enabled", false)
        _isPureBlackEnabled.value = sharedPrefs.getBoolean("pure_black_enabled", false)
        _isDynamicColorEnabled.value = sharedPrefs.getBoolean("dynamic_color_enabled", true)
        _themeMode.value = sharedPrefs.getString("theme_mode", "system") ?: "system"
        _languageCode.value = sharedPrefs.getString("language_code", "system") ?: "system"
        
        // Verify root status on init if it was previously enabled
        val rootEnabled = sharedPrefs.getBoolean("root_enabled", false)
        if (rootEnabled) {
            checkRoot(manualToggle = false)
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
}
