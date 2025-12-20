package com.fw.myfirewall.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fw.myfirewall.R
import com.fw.myfirewall.viewmodel.RulesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import  androidx.compose.material.icons.filled.Refresh
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomRulesScreen(
    modifier: Modifier = Modifier,
    viewModel: RulesViewModel = viewModel(),
    onBack: () -> Unit,
    onNavigateToSaveRules: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rulesText by remember { mutableStateOf("") }

    // Validation logic: letters, numbers, dots, hyphens, underscores, or wildcards
    val isError = rulesText.isNotEmpty() && !rulesText.all {
        it.isLetterOrDigit() ||
                it == '.' ||
                it == '-' ||
                it == '_' ||
                it == '*' ||
                it == '#' ||
                it == '=' ||
                it == '!' ||
                it == '?' ||
                it == '/' ||
                it == ':' ||
                it == ',' ||
                it == ';' ||
                it.isWhitespace()
    }

    BackHandler(onBack = onBack)

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(it)?.use { outputStream ->
                            outputStream.write(rulesText.toByteArray())
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.export_success), Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.export_failed, e.message), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.custom_rules_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // ############################################################################
            // RULES INPUT SECTION
            // ############################################################################
            OutlinedTextField(
                value = rulesText,
                onValueChange = { rulesText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                label = { Text(stringResource(R.string.custom_rules_label)) },
                shape = RoundedCornerShape(20.dp),
                supportingText = {
                    if (isError) {
                        Text(
                            text = stringResource(R.string.custom_rules_error),
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(stringResource(R.string.custom_rules_supporting_text))
                    }
                },
                isError = isError,
                // Shrink to 5 lines if text is present, stay at 10 if empty
                minLines = if (rulesText.isEmpty()) 10 else 5,
                maxLines = 15
            )

            // ############################################################################
            // BUTTONS SECTION
            // ############################################################################
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { 
                        viewModel.applyCustomRules(rulesText)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isError && rulesText.isNotEmpty()
                ) {
                    Text(stringResource(R.string.apply_button))
                }

                OutlinedButton(
                    onClick = { rulesText = "" },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.clear_button))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ############################################################################
            // EXPORT CARD
            // ############################################################################
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.export_rules_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.export_rules_description),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { exportLauncher.launch("custom_rules.txt") },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.export_button))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // In CustomRules.kt
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.import_applied_rules_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(text = stringResource(R.string.import_applied_rules_description), style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = {
                            scope.launch {
                                val currentRules = viewModel.getCurrentCustomRules()
                                if (currentRules.isNotEmpty()) {
                                    rulesText = currentRules
                                    Toast.makeText(context, "Loaded applied rules", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "No custom rules currently applied", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Text("Load")
                    }
                }
            }

            // RulesManagementCard is commented as per request
//            RulesManagementCard(
//                onNavigateToSaveRules = onNavigateToSaveRules,
//                onNavigateToCustomRules = { /* Already here */ }
//            )
        }
    }
}
