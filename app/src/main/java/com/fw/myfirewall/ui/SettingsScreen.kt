package com.fw.myfirewall.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fw.myfirewall.R
import com.fw.myfirewall.service.MyVpnService
import com.fw.myfirewall.ui.theme.MyFirewallTheme
import com.fw.myfirewall.viewmodel.SettingsViewModel

/**
 * SettingsScreen Composable:
 * Acts as the top-level container for the settings UI.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier, viewModel: SettingsViewModel = viewModel()) {
    val settingsViewModel: SettingsViewModel = viewModel

    // ############################################################################
    // 1. STATE OBSERVATION
    // ############################################################################
    val isPureBlackEnabled by settingsViewModel.isPureBlackEnabled.collectAsState()
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val isDynamicColorEnabled by settingsViewModel.isDynamicColorEnabled.collectAsState()

    // ############################################################################
    // 2. THEME CALCULATION
    // ############################################################################
    val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    MyFirewallTheme(
        darkTheme = darkTheme,
        pureBlack = isPureBlackEnabled,
        dynamicColor = isDynamicColorEnabled
    ) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            SettingsContent(viewModel = settingsViewModel)
        }
    }
}

/**
 * SettingsContent Composable:
 * Handles layout, category cards, and the new comprehensive tweaking section.
 */
@Composable
fun SettingsContent(viewModel: SettingsViewModel) {
    val context = LocalContext.current

    // ############################################################################
    // 3. UI LOCAL STATES
    // ############################################################################
    val showThemeDialog = remember { mutableStateOf(false) }
    val showLanguageDialog = remember { mutableStateOf(false) }
    val showAboutDialog = remember { mutableStateOf(false) }

    // ############################################################################
    // 4. PERMISSION HANDLERS
    // ############################################################################
    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = Intent(context, MyVpnService::class.java)
            intent.action = MyVpnService.ACTION_CONNECT
            context.startForegroundService(intent)
        }
    }

    val isRootEnabled by viewModel.isRootEnabled.collectAsState()
    val isStartOnBootEnabled by viewModel.isStartOnBootEnabled.collectAsState()
    val isPureBlackEnabled by viewModel.isPureBlackEnabled.collectAsState()
    val isDynamicColorEnabled by viewModel.isDynamicColorEnabled.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val languageCode by viewModel.languageCode.collectAsState()
    val rootCheckResult by viewModel.rootCheckResult.collectAsState()

    // Feedback for root check
    LaunchedEffect(rootCheckResult) {
        rootCheckResult?.let { hasRoot ->
            if (hasRoot) {
                Toast.makeText(context, context.getString(R.string.root_access_granted), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.root_access_not_available), Toast.LENGTH_SHORT).show()
            }
            viewModel.resetRootCheckResult()
        }
    }

    // ############################################################################
    // 5. DIALOG RENDERING
    // ############################################################################
    if (showThemeDialog.value) {
        ThemeDialog(
            currentSelection = themeMode,
            onDismissRequest = { showThemeDialog.value = false },
            onConfirmation = { selection ->
                viewModel.setThemeMode(selection)
                showThemeDialog.value = false
            }
        )
    }
    if (showLanguageDialog.value) {
        LanguageDialog(
            currentSelection = languageCode,
            onDismissRequest = { showLanguageDialog.value = false },
            onConfirmation = { selection ->
                viewModel.setLanguage(selection)
                com.fw.myfirewall.util.LanguageManager.setLocale(context, selection)
                if (context is Activity) {
//                    context.recreate()
                }
                showLanguageDialog.value = false
            }
        )
    }
    if (showAboutDialog.value) {
        AboutDialog(onDismissRequest = { showAboutDialog.value = false })
    }

    // ############################################################################
    // 6. MAIN LAYOUT
    // ############################################################################
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ROW: HEADER SECTION (Title on left, Icon on right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically

        ) {
            // 1. LE TITRE (À GAUCHE)
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp) // Aligne le texte avec le bord des cartes
                    .padding(top = 8.dp, bottom = 16.dp)

            )

            // 2. LE SPACER (PREND TOUT L'ESPACE DISPONIBLE)
            Spacer(modifier = Modifier.weight(1f))

            // 3. L'ICÔNE D'AIDE (À DROITE)
            IconButton(
                onClick = { showAboutDialog.value = true },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.HelpOutline,
                    contentDescription = "About",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // ----------------------------------------------------------------------------
        // CARD: FIREWALL ENGINE
        // ----------------------------------------------------------------------------
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(32.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.Function_type_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                SettingsItemRow(
                    title = stringResource(R.string.vpn_service_label),
                    description = stringResource(R.string.vpn_service_text),
                    icon = Icons.Rounded.VpnKey,
                    checked = MyVpnService.isServiceRunning.value,
                    onCheckedChange = { checked ->
                        if (checked) {
                            val intent = VpnService.prepare(context)
                            if (intent != null) {
                                vpnLauncher.launch(intent)
                            } else {
                                val serviceIntent = Intent(context, MyVpnService::class.java)
                                serviceIntent.action = MyVpnService.ACTION_CONNECT
                                context.startForegroundService(intent)
                            }
                        } else {
                            val serviceIntent = Intent(context, MyVpnService::class.java)
                            serviceIntent.action = MyVpnService.ACTION_DISCONNECT
                            context.startService(serviceIntent)
                        }
                    }
                )

                SettingsDivider()

                SettingsItemRow(
                    title = stringResource(R.string.root_service_label),
                    description = stringResource(R.string.root_service_text),
                    icon = Icons.Rounded.AdminPanelSettings,
                    checked = isRootEnabled
                ) { checked ->
                    viewModel.toggleRoot(checked)
                }

                SettingsDivider()

                SettingsItemRow(
                    title = stringResource(R.string.start_on_boot_label),
                    description = stringResource(R.string.start_on_boot_text),
                    icon = Icons.Rounded.PowerSettingsNew,
                    checked = isStartOnBootEnabled
                ) { checked ->
                    viewModel.toggleStartOnBoot(checked)
                }
            }
        }

        // ----------------------------------------------------------------------------
        // CARD: USER INTERFACE
        // ----------------------------------------------------------------------------
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(32.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.ui_settings_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showThemeDialog.value = true }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Palette,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.theme_settings_label),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = getThemeLabel(themeMode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowForwardIos,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SettingsDivider()

                SettingsItemRow(
                    title = stringResource(R.string.dynamic_color_label),
                    description = stringResource(R.string.dynamic_color_text),
                    icon = Icons.Rounded.AutoFixHigh,
                    checked = isDynamicColorEnabled
                ) { checked ->
                    viewModel.toggleDynamicColor(checked)
                }

                SettingsDivider()

                SettingsItemRow(
                    title = stringResource(R.string.pure_black_label),
                    description = stringResource(R.string.pure_black_text),
                    icon = Icons.Rounded.DarkMode,
                    checked = isPureBlackEnabled
                ) { checked ->
                    viewModel.togglePureBlack(checked)
                }

                SettingsDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLanguageDialog.value = true }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Language,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.language_settings_label),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = getLanguageLabel(languageCode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowForwardIos,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ############################################################################
// HELPER COMPOSABLES
// ############################################################################

@Composable
fun SettingsItemRow(
    title: String,
    description: String? = null,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
fun ThemeDialog(
    currentSelection: String,
    onDismissRequest: () -> Unit,
    onConfirmation: (String) -> Unit
) {
    val options = listOf(
        "system" to R.string.theme_system,
        "light" to R.string.theme_light,
        "dark" to R.string.theme_dark
    )

    AlertDialog(
        onDismissRequest = { onDismissRequest() },
        title = { Text(text = stringResource(R.string.theme_dialog_title)) },
        text = {
            Column {
                options.forEach { (key, labelRes) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onConfirmation(key) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        RadioButton(
                            selected = (key == currentSelection),
                            onClick = { onConfirmation(key) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun LanguageDialog(
    currentSelection: String,
    onDismissRequest: () -> Unit,
    onConfirmation: (String) -> Unit
) {
    val options = listOf(
        "system" to R.string.language_system,
        "en" to R.string.language_english,
        "fr" to R.string.language_french
    )

    AlertDialog(
        onDismissRequest = { onDismissRequest() },
        title = { Text(text = stringResource(R.string.language_dialog_title)) },
        text = {
            Column {
                options.forEach { (key, labelRes) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onConfirmation(key) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        RadioButton(
                            selected = (key == currentSelection),
                            onClick = { onConfirmation(key) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun getThemeLabel(key: String): String {
    return when(key) {
        "light" -> stringResource(R.string.theme_light)
        "dark" -> stringResource(R.string.theme_dark)
        else -> stringResource(R.string.theme_system)
    }
}

private fun getLanguageLabel(code: String): String {
    return when (code) {
        "en" -> "English"
        "fr" -> "Français"
        else -> "System Default"
    }
}
