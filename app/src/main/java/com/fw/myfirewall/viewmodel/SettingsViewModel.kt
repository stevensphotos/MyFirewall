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
    private val sharedPrefs = application.getSharedPreferences("MyFirewallPrefs", Context.MODE_PRIVATE)
    private val hostsGenerator: HostsGenerator

    private val _isRootEnabled = MutableStateFlow(false)
    val isRootEnabled: StateFlow<Boolean> = _isRootEnabled.asStateFlow()

    private val _isStartOnBootEnabled = MutableStateFlow(false)
    val isStartOnBootEnabled: StateFlow<Boolean> = _isStartOnBootEnabled.asStateFlow()

    private val _isPureBlackEnabled = MutableStateFlow(false)
    val isPureBlackEnabled: StateFlow<Boolean> = _isPureBlackEnabled.asStateFlow()
    
    private val _themeMode = MutableStateFlow("system") // system, light, dark
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _rootCheckResult = MutableStateFlow<Boolean?>(null)
    val rootCheckResult: StateFlow<Boolean?> = _rootCheckResult.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        hostsGenerator = HostsGenerator(db.blockRuleDao(), application)

        _isStartOnBootEnabled.value = sharedPrefs.getBoolean("start_on_boot", false)
        _isRootEnabled.value = sharedPrefs.getBoolean("root_enabled", false)
        _isPureBlackEnabled.value = sharedPrefs.getBoolean("pure_black_enabled", false)
        _themeMode.value = sharedPrefs.getString("theme_mode", "system") ?: "system"
    }

    fun toggleStartOnBoot(enabled: Boolean) {
        _isStartOnBootEnabled.value = enabled
        sharedPrefs.edit().putBoolean("start_on_boot", enabled).apply()
    }

    fun togglePureBlack(enabled: Boolean) {
        // Just toggle the preference. The UI logic (enable/disable) is handled in the UI if needed,
        // but here we just store the intent.
        // Wait, if we toggle it ON, we should check if we are in dark mode?
        // The prompt says: "if theme is darkcolorscheme and pure black toggle is ON then apply... else keep active theme"
        // This logic is mostly view/theme application logic, but maybe the prompt implies we shouldn't even allow enabling it if not dark?
        // Or that enabling it should only take effect if dark.
        // MyFirewallTheme already handles "pureBlack" parameter.
        // If pureBlack is true, MyFirewallTheme applies pure black scheme.
        // But MyFirewallTheme logic I wrote earlier:
        // when { pureBlack ... -> ... }
        // This applies pure black regardless of darkTheme flag if pureBlack is true!
        // The user wants: "if theme is darkcolorscheme and pure black toggle is ON then apply pure black... else keep active theme"
        // So pure black should ONLY apply if dark mode is active.
        
        // This logic belongs in MyFirewallTheme or MainActivity where we decide what to pass to MyFirewallTheme.
        // But here I'm updating the ViewModel state.
        
        _isPureBlackEnabled.value = enabled
        sharedPrefs.edit().putBoolean("pure_black_enabled", enabled).apply()
    }
    
    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        sharedPrefs.edit().putString("theme_mode", mode).apply()
    }

    fun toggleRoot(enabled: Boolean) {
        if (enabled) {
            checkRoot()
        } else {
            _isRootEnabled.value = false
            sharedPrefs.edit().putBoolean("root_enabled", false).apply()
        }
    }

    private fun checkRoot() {
        viewModelScope.launch {
            val hasRoot = withContext(Dispatchers.IO) {
                RootUtils.isDeviceRooted() && RootUtils.isRootAvailable()
            }
            _rootCheckResult.value = hasRoot
            if (hasRoot) {
                _isRootEnabled.value = true
                sharedPrefs.edit().putBoolean("root_enabled", true).apply()
                // Regenerate hosts file when root is enabled
                hostsGenerator.generateHostsFile()
            } else {
                _isRootEnabled.value = false
                sharedPrefs.edit().putBoolean("root_enabled", false).apply()
            }
        }
    }
    
    fun resetRootCheckResult() {
        _rootCheckResult.value = null
    }
}
