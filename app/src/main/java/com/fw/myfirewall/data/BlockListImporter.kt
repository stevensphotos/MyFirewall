package com.fw.myfirewall.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL

object BlockListImporter {

    // Pré-compilation de la Regex pour la performance
    private val spaceRegex = Regex("\\s+")

    suspend fun importFromUrl(context: Context, urlString: String, category: BlockRuleCategory): Int {
        return withContext(Dispatchers.IO) {
            var count = 0
            val db = AppDatabase.getDatabase(context)
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                // Ajout de Timeouts pour éviter les blocages indéfinis
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val rules = mutableListOf<BlockRule>()

                    // Utilisation d'une transaction pour garantir l'intégrité des données
                    db.withTransaction {
                        db.blockRuleDao().deleteByCategory(category)

                        var line: String? = reader.readLine()
                        while (line != null) {
                            val domain = parseLine(line)
                            if (domain != null) {
                                rules.add(BlockRule(domain = domain, category = category))
                            }

                            if (rules.size >= 1000) {
                                db.blockRuleDao().insertBlockRules(rules)
                                count += rules.size
                                rules.clear()
                            }
                            line = reader.readLine()
                        }

                        if (rules.isNotEmpty()) {
                            db.blockRuleDao().insertBlockRules(rules)
                            count += rules.size
                        }
                    }
                    reader.close()
                } else {
                    Log.e("BlockListImporter", "Failed to download file: ${connection.responseCode}")
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e("BlockListImporter", "Error importing blocklist from URL", e)
            }
            count
        }
    }

    suspend fun importFromText(context: Context, text: String, category: BlockRuleCategory): Int {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            var count = 0
            try {
                // Utilisation de StringReader pour éviter de splitter massivement la mémoire
                val reader = BufferedReader(StringReader(text))
                val rules = mutableListOf<BlockRule>()

                db.withTransaction {
                    db.blockRuleDao().deleteByCategory(category)

                    var line: String? = reader.readLine()
                    while (line != null) {
                        val domain = parseLine(line)
                        if (domain != null) {
                            rules.add(BlockRule(domain = domain, category = category))
                        }

                        if (rules.size >= 1000) {
                            db.blockRuleDao().insertBlockRules(rules)
                            count += rules.size
                            rules.clear()
                        }
                        line = reader.readLine()
                    }

                    if (rules.isNotEmpty()) {
                        db.blockRuleDao().insertBlockRules(rules)
                        count += rules.size
                    }
                }
            } catch (e: Exception) {
                Log.e("BlockListImporter", "Error importing blocklist from text", e)
            }
            count
        }
    }

    private fun parseLine(line: String): String? {
        val trimmedLine = line.trim()
        // Gestion améliorée des commentaires en fin de ligne
        if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) return null

        // Nettoyage de la ligne pour ne garder que le domaine avant un éventuel commentaire
        val cleanLine = trimmedLine.split("#")[0].trim()
        if (cleanLine.isEmpty()) return null

        val parts = cleanLine.split(spaceRegex)
        val domain = if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1")) {
            parts[1]
        } else {
            parts[0]
        }
        return if (domain.isNotEmpty()) domain else null
    }
}
