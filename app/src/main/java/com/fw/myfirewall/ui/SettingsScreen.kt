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
 * It observes global states (Theme, Dark Mode, Pure Black) from the ViewModel
 * and wraps the content in the custom MyFirewallTheme to ensure immediate UI updates.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier, viewModel: SettingsViewModel = viewModel()) {
    val settingsViewModel: SettingsViewModel = viewModel
    
    // ############################################################################
    // 1. STATE OBSERVATION
    // Collect settings flows from ViewModel as Compose state
    // ############################################################################
    val isPureBlackEnabled by settingsViewModel.isPureBlackEnabled.collectAsState()
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val isDynamicColorEnabled by settingsViewModel.isDynamicColorEnabled.collectAsState()

    // ############################################################################
    // 2. THEME CALCULATION
    // Determine light vs dark theme based on the user's preference or system setting
    // ############################################################################
    val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    // Apply the theme wrapper to propagate changes instantly to all children
    MyFirewallTheme(
        darkTheme = darkTheme,
        pureBlack = isPureBlackEnabled,
        dynamicColor = isDynamicColorEnabled
    ) {
        // Surface provides the primary background color for the screen
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
 * Handles the actual layout, cards, and individual rows.
 * Includes interaction logic for permissions, dialogs, and navigation.
 */
@Composable
fun SettingsContent(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    
    // ############################################################################
    // 3. UI LOCAL STATES (Dialog Visibility)
    // ############################################################################
    val showThemeDialog = remember { mutableStateOf(false) }
    val showLanguageDialog = remember { mutableStateOf(false) }

    // ############################################################################
    // 4. PERMISSION HANDLERS
    // Setup activity result launcher for system VPN permission requests
    // ############################################################################
    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Permission granted by user, start service in Connect mode
            val intent = Intent(context, MyVpnService::class.java)
            intent.action = MyVpnService.ACTION_CONNECT
            context.startForegroundService(intent)
        }
    }
    
    // Collection of state for row switches and values
    val isRootEnabled by viewModel.isRootEnabled.collectAsState()
    val isStartOnBootEnabled by viewModel.isStartOnBootEnabled.collectAsState()
    val isPureBlackEnabled by viewModel.isPureBlackEnabled.collectAsState()
    val isDynamicColorEnabled by viewModel.isDynamicColorEnabled.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val languageCode by viewModel.languageCode.collectAsState()
    val rootCheckResult by viewModel.rootCheckResult.collectAsState()

    // ############################################################################
    // 5. ASYNCHRONOUS FEEDBACK (Root Checks)
    // Listen for root verification results triggered by ViewModel
    // ############################################################################
    LaunchedEffect(rootCheckResult) {
        rootCheckResult?.let { hasRoot ->
            if (hasRoot) {
                Toast.makeText(context, context.getString(R.string.root_access_granted), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.root_access_not_available), Toast.LENGTH_SHORT).show()
            }
            // Clear the result once processed
            viewModel.resetRootCheckResult()
        }
    }

    // ############################################################################
    // 6. DIALOG RENDERING
    // Conditionally show selection popups
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
                // Locale update logic via LanguageManager
                com.fw.myfirewall.util.LanguageManager.setLocale(context, selection)
                // Recreate activity to apply changes to non-composable parts
                if (context is Activity) {
                    context.recreate()
                }
                showLanguageDialog.value = false
            }
        )
    }

    // ############################################################################
    // 7. SCREEN LAYOUT (Vertical Scroll)
    // ############################################################################
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // ----------------------------------------------------------------------------
        // CARD: FIREWALL ENGINE
        // Functional core components (VPN, URL Filter, Start on Boot)
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
                // Header Label for Engine section
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.Function_type_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                
                // ITEM: Applications Filter (VPN based)
                SettingsItemRow(
                    title = stringResource(R.string.vpn_service_label),
                    description = stringResource(R.string.vpn_service_text),
                    icon = Icons.Rounded.VpnKey,
                    checked = MyVpnService.isServiceRunning.value,
                    onCheckedChange = { checked ->
                        if (checked) {
                            // Check for system VPN permission
                            val intent = VpnService.prepare(context)
                            if (intent != null) {
                                vpnLauncher.launch(intent)
                            } else {
                                val serviceIntent = Intent(context, MyVpnService::class.java)
                                serviceIntent.action = MyVpnService.ACTION_CONNECT
                                context.startForegroundService(serviceIntent)
                            }
                        } else {
                            // Disconnect service
                            val serviceIntent = Intent(context, MyVpnService::class.java)
                            serviceIntent.action = MyVpnService.ACTION_DISCONNECT
                            context.startService(serviceIntent)
                        }
                    }
                )

                SettingsDivider()

                // ITEM: URL Filter (Root based)
                SettingsItemRow(
                    title = stringResource(R.string.root_service_label),
                    description = stringResource(R.string.root_service_text),
                    icon = Icons.Rounded.AdminPanelSettings,
                    checked = isRootEnabled
                ) { checked ->
                    // Logic: The ViewModel handles root check before allowing state change
                    viewModel.toggleRoot(checked)
                }

                SettingsDivider()
                
                // ITEM: Boot Start
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
        // Visual customisation (Theme, Color Mode, Language)
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
                // Header Label for UI section
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.ui_settings_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // ITEM: Theme Mode Selector
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
                        contentDescription = "Configure",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SettingsDivider()

                // ITEM: Dynamic Color (Material You) Toggle
                SettingsItemRow(
                    title = stringResource(R.string.dynamic_color_label),
                    description = stringResource(R.string.dynamic_color_text),
                    icon = Icons.Rounded.AutoFixHigh,
                    checked = isDynamicColorEnabled
                ) { checked ->
                    viewModel.toggleDynamicColor(checked)
                }

                SettingsDivider()

                // ITEM: Pure Black Toggle (AMOLED)
                SettingsItemRow(
                    title = stringResource(R.string.pure_black_label),
                    description = stringResource(R.string.pure_black_text),
                    icon = Icons.Rounded.DarkMode,
                    checked = isPureBlackEnabled
                ) { checked ->
                    viewModel.togglePureBlack(checked)
                }

                SettingsDivider()

                // ITEM: Language Selector
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
                        contentDescription = "Navigate",
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

/**
 * SettingsItemRow: Standardised row layout for settings containing
 * an icon, title, description, and an interactive Switch.
 */
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

/**
 * SettingsDivider: Standard horizontal line separating items in cards.
 */
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

/**
 * ThemeDialog: Popup window allowing users to choose light, dark, or system default.
 */
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

/**
 * LanguageDialog: Popup window for localization preference selection.
 */
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

/**
 * Maps preference keys to localized UI labels for the Theme picker.
 */
@Composable
private fun getThemeLabel(key: String): String {
    return when(key) {
        "light" -> stringResource(R.string.theme_light)
        "dark" -> stringResource(R.string.theme_dark)
        else -> stringResource(R.string.theme_system)
    }
}

/**
 * Maps internal language codes to human-readable names.
 */
private fun getLanguageLabel(code: String): String {
    return when (code) {
        "en" -> "English"
        "fr" -> "Français"
        else -> "System Default"
    }
}
