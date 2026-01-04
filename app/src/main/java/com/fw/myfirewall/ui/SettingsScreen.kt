package com.fw.myfirewall.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fw.myfirewall.R
import com.fw.myfirewall.service.MyVpnService
import com.fw.myfirewall.ui.theme.MyFirewallTheme
import com.fw.myfirewall.viewmodel.SettingsViewModel

/**
 * SettingsScreen Composable:
 * Point d'entrée principal de l'écran des paramètres.
 * Gère l'application du thème global en fonction de l'état du ViewModel.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
    onNavigateToHelp: () -> Unit = {}
) {
    val settingsViewModel: SettingsViewModel = viewModel

    // ############################################################################
    // 1. OBSERVATION DE L'ÉTAT (UI THEME STATE)
    // Récupère les préférences utilisateur stockées dans le ViewModel via StateFlow
    // ############################################################################
    val isPureBlackEnabled by settingsViewModel.isPureBlackEnabled.collectAsState()
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val isDynamicColorEnabled by settingsViewModel.isDynamicColorEnabled.collectAsState()

    // ############################################################################
    // 2. LOGIQUE DU THÈME
    // Calcule dynamiquement le mode sombre/clair pour MyFirewallTheme
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
 * Gère la disposition graphique, les cartes de catégories et les interactions.
 * Centralise les appels vers le ViewModel pour modifier les préférences.
 */
@Composable
fun SettingsContent(viewModel: SettingsViewModel, onNavigateToHelp: () -> Unit) {
    val context = LocalContext.current

    val rootGrantedMsg = stringResource(R.string.root_access_granted)
    val rootDeniedMsg = stringResource(R.string.root_access_not_available)

    // ############################################################################
    // 3. ÉTATS LOCAUX DE L'INTERFACE (UI VISIBILITY)
    // ############################################################################
    val showThemeDialog = remember { mutableStateOf(false) }
    val showLanguageDialog = remember { mutableStateOf(false) }
    val showDnsDialog = remember { mutableStateOf(false) }

    // Stocke temporairement une action Lambda à exécuter après l'accord système VPN
    val pendingVpnAction = remember { mutableStateOf<(() -> Unit)?>(null) }

    // ############################################################################
    // 4. GESTIONNAIRES DE PERMISSIONS ET ÉTATS DU VIEWMODEL
    // Liaison directe avec les préférences persistantes (DataStore/SharedPreferences)
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
    val isAppLockEnabled by viewModel.isAppLockEnabled.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val languageCode by viewModel.languageCode.collectAsState()
    val rootCheckResult by viewModel.rootCheckResult.collectAsState()
    val isRootPossible by viewModel.isRootPossible.collectAsState()

    // ############################################################################
    // 5. FONCTIONS UTILITAIRES ET EFFETS (SIDE EFFECTS)
    // ############################################################################

    /**
     * performVpnAction:
     * Vérifie si le système Android autorise le service VPN avant d'appeler
     * les fonctions de changement d'état du ViewModel.
     */
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

    /**
     * Observateur du résultat Root:
     * Affiche un Toast suite à une demande de privilèges root via le ViewModel.
     */
    LaunchedEffect(rootCheckResult) {
        rootCheckResult?.let { hasRoot ->
            val msg = if (hasRoot) rootGrantedMsg else rootDeniedMsg
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.resetRootCheckResult()
        }
    }

    // ############################################################################
    // 6. RENDU DES DIALOGUES (OVERLAYS)
    // Permet la saisie utilisateur pour les paramètres complexes.
    // ############################################################################

    // Sélecteur de Thème : Appelle viewModel.setThemeMode
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

    // Sélecteur de Langue : Appelle viewModel.setLanguage et met à jour la Locale
    if (showLanguageDialog.value) {
        LanguageDialog(
            currentSelection = languageCode,
            onDismissRequest = { showLanguageDialog.value = false },
            onConfirmation = { selection ->
                // 1. Mettre à jour l'état dans le ViewModel et SharedPreferences
                viewModel.setLanguage(selection)

                // 2. Appliquer la nouvelle locale au contexte système
                com.fw.myfirewall.util.LanguageManager.setLocale(context, selection)

                // 3. Fermer le dialogue
                showLanguageDialog.value = false

                // 4. CORRECTION : Forcer la recréation de l'activité pour rafraîchir les ressources
                (context as? android.app.Activity)?.recreate()
            }
        )
    }

    // Configuration DNS : Met à jour les serveurs dans le ViewModel et recharge le service VPN
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
    // 7. DISPOSITION PRINCIPALE (UI LAYOUT)
    // Structure les options par catégories visuelles (Cartes).
    // ############################################################################
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- SECTION: MOTEUR FIREWALL ---
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(32.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.Function_type_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = onNavigateToHelp, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                            contentDescription = "Help",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // FILTRE D'APPLICATION : Bascule le mode VPN d'interception d'apps
                SettingsItemRow(
                    title = stringResource(R.string.vpn_service_label),
                    description = stringResource(R.string.vpn_service_text),
                    icon = Icons.Rounded.VpnKey,
                    checked = isAppFilterEnabled,
                    onCheckedChange = { checked -> performVpnAction(checked) { viewModel.toggleAppFilter(checked) } }
                )

                SettingsDivider()

                //**********************************
                //********** URL FILTER VPN ************
                //**********************************
                SettingsItemRow(
                    title = stringResource(R.string.url_filter_label),
                    description = stringResource(R.string.url_filter_desc),
                    icon = Icons.Rounded.FilterAlt,
                    checked = isUrlFilterEnabled,
                    onCheckedChange = { checked -> performVpnAction(checked) { viewModel.toggleUrlFilter(checked) } }
                )

                SettingsDivider()

                val labelColor = if (isRootPossible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                SettingsItemRow(
                    title = stringResource(R.string.root_service_label),
                    description = stringResource(R.string.root_service_text),
                    icon = Icons.Rounded.Security,
                    checked = isRootEnabled,
                    onCheckedChange = { viewModel.toggleRoot(it) },
                    enabled = isRootPossible,
                    titleColor = labelColor
                )

                SettingsDivider()

                //START ON BOOT OPTION


                SettingsItemRow(
                    title = stringResource(R.string.start_on_boot_label),
                    description = stringResource(R.string.start_on_boot_text),
                    icon = Icons.Rounded.PowerSettingsNew,
                    checked = isStartOnBootEnabled,
                    onCheckedChange = { viewModel.toggleStartOnBoot(it, context) }
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
        // CARD: USER INTERFACE (RESTORED SECTION)
        // ----------------------------------------------------------------------------
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(32.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.ui_settings_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )

                SettingsItemRow(
                    title = stringResource(R.string.app_lock_label),
                    description = stringResource(R.string.app_lock_desc),
                    icon = Icons.Rounded.Fingerprint,
                    checked = isAppLockEnabled,
                    onCheckedChange = { viewModel.toggleAppLock(it) }
                )

                SettingsDivider()

                SettingsItemRow(
                    title = stringResource(R.string.dynamic_color_label),
                    description = stringResource(R.string.dynamic_color_text),
                    icon = Icons.Rounded.AutoFixHigh,
                    checked = isDynamicColorEnabled,
                    onCheckedChange = { viewModel.toggleDynamicColor(it) }
                )

                SettingsDivider()

                SettingsItemRow(
                    title = stringResource(R.string.pure_black_label),
                    description = stringResource(R.string.pure_black_text),
                    icon = Icons.Rounded.DarkMode,
                    checked = isPureBlackEnabled,
                    onCheckedChange = { viewModel.togglePureBlack(it) }
                )

                SettingsDivider()

                // Theme Selection Row
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showThemeDialog.value = true }.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.background, modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Palette, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.theme_settings_label), style = MaterialTheme.typography.bodyLarge)
                            Text(getThemeLabel(themeMode, context), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                SettingsDivider()

                // Language Selection Row
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showLanguageDialog.value = true }.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.background, modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Language, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.language_settings_label), style = MaterialTheme.typography.bodyLarge)
                            Text(getLanguageLabel(languageCode, context), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ############################################################################
// HELPER COMPOSABLES
// ############################################################################

/**
 * SettingsItemRow Composable:
 * Optimized for accessibility and large font sizes.
 */
@Composable
fun SettingsItemRow(
    title: String,
    description: String? = null,
    icon: ImageVector,
    checked: Boolean,
    enabled: Boolean = true,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onCheckedChange(!checked) },
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (enabled) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                    )
                }
            }

            // Text Section: The modifier.weight(1f) is critical for accessibility.
            // It ensures the text wraps instead of pushing the Switch off-screen.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) titleColor else titleColor.copy(alpha = 0.38f),
                    softWrap = true
                )
                if (!description.isNullOrEmpty()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
                        softWrap = true
                    )
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
fun ThemeDialog(currentSelection: String, onDismissRequest: () -> Unit, onConfirmation: (String) -> Unit) {
    val options = listOf("system" to R.string.theme_system, "light" to R.string.theme_light, "dark" to R.string.theme_dark)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.theme_dialog_title)) },
        text = {
            Column {
                options.forEach { (key, labelRes) ->
                    Row(Modifier.fillMaxWidth().clickable { onConfirmation(key) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        RadioButton(selected = (key == currentSelection), onClick = { onConfirmation(key) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun LanguageDialog(currentSelection: String, onDismissRequest: () -> Unit, onConfirmation: (String) -> Unit) {
    val options = listOf("system" to R.string.language_system, "en" to R.string.language_english, "fr" to R.string.language_french)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.language_dialog_title)) },
        text = {
            Column {
                options.forEach { (key, labelRes) ->
                    Row(Modifier.fillMaxWidth().clickable { onConfirmation(key) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        RadioButton(selected = (key == currentSelection), onClick = { onConfirmation(key) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.cancel)) } }
    )
}

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
        title = { Text(stringResource(R.string.dns_dialog_title)) },
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
                    Spacer(modifier = Modifier.height(


                        8.dp))
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

private fun getThemeLabel(mode: String, context: android.content.Context): String {
    return when (mode) {
        "light" -> context.getString(R.string.theme_light)
        "dark" -> context.getString(R.string.theme_dark)
        else -> context.getString(R.string.theme_system)
    }
}

private fun getLanguageLabel(code: String, context: android.content.Context): String {
    return when (code) {
        "en" -> context.getString(R.string.language_english)
        "fr" -> context.getString(R.string.language_french)
        else -> context.getString(R.string.language_system)
    }
}
