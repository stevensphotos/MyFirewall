package com.fw.myfirewall.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object BlockListImporter {

    suspend fun importFromUrl(context: Context, urlString: String, category: BlockRuleCategory): Int {
        return withContext(Dispatchers.IO) {
            var count = 0
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val db = AppDatabase.getDatabase(context)
                    // Clear old rules ONLY if download is successful (started)
                    db.blockRuleDao().deleteByCategory(category)
                    
                    val inputStream = connection.inputStream
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val rules = mutableListOf<BlockRule>()

                    var line: String? = reader.readLine()
                    while (line != null) {
                        val domain = parseLine(line)
                        if (domain != null) {
                            rules.add(BlockRule(domain = domain, category = category))
                        }
                        
                        // Batch insert every 1000 rules to manage memory
                        if (rules.size >= 1000) {
                            db.blockRuleDao().insertBlockRules(rules)
                            count += rules.size
                            rules.clear()
                        }
                        
                        line = reader.readLine()
                    }

                    // Insert remaining
                    if (rules.isNotEmpty()) {
                        db.blockRuleDao().insertBlockRules(rules)
                        count += rules.size
                    }

                    reader.close()
                } else {
                    Log.e("BlockListImporter", "Failed to download file: ${connection.responseCode}")
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e("BlockListImporter", "Error importing blocklist", e)
            }
            count
        }
    }

    suspend fun importFromText(context: Context, text: String, category: BlockRuleCategory): Int {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            db.blockRuleDao().deleteByCategory(category)
            
            val lines = text.split("\n")
            val rules = mutableListOf<BlockRule>()
            var count = 0

            for (line in lines) {
                val domain = parseLine(line)
                if (domain != null) {
                    rules.add(BlockRule(domain = domain, category = category))
                }

                if (rules.size >= 500) {
                    db.blockRuleDao().insertBlockRules(rules)
                    count += rules.size
                    rules.clear()
                }
            }

            if (rules.isNotEmpty()) {
                db.blockRuleDao().insertBlockRules(rules)
                count += rules.size
            }
            count
        }
    }

    private fun parseLine(line: String): String? {
        val trimmedLine = line.trim()
        if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) return null

        val parts = trimmedLine.split("\\s+".toRegex())
        val domain = if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1")) {
            parts[1]
        } else {
            parts[0]
        }
        return if (domain.isNotEmpty()) domain else null
    }
}
