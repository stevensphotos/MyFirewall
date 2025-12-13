package com.fw.myfirewall.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fw.myfirewall.R
import com.fw.myfirewall.service.MyVpnService

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("MyFirewallPrefs", Context.MODE_PRIVATE) }
    
    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = Intent(context, MyVpnService::class.java)
            intent.action = MyVpnService.ACTION_CONNECT
            context.startForegroundService(intent)
        }
    }
    
    val isRootEnabled = remember { mutableStateOf(false) }
    val isStartOnBootEnabled = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isStartOnBootEnabled.value = sharedPrefs.getBoolean("start_on_boot", false)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
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

        // Section Title
        Text(
            text = stringResource(R.string.Function_type_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // VPN Service Card
        SettingsItemCard(
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

        // Root Service Card
        SettingsItemCard(
            title = stringResource(R.string.root_service_label),
            icon = Icons.Rounded.AdminPanelSettings,
            checked = isRootEnabled.value,
            onCheckedChange = { checked ->
                isRootEnabled.value = checked
                // Logic for Root service can be added here
            }
        )
        
        // Start on Boot Card
        SettingsItemCard(
            title = stringResource(R.string.start_on_boot_label),
            icon = Icons.Rounded.PowerSettingsNew,
            checked = isStartOnBootEnabled.value,
            onCheckedChange = { checked ->
                isStartOnBootEnabled.value = checked
                sharedPrefs.edit().putBoolean("start_on_boot", checked).apply()
            }
        )
    }
}

@Composable
fun SettingsItemCard(
    title: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
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
}
