package com.fw.myfirewall.viewmodel

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fw.myfirewall.data.AppDatabase
import com.fw.myfirewall.data.BlockListConfig
import com.fw.myfirewall.data.BlockListImporter
import com.fw.myfirewall.data.BlockRuleCategory
import com.fw.myfirewall.util.HostsGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RulesViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val sharedPrefs = context.getSharedPreferences("MyFirewallPrefs", Context.MODE_PRIVATE)
    private val database = AppDatabase.getDatabase(context)
    private val hostsGenerator = HostsGenerator(database.blockRuleDao(), context)

    private val _isAdvertisingEnabled = MutableStateFlow(false)
    val isAdvertisingEnabled: StateFlow<Boolean> = _isAdvertisingEnabled.asStateFlow()

    private val _isAnnoyancesEnabled = MutableStateFlow(false)
    val isAnnoyancesEnabled: StateFlow<Boolean> = _isAnnoyancesEnabled.asStateFlow()

    private val _isSocialEnabled = MutableStateFlow(false)
    val isSocialEnabled: StateFlow<Boolean> = _isSocialEnabled.asStateFlow()

    private val _isCustomEnabled = MutableStateFlow(false)
    val isCustomEnabled: StateFlow<Boolean> = _isCustomEnabled.asStateFlow()

    private val _selectedSchedule = MutableStateFlow("schedule_24h")
    val selectedSchedule: StateFlow<String> = _selectedSchedule.asStateFlow()

    init {
        _isAdvertisingEnabled.value = sharedPrefs.getBoolean("advertising_enabled", false)
        _isAnnoyancesEnabled.value = sharedPrefs.getBoolean("annoyances_enabled", false)
        _isSocialEnabled.value = sharedPrefs.getBoolean("social_enabled", false)
        _isCustomEnabled.value = sharedPrefs.getBoolean("custom_enabled", false)
        _selectedSchedule.value =
            sharedPrefs.getString("ad_blocker_schedule", "schedule_24h") ?: "schedule_24h"
    }

    fun clearAllCustomRules() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Supprime la catégorie CUSTOM de la base de données
                database.blockRuleDao().deleteByCategory(BlockRuleCategory.CUSTOM)
                // Régénère le fichier hosts (vide ou sans ces règles)
                hostsGenerator.generateHostsFile()
            }
        }
    }


    fun toggleAdvertising(enabled: Boolean) {
        _isAdvertisingEnabled.value = enabled
        sharedPrefs.edit().putBoolean("advertising_enabled", enabled).apply()
        handleRuleToggle(enabled, BlockRuleCategory.ADVERTISING, BlockListConfig.URL_ADVERTISING)
    }

    fun toggleAnnoyances(enabled: Boolean) {
        _isAnnoyancesEnabled.value = enabled
        sharedPrefs.edit().putBoolean("annoyances_enabled", enabled).apply()
        handleRuleToggle(enabled, BlockRuleCategory.ANNOYANCES, BlockListConfig.URL_ANNOYANCES)
    }

    fun toggleSocial(enabled: Boolean) {
        _isSocialEnabled.value = enabled
        sharedPrefs.edit().putBoolean("social_enabled", enabled).apply()
        handleRuleToggle(enabled, BlockRuleCategory.SOCIAL_MEDIA, BlockListConfig.URL_SOCIAL)
    }
    
    fun toggleCustom(enabled: Boolean) {
        _isCustomEnabled.value = enabled
        sharedPrefs.edit().putBoolean("custom_enabled", enabled).apply()
        viewModelScope.launch {
            hostsGenerator.generateHostsFile()
        }
    }

    fun applyCustomRules(rulesText: String) {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                BlockListImporter.importFromText(context, rulesText, BlockRuleCategory.CUSTOM)
            }
            if (count > 0) {
                Toast.makeText(context, "Applied $count custom rules", Toast.LENGTH_SHORT).show()
                // Auto-enable custom rules if rules were added
                if (!_isCustomEnabled.value) {
                    toggleCustom(true)
                } else {
                    hostsGenerator.generateHostsFile()
                }
            } else {
                Toast.makeText(context, "No valid rules found to apply", Toast.LENGTH_SHORT).show()
            }
        }
    }

    suspend fun getCurrentCustomRules(): String {
        return withContext(Dispatchers.IO) {
            val rules = database.blockRuleDao().getBlockRulesByCategory(BlockRuleCategory.CUSTOM).first()
            rules.joinToString("\n") { it.domain }
        }
    }
    
    fun updateSchedule(schedule: String) {
        _selectedSchedule.value = schedule
        sharedPrefs.edit().putString("ad_blocker_schedule", schedule).apply()
    }

    private fun handleRuleToggle(enabled: Boolean, category: BlockRuleCategory, url: String) {
        viewModelScope.launch {
            if (enabled) {
                val count = withContext(Dispatchers.IO) {
                    database.blockRuleDao().countByCategory(category).first()
                }

                if (count == 0) {
                    Toast.makeText(context, "Downloading block list...", Toast.LENGTH_SHORT).show()
                    val imported = withContext(Dispatchers.IO) {
                         BlockListImporter.importFromUrl(context, url, category)
                    }
                    if (imported > 0) {
                        Toast.makeText(context, "Imported $imported rules", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to import rules", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            hostsGenerator.generateHostsFile()
        }
    }
}
