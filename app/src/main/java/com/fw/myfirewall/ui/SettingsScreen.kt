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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
    onNavigateToHelp: () -> Unit = {}
) {
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
            SettingsContent(viewModel = settingsViewModel, onNavigateToHelp = onNavigateToHelp)
        }
    }
}

/**
 * SettingsContent Composable:
 * Handles layout, category cards, and the new comprehensive tweaking section.
 */
@Composable
fun SettingsContent(viewModel: SettingsViewModel, onNavigateToHelp: () -> Unit) {
    val context = LocalContext.current

    val rootGrantedMsg = stringResource(R.string.root_access_granted)
    val rootDeniedMsg = stringResource(R.string.root_access_not_available)

    // ############################################################################
    // 3. UI LOCAL STATES
    // ############################################################################
    val showThemeDialog = remember { mutableStateOf(false) }
    val showLanguageDialog = remember { mutableStateOf(false) }
    val showDnsDialog = remember { mutableStateOf(false) }

    // Store the pending action to execute after VPN permission is granted
    val pendingVpnAction = remember { mutableStateOf<(() -> Unit)?>(null) }

    // ############################################################################
    // 4. PERMISSION HANDLERS
    // ############################################################################
    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingVpnAction.value?.invoke()
        }
        pendingVpnAction.value = null
    }

    val isRootEnabled by viewModel.isRootEnabled.collectAsState()
    val isAppFilterEnabled by viewModel.isAppFilterEnabled.collectAsState()
    val isUrlFilterEnabled by viewModel.isUrlFilterEnabled.collectAsState()
    val isStartOnBootEnabled by viewModel.isStartOnBootEnabled.collectAsState()
    val isPureBlackEnabled by viewModel.isPureBlackEnabled.collectAsState()
    val isDynamicColorEnabled by viewModel.isDynamicColorEnabled.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val languageCode by viewModel.languageCode.collectAsState()
    val rootCheckResult by viewModel.rootCheckResult.collectAsState()
    val isRootPossible by viewModel.isRootPossible.collectAsState()

    // Helper to trigger VPN actions with permission check
    fun performVpnAction(enable: Boolean, action: () -> Unit) {
        if (enable) {
            val intent = VpnService.prepare(context)
            if (intent != null) {
                pendingVpnAction.value = action
                vpnLauncher.launch(intent)
            } else {
                action()
            }
        } else {
            action()
        }
    }

    // Feedback for root check
    LaunchedEffect(rootCheckResult) {
        rootCheckResult?.let { hasRoot ->
            if (hasRoot) {
                Toast.makeText(context, rootGrantedMsg, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, rootDeniedMsg, Toast.LENGTH_SHORT).show()
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
    if (showDnsDialog.value) {
        DnsDialog(
            currentPrimary = viewModel.getDnsPrimary(),
            currentSecondary = viewModel.getDnsSecondary(),
            onDismissRequest = { showDnsDialog.value = false },
            onConfirmation = { primary, secondary ->
                viewModel.setDnsServer(primary, secondary)
                // Redémarrer le VPN si actif pour appliquer les changements
                if (isUrlFilterEnabled) {
                    val intent = Intent(context, MyVpnService::class.java).apply {
                        action = "com.fw.myfirewall.ACTION_RELOAD_RULES"
                    }
                    context.startService(intent)
                }
                showDnsDialog.value = false
            }
        )
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
                // --- HEADER DE CARTE AVEC ICÔNE À GAUCHE ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween // Pousse les éléments aux extrémités
                ) {
                    // L'ICÔNE D'AIDE À GAUCHE (Maintenant lance Onboarding)
                    Text(
                        text = stringResource(R.string.Function_type_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    // L'ICÔNE D'AIDE (LANCE L'ONBOARDING)
                    IconButton(
                        onClick = onNavigateToHelp,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                            contentDescription = "Help",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // APPLICATION FILTER
                SettingsItemRow(
                    title = stringResource(R.string.vpn_service_label),
                    description = stringResource(R.string.vpn_service_text),
                    icon = Icons.Rounded.VpnKey,
                    checked = isAppFilterEnabled,
                    onCheckedChange = { checked ->
                        performVpnAction(checked) {
                            viewModel.toggleAppFilter(checked)
                        }
                    }
                )

                SettingsDivider()

                //**********************************
                //********** URL FILTER VPN ************
                //**********************************
                SettingsItemRow(
                    title = stringResource(R.string.url_filter_label),
                    description=  stringResource (R.string.url_filter_desc),
                    icon = Icons.Rounded.FilterAlt,
                    checked = isUrlFilterEnabled,
                    onCheckedChange = { checked ->
                        performVpnAction(checked) {
                            viewModel.toggleUrlFilter(checked)
                        }
                    }
                )

                SettingsDivider()
                //**********************************
                //******* URL FILTER ROOT*******
                //**********************************

                // Determine label color based on root availability
                val labelColor = if (isRootPossible)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.error


                SettingsItemRow(
                    title = if (isRootPossible)
                        stringResource(R.string.root_service_label)
                    else
                        "${stringResource(R.string.root_service_label)} (${stringResource(R.string.not_available)})",
                    description = stringResource(R.string.root_service_text),
                    icon = if (isRootPossible) Icons.Rounded.AdminPanelSettings else Icons.Rounded.Block,
                    checked = isRootEnabled,
                    titleColor = labelColor
                ) { checked ->
                    viewModel.toggleRoot(checked)
                }


                SettingsDivider()

                //START ON BOOT OPTION


                SettingsItemRow(
                    title = stringResource(R.string.start_on_boot_label),
                    description = stringResource(R.string.start_on_boot_text),
                    icon = Icons.Rounded.PowerSettingsNew,
                    checked = isStartOnBootEnabled,
                    onCheckedChange = { checked ->
                        // On passe le contexte pour le Toast d'erreur
                        viewModel.toggleStartOnBoot(checked, context)
                    }
                )

                SettingsDivider()

                // DNS SETTINGS ROW
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDnsDialog.value = true }
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
                                    imageVector = Icons.Rounded.Dns,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.dns_settings_label),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = stringResource(R.string.dns_settings_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
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
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
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
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
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
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor
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

/**
 * DnsDialog: Custom DNS configuration.
 */
@Composable
fun DnsDialog(
    currentPrimary: String,
    currentSecondary: String,
    onDismissRequest: () -> Unit,
    onConfirmation: (String, String) -> Unit
) {
    val dnsPresets = listOf(
        Triple("system", R.string.dns_preset_system, "" to ""),
        Triple("google", R.string.dns_preset_google, "8.8.8.8" to "8.8.4.4"),
        Triple("cloudflare", R.string.dns_preset_cloudflare, "1.1.1.1" to "1.0.0.1"),
        Triple("quad9", R.string.dns_preset_quad9, "9.9.9.9" to "149.112.112.112"),
        Triple("custom", R.string.dns_preset_custom, "" to "")
    )

    // Détecter le preset actuel
    val initialPreset = dnsPresets.find {
        it.third.first == currentPrimary && it.third.second == currentSecondary
    }?.first ?: if (currentPrimary.isEmpty()) "system" else "custom"

    var selectedPreset by remember { mutableStateOf(initialPreset) }
    var primaryDns by remember { mutableStateOf(currentPrimary) }
    var secondaryDns by remember { mutableStateOf(currentSecondary) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(R.string.dns_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Preset options
                dnsPresets.forEach { (key, labelRes, dnsValues) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedPreset = key
                                val preset = dnsPresets.find { it.first == key }
                                if (preset != null) {
                                    primaryDns = preset.third.first
                                    secondaryDns = preset.third.second
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        RadioButton(
                            selected = (key == selectedPreset),
                            onClick = {
                                selectedPreset = key
                                val preset = dnsPresets.find { it.first == key }
                                if (preset != null) {
                                    primaryDns = preset.third.first
                                    secondaryDns = preset.third.second
                                }
                            }
                        )
                    }
                }

                if (selectedPreset == "custom") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = primaryDns,
                        onValueChange = { primaryDns = it },
                        label = { Text(stringResource(R.string.dns_primary_label)) },
                        placeholder = { Text("8.8.8.8") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = secondaryDns,
                        onValueChange = { secondaryDns = it },
                        label = { Text(stringResource(R.string.dns_secondary_label)) },
                        placeholder = { Text("8.8.4.4") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirmation(primaryDns, secondaryDns) }) {
                Text(stringResource(R.string.dns_apply))
            }
        },
        dismissButton = {
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
