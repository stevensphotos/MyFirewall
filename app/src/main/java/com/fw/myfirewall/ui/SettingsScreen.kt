package com.fw.myfirewall.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.fw.myfirewall.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(modifier: Modifier = Modifier, viewModel: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val showThemeDialog = remember { mutableStateOf(false) }
    val showLanguageDialog = remember { mutableStateOf(false) }

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
    val themeMode by viewModel.themeMode.collectAsState()
    val languageCode by viewModel.languageCode.collectAsState()
    val rootCheckResult by viewModel.rootCheckResult.collectAsState()

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
                // 1. Sauvegarder la préférence dans le ViewModel (existant)
                viewModel.setLanguage(selection)

                // 2. Appliquer immédiatement la nouvelle langue via LanguageManager
                // Cela reconfigure les ressources de l'activité en cours
                com.fw.myfirewall.util.LanguageManager.setLocale(context, selection)

                // 3. Optionnel : Recharger l'activité pour forcer le rafraîchissement complet de l'UI
                // (Souvent nécessaire car certains textes Compose ne se rafraîchissent pas instantanément)
                if (context is Activity) {
                    context.recreate()
                }

                showLanguageDialog.value = false
            }
        )
    }



    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ############################################################################
        // TITLE SECTION
        // ############################################################################
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // ############################################################################
        // MAIN SETTINGS CARD (VPN, ROOT, BOOT)
        // ############################################################################
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(32.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // ############################################################################
                // FIREWALL MODE TITLE
                // ############################################################################
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.Function_type_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                
                // ############################################################################
                // VPN SERVICE ROW
                // ############################################################################
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
                                context.startForegroundService(serviceIntent)
                            }
                        } else {
                            val serviceIntent = Intent(context, MyVpnService::class.java)
                            serviceIntent.action = MyVpnService.ACTION_DISCONNECT
                            context.startService(serviceIntent)
                        }
                    }
                )

                SettingsDivider()

                // ############################################################################
                // ROOT SERVICE ROW
                // ############################################################################
                SettingsItemRow(
                    title = stringResource(R.string.root_service_label),
                    description = stringResource(R.string.root_service_text),
                    icon = Icons.Rounded.AdminPanelSettings,
                    checked = isRootEnabled
                ) { checked ->
                    viewModel.toggleRoot(checked)
                }

                SettingsDivider()
                
                // ############################################################################
                // START ON BOOT ROW
                // ############################################################################
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

        // ############################################################################
        // UI SETTINGS CARD
        // ############################################################################
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
                // ############################################################################
                // UI SETTINGS TITLE
                // ############################################################################
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.ui_settings_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // ############################################################################
                // THEME SELECTION ROW
                // ############################################################################
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
                                text = (themeMode),
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

                // ############################################################################
                // PURE BLACK TOGGLE ROW
                // ############################################################################
                SettingsItemRow(
                    title = stringResource(R.string.pure_black_label),
                    description = stringResource(R.string.pure_black_text),
                    icon = Icons.Rounded.DarkMode,
                    checked = isPureBlackEnabled
                ) { checked ->
                    viewModel.togglePureBlack(checked)

                }
                SettingsDivider()}
            // ############################################################################
            // LANGUAGE SELECTION ROW
            // ############################################################################
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
        thickness = 4.dp,
        color = MaterialTheme.colorScheme.background
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
        confirmButton = {}
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
        confirmButton = {}
    )

@Composable
fun getThemeLabel(key: String): String {
    return when(key) {
        "system" -> stringResource(R.string.theme_system)
        "light" -> stringResource(R.string.theme_light)
        "dark" -> stringResource(R.string.theme_dark)
        else -> stringResource(R.string.theme_system)
    }
}
}

@Composable
private fun getThemeLabel(mode: Int): String {
    // These IDs are examples. Replace them with the actual string IDs from your strings.xml
    return when (mode) {
        0 -> stringResource(R.string.theme_system) // e.g. "System Default"
        1 -> stringResource(R.string.theme_light)          // e.g. "Light"
        2 -> stringResource(R.string.theme_dark)           // e.g. "Dark"
        else -> stringResource(R.string.theme_system)
    }
}

private fun getLanguageLabel(code: String): String {
    return when (code) {
        "en" -> "English"
        "es" -> "Español"
        "fr" -> "Français"
        "de" -> "Deutsch"
        "it" -> "Italiano"
        "pt" -> "Português"
        "ru" -> "Русский"
        "zh" -> "中文"
        "ja" -> "日本語"
        "ko" -> "한국어"
        else -> "System Default" // Or handle unknown codes appropriately
    }
}