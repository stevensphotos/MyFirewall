package com.fw.myfirewall.util

import android.content.Context
import android.util.Log
import com.fw.myfirewall.data.BlockRule
import com.fw.myfirewall.data.BlockRuleCategory
import com.fw.myfirewall.data.BlockRuleDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import android.content.Intent
import com.fw.myfirewall.service.MyVpnService

class HostsGenerator(
    private val blockRuleDao: BlockRuleDao,
    private val context: Context
) {

    companion object {
        private const val HOSTS_FILE_PATH = "/data/adb/modules/hosts/system/etc/hosts"
        private const val TAG = "HostsGenerator"
    }

    /**
     * Génère un fichier hosts contenant uniquement le localhost (purge).
     * Cette fonction est utile pour désactiver proprement le filtrage Root.
     */
    suspend fun generateDefaultHostsFile() {
        // On vérifie quand même le root avant d'agir
        if (!RootUtils.isDeviceRooted() || !RootUtils.isRootAvailable()) return

        withContext(Dispatchers.IO) {
            try {
                val hostsContent = StringBuilder()
                hostsContent.append("127.0.0.1 localhost\n")
                hostsContent.append("::1 localhost\n\n")

                // On écrit directement le contenu par défaut
                writeToRootFile(hostsContent.toString())
                Log.d("HostsGenerator", "Default hosts file generated (purged)")
            } catch (e: Exception) {
                Log.e("HostsGenerator", "Error purging hosts file", e)
            }
        }
    }

    /**
     * Cette routine permet de s'assurer que les domaines sont bien présents
     * dans la base de données, ce qui est indispensable pour le filtrage VPN.
     * Elle est appelée même si le Root n'est pas disponible.
     */
    /**
     * Cette routine permet de s'assurer que les domaines sont bien présents
     * dans la base de données, ce qui est indispensable pour le filtrage VPN.
     * Elle est appelée même si le Root n'est pas disponible.
     */
    suspend fun syncRulesToDatabase(category: BlockRuleCategory, domains: List<String>) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Préparation des objets BlockRule
                val blockRules = domains.map { domain ->
                    BlockRule(
                        domain = domain,
                        category = category,
                        isEnabled = true
                    )
                }

                // 2. Insertion en base de données
                blockRuleDao.insertBlockRules(blockRules)

                // 3. Notification du service VPN pour recharger les règles
                val intent =
                    Intent(context, MyVpnService::class.java).apply {
                        action = "com.fw.myfirewall.ACTION_RELOAD_RULES"
                    }
                context.startService(intent)

                Log.d(
                    TAG,
                    "Successfully synced ${blockRules.size} domains to DB for category ${category.name}"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing rules to database", e)
            }
        }
    }


    /**
     * Point d'entrée recommandé après un téléchargement de liste.
     * 1. Synchronise la DB (indispensable pour le VPN)
     * 2. Tente de générer le fichier Hosts (si Root dispo et activé)
     */
    suspend fun updateAllFilters(category: BlockRuleCategory, domains: List<String>) {
        // Étape 1 : Toujours synchroniser la base de données
        syncRulesToDatabase(category, domains)

        // Étape 2 : Mettre à jour le fichier système si le mode Root est utilisé
        if (RootUtils.isRootAvailable()) {
            generateHostsFile()
        }

        // Note : Le MyVpnService recharge ses règles via ACTION_RELOAD_RULES
        // déclenché par le ViewModel ou le Worker après cet appel.
    }


    suspend fun generateHostsFile() {
        if (!RootUtils.isDeviceRooted() || !RootUtils.isRootAvailable()) {
            Log.d(TAG, "Root not available, skipping hosts generation")
            return
        }
        
        // We need to check if the root service is enabled in preferences too
        val sharedPrefs = context.getSharedPreferences("MyFirewallPrefs", Context.MODE_PRIVATE)
        val isRootEnabled = sharedPrefs.getBoolean("root_enabled", false)
        
        if (!isRootEnabled) {
             Log.d(TAG, "Root service disabled in settings, skipping hosts generation")
             return
        }

        withContext(Dispatchers.IO) {
            try {
                // Prepare content builder
                val hostsContent = StringBuilder()
                hostsContent.append("127.0.0.1 localhost\n")
                hostsContent.append("::1 localhost\n\n")

                // Check toggle states from SharedPreferences
                val isAdsEnabled = sharedPrefs.getBoolean("advertising_enabled", false)
                val isAnnoyanceEnabled = sharedPrefs.getBoolean("annoyances_enabled", false)
                val isSocialEnabled = sharedPrefs.getBoolean("social_enabled", false)
                val isCustomEnabled = sharedPrefs.getBoolean("custom_enabled", false)

                // Load rules from DB based on toggles
                if (isAdsEnabled) {
                   appendRulesForCategory(BlockRuleCategory.ADVERTISING, hostsContent)
                }
                if (isAnnoyanceEnabled) {
                    appendRulesForCategory(BlockRuleCategory.ANNOYANCES, hostsContent)
                }
                if (isSocialEnabled) {
                    appendRulesForCategory(BlockRuleCategory.SOCIAL_MEDIA, hostsContent)
                }
                
                // Note: Custom rules logic wasn't explicitly requested to be different, 
                // but usually they are always included or have their own toggle. 
                // Based on RulesScreen logic I added, there is a 'custom_enabled' preference.
                // Assuming we should include them if enabled.
                if (isCustomEnabled) {
                    appendRulesForCategory(BlockRuleCategory.CUSTOM, hostsContent)
                }
                
                // Always write content, even if empty (clearing file effectively if all toggles off)
                writeToRootFile(hostsContent.toString())

            } catch (e: Exception) {
                Log.e(TAG, "Error generating hosts file", e)
            }
        }
    }

    private suspend fun appendRulesForCategory(category: BlockRuleCategory, builder: StringBuilder) {
        val rules = blockRuleDao.getBlockRulesByCategory(category).first()
        builder.append("# Category: ${category.name}\n")
        for (rule in rules) {
             if (rule.isEnabled) {
                 builder.append("0.0.0.0 ${rule.domain}\n")
             }
        }
        builder.append("\n")
    }

    private fun writeToRootFile(content: String) {
         // Create a temporary file in app cache
        val tempFile = File(context.cacheDir, "hosts_temp")
        tempFile.writeText(content)

        // Move it to the destination using su
        val command = "cp ${tempFile.absolutePath} $HOSTS_FILE_PATH && chmod 644 $HOSTS_FILE_PATH"
        val success = RootUtils.executeRootCommand(command)
        
        if (success) {
            Log.d(TAG, "Hosts file updated successfully at $HOSTS_FILE_PATH")
        } else {
             Log.e(TAG, "Failed to update hosts file at $HOSTS_FILE_PATH")
        }
        
        tempFile.delete()
    }
}

