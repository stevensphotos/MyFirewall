package com.fw.myfirewall.ui

import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.fw.myfirewall.R
import com.fw.myfirewall.data.AppDatabase
import com.fw.myfirewall.data.BlockListConfig
import com.fw.myfirewall.data.BlockListImporter
import com.fw.myfirewall.data.BlockRuleCategory
import com.fw.myfirewall.worker.BlockListUpdateWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@Composable
fun RulesScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("MyFirewallPrefs", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()
    
    val showDialog = remember { mutableStateOf(false) }
    val selectedSchedule = remember { mutableStateOf("schedule_24h") }
    
    val isAdvertisingEnabled = remember { mutableStateOf(false) }
    val isAnnoyancesEnabled = remember { mutableStateOf(false) }
    val isSocialEnabled = remember { mutableStateOf(false) }
    val isCustomEnabled = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        selectedSchedule.value = sharedPrefs.getString("ad_blocker_schedule", "schedule_24h") ?: "schedule_24h"
        isAdvertisingEnabled.value = sharedPrefs.getBoolean("advertising_enabled", false)
        isAnnoyancesEnabled.value = sharedPrefs.getBoolean("annoyances_enabled", false)
        isSocialEnabled.value = sharedPrefs.getBoolean("social_enabled", false)
        isCustomEnabled.value = sharedPrefs.getBoolean("custom_enabled", false)
    }

    if (showDialog.value) {
        ScheduleDialog(
            currentSelection = selectedSchedule.value,
            onDismissRequest = { showDialog.value = false },
            onConfirmation = { selection ->
                selectedSchedule.value = selection
                sharedPrefs.edit().putString("ad_blocker_schedule", selection).apply()
                showDialog.value = false
                scheduleWorker(context, selection)
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
        // Title
        Text(
            text = stringResource(R.string.rules_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Main Card
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
                // Section Title
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.advertising_label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // Advertising Row
                RulesItemRow(
                    title = stringResource(R.string.advertising_label),
                    icon = Icons.Default.Block,
                    checked = isAdvertisingEnabled.value,
                    onCheckedChange = { checked ->
                        isAdvertisingEnabled.value = checked
                        sharedPrefs.edit().putBoolean("advertising_enabled", checked).apply()
                        
                        if (checked) {
                            scheduleWorker(context, selectedSchedule.value) // Start/Reset timer
                            coroutineScope.launch {
                                val count = withContext(Dispatchers.IO) {
                                    val db = AppDatabase.getDatabase(context)
                                    db.blockRuleDao().countByCategory(BlockRuleCategory.ADVERTISING).first()
                                }
                                
                                if (count == 0) {
                                    Toast.makeText(context, "Downloading block list...", Toast.LENGTH_SHORT).show()
                                    val imported = BlockListImporter.importFromUrl(context, BlockListConfig.URL_ADVERTISING, BlockRuleCategory.ADVERTISING)
                                    if (imported > 0) {
                                        Toast.makeText(context, "Imported $imported rules", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to import rules", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                )
                
                RulesDivider()

                // Annoyances Row
                RulesItemRow(
                    title = stringResource(R.string.annoyances_label),
                    icon = Icons.Default.VisibilityOff,
                    checked = isAnnoyancesEnabled.value,
                    onCheckedChange = { checked ->
                        isAnnoyancesEnabled.value = checked
                        sharedPrefs.edit().putBoolean("annoyances_enabled", checked).apply()
                        
                        if (checked) {
                            scheduleWorker(context, selectedSchedule.value) // Start/Reset timer
                            coroutineScope.launch {
                                val count = withContext(Dispatchers.IO) {
                                    val db = AppDatabase.getDatabase(context)
                                    db.blockRuleDao().countByCategory(BlockRuleCategory.ANNOYANCES).first()
                                }
                                
                                if (count == 0) {
                                    Toast.makeText(context, "Downloading block list...", Toast.LENGTH_SHORT).show()
                                    val imported = BlockListImporter.importFromUrl(context, BlockListConfig.URL_ANNOYANCES, BlockRuleCategory.ANNOYANCES)
                                    if (imported > 0) {
                                        Toast.makeText(context, "Imported $imported rules", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to import rules", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                )

                RulesDivider()

                // Social Row
                RulesItemRow(
                    title = stringResource(R.string.social_label),
                    icon = Icons.Default.Group,
                    checked = isSocialEnabled.value,
                    onCheckedChange = { checked ->
                        isSocialEnabled.value = checked
                        sharedPrefs.edit().putBoolean("social_enabled", checked).apply()
                        
                        if (checked) {
                            scheduleWorker(context, selectedSchedule.value) // Start/Reset timer
                            coroutineScope.launch {
                                val count = withContext(Dispatchers.IO) {
                                    val db = AppDatabase.getDatabase(context)
                                    db.blockRuleDao().countByCategory(BlockRuleCategory.SOCIAL_MEDIA).first()
                                }
                                
                                if (count == 0) {
                                    Toast.makeText(context, "Downloading block list...", Toast.LENGTH_SHORT).show()
                                    val imported = BlockListImporter.importFromUrl(context, BlockListConfig.URL_SOCIAL, BlockRuleCategory.SOCIAL_MEDIA)
                                    if (imported > 0) {
                                        Toast.makeText(context, "Imported $imported rules", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to import rules", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                )

                RulesDivider()

                // Custom Row
                RulesItemRow(
                    title = stringResource(R.string.custom_label),
                    icon = Icons.Default.Edit,
                    checked = isCustomEnabled.value,
                    onCheckedChange = { checked ->
                        isCustomEnabled.value = checked
                        sharedPrefs.edit().putBoolean("custom_enabled", checked).apply()
                    }
                )
                
                RulesDivider()

                // Update Schedule Row (opening dialog)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDialog.value = true }
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
                                    imageVector = Icons.Default.Block, 
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.update_schedule_label),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = getScheduleLabel(selectedSchedule.value),
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
            }
        }
    }
}

private fun scheduleWorker(context: Context, selection: String) {
    val workManager = WorkManager.getInstance(context)
    val interval = when(selection) {
        "schedule_12h" -> 12L to TimeUnit.HOURS
        "schedule_24h" -> 24L to TimeUnit.HOURS
        "schedule_weekly" -> 7L to TimeUnit.DAYS
        "schedule_monthly" -> 30L to TimeUnit.DAYS
        else -> 24L to TimeUnit.HOURS
    }
    
    val updateRequest = PeriodicWorkRequestBuilder<BlockListUpdateWorker>(
        interval.first, interval.second
    ).build()
    
    workManager.enqueueUniquePeriodicWork(
        "blocklist_update",
        ExistingPeriodicWorkPolicy.UPDATE,
        updateRequest
    )
}

@Composable
fun RulesItemRow(
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
fun RulesDivider() {
    HorizontalDivider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        thickness = 4.dp,
        color = MaterialTheme.colorScheme.background
    )
}

@Composable
fun ScheduleDialog(
    currentSelection: String,
    onDismissRequest: () -> Unit,
    onConfirmation: (String) -> Unit
) {
    val options = listOf(
        "schedule_12h" to R.string.schedule_12h,
        "schedule_24h" to R.string.schedule_24h,
        "schedule_weekly" to R.string.schedule_weekly,
        "schedule_monthly" to R.string.schedule_monthly
    )
    
    AlertDialog(
        onDismissRequest = { onDismissRequest() },
        title = { Text(text = stringResource(R.string.schedule_dialog_title)) },
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
fun getScheduleLabel(key: String): String {
    return when(key) {
        "schedule_12h" -> stringResource(R.string.schedule_12h)
        "schedule_24h" -> stringResource(R.string.schedule_24h)
        "schedule_weekly" -> stringResource(R.string.schedule_weekly)
        "schedule_monthly" -> stringResource(R.string.schedule_monthly)
        else -> stringResource(R.string.schedule_24h)
    }
}
