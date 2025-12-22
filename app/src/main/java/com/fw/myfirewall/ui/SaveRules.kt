package com.fw.myfirewall.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fw.myfirewall.R
import com.fw.myfirewall.data.AppDatabase
import com.fw.myfirewall.data.AppRule
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import androidx.compose.material.icons.automirrored.rounded.ArrowBack


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveRulesScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = AppDatabase.getDatabase(context)

    BackHandler(onBack = onBack)

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                exportRules(context, db, it)
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                importRules(context, db, it)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    text = stringResource(R.string.save_rules_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack, // Modification ici
                            contentDescription = stringResource(R.string.save_rules_back_desc)
                        )
                    }
                }

            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            
            // Export Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(0.95f),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.export_rules_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.export_rules_description),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { exportLauncher.launch("myfirewall_rules.json") },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.export_button))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Import Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(0.95f),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.import_rules_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.import_rules_description),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.import_button))
                    }
                }
            }
        }
    }
}

private suspend fun importRules(context: Context, db: AppDatabase, uri: Uri) {
    withContext(Dispatchers.IO) {
        try {
            val stringBuilder = StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        stringBuilder.append(line)
                        line = reader.readLine()
                    }
                }
            }

            val json = stringBuilder.toString()
            val gson = Gson()

            // On essaie de détecter le type de contenu dans le JSON
            if (json.contains("\"domain\"")) {
                // Importation de BlockRules (URL Filter)
                val listType = object : TypeToken<List<com.fw.myfirewall.data.BlockRule>>() {}.type
                val rules: List<com.fw.myfirewall.data.BlockRule> = gson.fromJson(json, listType)
                db.blockRuleDao().insertBlockRules(rules)

                notifyVpnToReload(context) // Prévenir le VPN

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Importé ${rules.size} règles URL avec succès", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Importation de AppRules (Application Filter)
                val listType = object : TypeToken<List<AppRule>>() {}.type
                val rules: List<AppRule> = gson.fromJson(json, listType)
                rules.forEach { db.appRuleDao().insertRule(it) }

                notifyVpnToReload(context) // Prévenir le VPN

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Importé ${rules.size} règles App avec succès", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Échec de l'importation: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
private fun notifyVpnToReload(context: Context) {
    val intent = android.content.Intent(context, com.fw.myfirewall.service.MyVpnService::class.java).apply {
        action = "com.fw.myfirewall.ACTION_RELOAD_RULES"
    }
    context.startService(intent)
}
private suspend fun exportRules(context: Context, db: AppDatabase, uri: Uri) {
    withContext(Dispatchers.IO) {
        try {            // On récupère toutes les règles (App + Block)
            val appRules = db.appRuleDao().getAllRules().first()
            val blockRules = db.blockRuleDao().getAllRulesSync()

            val gson = Gson()

            // On décide quoi exporter : si on a des domaines, on exporte les BlockRules
            // Sinon on exporte les AppRules.
            // Note : Pour un export complet, vous pourriez créer une classe "Backup" regroupant les deux.
            val json = if (blockRules.isNotEmpty()) {
                gson.toJson(blockRules)
            } else {
                gson.toJson(appRules)
            }

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toByteArray())
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.export_success, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Échec de l'exportation : ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
