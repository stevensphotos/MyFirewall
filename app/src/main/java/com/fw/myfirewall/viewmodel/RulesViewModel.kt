package com.fw.myfirewall.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
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
import com.fw.myfirewall.R
import androidx.core.content.edit


/**
 * ############################################################################
 * VIEWMODEL: RulesViewModel
 * Rôle : Orchestre la gestion des listes de blocage DNS/Hosts.
 * Fichiers connectés :
 * - BlockListImporter.kt (Téléchargement et Parsing)
 * - HostsGenerator.kt (Génération du fichier Root / Mise à jour VPN)
 * - AppDatabase.kt (Stockage persistant des domaines)
 * ############################################################################
 */
class RulesViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val sharedPrefs = context.getSharedPreferences("MyFirewallPrefs", Context.MODE_PRIVATE)
    private val database = AppDatabase.getDatabase(context)
    private val hostsGenerator = HostsGenerator(database.blockRuleDao(), context)

    // ----------------------------------------------------------------------------
    // 1. [SORTIES STATEFLOW] : États observés par l'UI (SettingsScreen / StatusScreen)
    // ----------------------------------------------------------------------------
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

    /**
     * ROUTINE : prefListener
     * [ENTRÉE SYSTÈME] : Écoute les changements dans SharedPreferences.
     * [SORTIE INTERNE] : Met à jour les StateFlows pour rafraîchir l'UI en temps réel.
     */
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            "advertising_enabled" -> _isAdvertisingEnabled.value = prefs.getBoolean(key, false)
            "annoyances_enabled" -> _isAnnoyancesEnabled.value = prefs.getBoolean(key, false)
            "social_enabled" -> _isSocialEnabled.value = prefs.getBoolean(key, false)
            "custom_enabled" -> _isCustomEnabled.value = prefs.getBoolean(key, false)
            "ad_blocker_schedule" -> _selectedSchedule.value = prefs.getString(key, "schedule_24h") ?: "schedule_24h"
        }
    }

    init {
        // [ENTRÉE PERSISTANCE] : Chargement initial des préférences utilisateur
        _isAdvertisingEnabled.value = sharedPrefs.getBoolean("advertising_enabled", false)
        _isAnnoyancesEnabled.value = sharedPrefs.getBoolean("annoyances_enabled", false)
        _isSocialEnabled.value = sharedPrefs.getBoolean("social_enabled", false)
        _isCustomEnabled.value = sharedPrefs.getBoolean("custom_enabled", false)
        _selectedSchedule.value =
            sharedPrefs.getString("ad_blocker_schedule", "schedule_24h") ?: "schedule_24h"
            
        sharedPrefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    /**
     * ROUTINE : clearAllCustomRules
     * [SORTIE ROOM] : Supprime définitivement la catégorie CUSTOM de la DB.
     * [SORTIE FILTRE] : Purge les fichiers hosts via HostsGenerator.
     */
    fun clearAllCustomRules() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // 1. Supprime les règles de la base de données
                database.blockRuleDao().deleteByCategory(BlockRuleCategory.CUSTOM)

                // 2. Met à jour les filtres avec une liste vide pour purger
                // Cela va vider le fichier hosts (Root) et notifier le VPN
                hostsGenerator.updateAllFilters(
                    category = BlockRuleCategory.CUSTOM,
                    domains = emptyList()
                )
            }
            // Optionnel : On peut notifier l'UI si nécessaire
            _isCustomEnabled.value = false
            sharedPrefs.edit { putBoolean("custom_enabled", false) }
        }
    }

    private fun HostsGenerator.updateAllFilters(
        category: BlockRuleCategory,
        domains: Any
    ) {
    }

    // ----------------------------------------------------------------------------
    // 2. [ENTRÉES UI] : Commandes de basculement des filtres DNS
    // ----------------------------------------------------------------------------

    fun toggleAdvertising(enabled: Boolean) {
        _isAdvertisingEnabled.value = enabled
        sharedPrefs.edit { putBoolean("advertising_enabled", enabled) }
        handleRuleToggle(enabled, BlockRuleCategory.ADVERTISING, BlockListConfig.URL_ADVERTISING)
    }

    fun toggleAnnoyances(enabled: Boolean) {
        _isAnnoyancesEnabled.value = enabled
        sharedPrefs.edit { putBoolean("annoyances_enabled", enabled) }
        handleRuleToggle(enabled, BlockRuleCategory.ANNOYANCES, BlockListConfig.URL_ANNOYANCES)
    }

    fun toggleSocial(enabled: Boolean) {
        _isSocialEnabled.value = enabled
        sharedPrefs.edit { putBoolean("social_enabled", enabled) }
        handleRuleToggle(enabled, BlockRuleCategory.SOCIAL_MEDIA, BlockListConfig.URL_SOCIAL)
    }
    
    fun toggleCustom(enabled: Boolean) {
        _isCustomEnabled.value = enabled
        sharedPrefs.edit { putBoolean("custom_enabled", enabled) }
        viewModelScope.launch {
            // [SORTIE SYSTÈME] : Déclenche la régénération du fichier hosts
            hostsGenerator.generateHostsFile()
        }
    }

    /**
     * ROUTINE : applyCustomRules
     * [ENTRÉE UI] : Texte brut saisi par l'utilisateur.
     * [SORTIE DB] : Importation via BlockListImporter.
     */
    fun applyCustomRules(rulesText: String) {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                BlockListImporter.importFromText(context, rulesText, BlockRuleCategory.CUSTOM)
            }

            // ############################################################################
            // FIX : Utilisation des ressources mises à jour pour la localisation
            // ############################################################################
            if (count > 0) {
                val msg = context.resources.getString(R.string.import_success, count)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                if (!_isCustomEnabled.value) {
                    toggleCustom(true)
                } else {
                    hostsGenerator.generateHostsFile()
                }
            } else {
                val errorMsg = context.resources.getString(R.string.custom_rules_error)
                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
            }
        }
    }


    /**
     * [SORTIE UI] : Récupère les règles actuelles pour affichage dans un champ d'édition.
     */
    suspend fun getCurrentCustomRules(): String {
        return withContext(Dispatchers.IO) {
            val rules = database.blockRuleDao().getBlockRulesByCategory(BlockRuleCategory.CUSTOM).first()
            rules.joinToString("\n") { it.domain }
        }
    }
    
    fun updateSchedule(schedule: String) {
        _selectedSchedule.value = schedule
        sharedPrefs.edit { putString("ad_blocker_schedule", schedule) }
    }

    /**
     * ROUTINE : handleRuleToggle
     * Logique de gestion de l'importation automatique.
     * [ENTRÉE CONFIG] : URL de la liste définie dans BlockListConfig.
     * [SORTIE RÉSEAU] : Téléchargement si la base de données est vide.
     */
    private fun handleRuleToggle(enabled: Boolean, category: BlockRuleCategory, url: String) {
        viewModelScope.launch {
            if (enabled) {
                val count = withContext(Dispatchers.IO) {
                    database.blockRuleDao().countByCategory(category).first()
                }

                if (count == 0) {
                    val loadingMsg = context.getString(R.string.advertising_title)
                    Toast.makeText(context, loadingMsg, Toast.LENGTH_SHORT).show()

                    val imported = withContext(Dispatchers.IO) {
                         BlockListImporter.importFromUrl(context, url, category)
                    }

                    if (imported > 0) {
                        val successMsg = context.getString(R.string.import_success, imported)
                        Toast.makeText(context, successMsg, Toast.LENGTH_SHORT).show()
                    } else {
                        val failMsg = context.getString(R.string.import_failed, "Network error")
                        Toast.makeText(context, failMsg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            // [SORTIE FINALE] : Rafraîchissement du moteur de blocage
            hostsGenerator.generateHostsFile()
        }
    }

    override fun onCleared() {
        super.onCleared()
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }
}
