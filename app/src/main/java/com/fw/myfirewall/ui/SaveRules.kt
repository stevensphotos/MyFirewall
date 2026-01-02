package com.fw.myfirewall.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fw.myfirewall.R
import com.fw.myfirewall.data.AppDatabase
import com.fw.myfirewall.data.AppRule
import com.fw.myfirewall.data.BlockRule
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import androidx.room.withTransaction
import kotlinx.coroutines.withContext

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

    // ############################################################################
    // EXPORT LAUNCHERS
    // ############################################################################

    val exportAppRulesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { scope.launch { exportRules(context, db, it, isAppRules = true) } }
    }

    val exportUrlRulesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { scope.launch { exportRules(context, db, it, isAppRules = false) } }
    }

    // ############################################################################
    // IMPORT LAUNCHER
    // ############################################################################

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { scope.launch { importRules(context, db, it) } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.save_rules_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
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

            // --- SECTION 1: EXPORT ---
            var exportExpanded by remember { mutableStateOf(false) }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(0.95f),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { exportAppRulesLauncher.launch("myfirewall_app_rules.json") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.export_button))
                        }

                        Button(
                            onClick = { exportExpanded = true },
                            modifier = Modifier
                                .width(48.dp)
                                .offset(x = (-1).dp),
                            shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            DropdownMenu(
                                expanded = exportExpanded,
                                onDismissRequest = { exportExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.export_app_rules_label)) },
                                    onClick = {
                                        exportExpanded = false
                                        exportAppRulesLauncher.launch("myfirewall_app_rules.json")
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Download,
                                            contentDescription = null
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.export_url_rules_label)) },
                                    onClick = {
                                        exportExpanded = false
                                        exportUrlRulesLauncher.launch("myfirewall_url_rules.json")
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Download,
                                            contentDescription = null
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- SECTION 2: IMPORT ---
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(0.95f),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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

                    // SPLIT IMPORT BUTTON
                    var importExpanded by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { importLauncher.launch(arrayOf("application/json")) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.import_button))
                        }

                        Button(
                            onClick = { importExpanded = true },
                            modifier = Modifier
                                .width(48.dp)
                                .offset(x = (-1).dp),
                            shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            DropdownMenu(
                                expanded = importExpanded,
                                onDismissRequest = { importExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.import_app_rules_label)) },
                                    onClick = {
                                        importExpanded = false
                                        importLauncher.launch(arrayOf("application/json"))
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Upload,
                                            contentDescription = null
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.import_url_rules_label)) },
                                    onClick = {
                                        importExpanded = false
                                        importLauncher.launch(arrayOf("application/json"))
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Upload,
                                            contentDescription = null
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ############################################################################
// HELPER FUNCTIONS
// ############################################################################

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

                    // Regex de validation pour la sécurité
                val packageRegex = "^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+[0-9a-z_]\$".toRegex()

                if (json.contains("\"domain\"")) {
                    // --- Import URL Rules ---
                    val listType = object : TypeToken<List<BlockRule>>() {}.type
                    val rawRules: List<BlockRule> = gson.fromJson(json, listType)

                    // Filtrage et validation de sécurité
                    val validatedRules = rawRules.filter {
                        it.domain.isNotBlank() && it.domain.length < 255
                    }

                                        if (validatedRules.isNotEmpty()) {
                    // Utilisation de la transaction atomique (nécessite room-ktx)
                    db.withTransaction {
                        db.blockRuleDao().insertBlockRules(validatedRules)
                    }
                        notifyVpnToReload(context)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "Imported ${validatedRules.size} URL rules successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    // --- Import App Rules ---
                    val listType = object : TypeToken<List<AppRule>>() {}.type
                    val rawRules: List<AppRule> = gson.fromJson(json, listType)

                    // Validation rigoureuse des packages (Sécurité injection/DoS)
                    val validatedRules = rawRules.filter {
                        it.packageName.length in 3..255 && packageRegex.matches(it.packageName)
                    }

                    if (validatedRules.isNotEmpty()) {
                        db.withTransaction {
                            validatedRules.forEach { db.appRuleDao().insertRule(it) }
                        }

                        notifyVpnToReload(context)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.import_success, validatedRules.size),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.import_failed, e.message),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        private fun notifyVpnToReload(context: Context) {
            val intent =
                android.content.Intent(context, com.fw.myfirewall.service.MyVpnService::class.java)
                    .apply {
                        action = "com.fw.myfirewall.ACTION_RELOAD_RULES"
                    }
            context.startService(intent)
        }

        private suspend fun exportRules(
            context: Context,
            db: AppDatabase,
            uri: Uri,
            isAppRules: Boolean
        ) {
            withContext(Dispatchers.IO) {
                try {
                    val gson = Gson()
                    val json = if (isAppRules) {
                        val appRules = db.appRuleDao().getAllRules().first()
                        gson.toJson(appRules)
                    } else {
                        val blockRules = db.blockRuleDao().getAllRulesSync()
                        gson.toJson(blockRules)
                    }

                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(json.toByteArray())
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.export_success),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.export_failed, e.message),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

