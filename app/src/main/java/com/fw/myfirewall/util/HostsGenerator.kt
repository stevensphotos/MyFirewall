package com.fw.myfirewall.util

import android.content.Context
import android.util.Log
import com.fw.myfirewall.data.BlockRuleCategory
import com.fw.myfirewall.data.BlockRuleDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

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
