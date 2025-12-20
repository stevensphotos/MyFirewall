package com.fw.myfirewall.ui

import android.content.Context
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fw.myfirewall.R
import com.fw.myfirewall.data.AppDatabase
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomRulesScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onNavigateToSaveRules: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rulesText by remember { mutableStateOf("") }

    // Validation logic: letters, numbers or dots
    val isError = rulesText.isNotEmpty() && !rulesText.all { it.isLetterOrDigit() || it == '.' || it.isWhitespace() }

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

    Scaffold(        topBar = {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.custom_rules_title),
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        // Conseil : Utilisez Icons.AutoMirrored.Rounded.ArrowBack pour rester cohérent avec votre thème "Rounded"
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
                    .fillMaxWidth(0.95f),
                label = { Text(stringResource(R.string.custom_rules_label)) },
                shape = RoundedCornerShape(16.dp), // Ajout d'un arrondi pour la cohérence UI
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
                minLines = 5
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ############################################################################
            // BUTTONS SECTION
            // ############################################################################
            Row(
                modifier = Modifier.fillMaxWidth(0.7f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { /* Apply rules logic */ },
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

            // ############################################################################
            // RULES MANAGEMENT CARD
            // ############################################################################
//            RulesManagementCard(
//                onNavigateToSaveRules = onNavigateToSaveRules,
//                onNavigateToCustomRules = { /* Already here */ }
//            )
        }
    }
}
