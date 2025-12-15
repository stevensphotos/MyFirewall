package com.fw.myfirewall.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    val rootCheckResult by viewModel.rootCheckResult.collectAsState()

    LaunchedEffect(rootCheckResult) {
        rootCheckResult?.let { hasRoot ->
            if (hasRoot) {
                Toast.makeText(context, "Root access granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Root access not available", Toast.LENGTH_SHORT).show()
            }
            viewModel.resetRootCheckResult()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title at top left
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Main Card grouping all settings
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
                // Firewall Mode Title
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.Function_type_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                
                // VPN Service Row
                SettingsItemRow(
                    title = stringResource(R.string.vpn_service_label),
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

                // Root Service Row
                SettingsItemRow(
                    title = stringResource(R.string.root_service_label),
                    icon = Icons.Rounded.AdminPanelSettings,
                    checked = isRootEnabled,
                    onCheckedChange = { checked ->
                        viewModel.toggleRoot(checked)
                    }
                )
                
                SettingsDivider()
                
                // Start on Boot Row
                SettingsItemRow(
                    title = stringResource(R.string.start_on_boot_label),
                    icon = Icons.Rounded.PowerSettingsNew,
                    checked = isStartOnBootEnabled,
                    onCheckedChange = { checked ->
                        viewModel.toggleStartOnBoot(checked)
                    }
                )
            }
        }

        // Theme Settings Card
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
                // UI Settings Title
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.ui_settings_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // Pure Black Toggle
                SettingsItemRow(
                    title = stringResource(R.string.pure_black_label),
                    icon = Icons.Rounded.DarkMode,
                    checked = isPureBlackEnabled,
                    onCheckedChange = { checked ->
                        viewModel.togglePureBlack(checked)
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsItemRow(
    title: String,
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
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
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
