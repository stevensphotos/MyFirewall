package com.fw.myfirewall.ui

import android.content.Context
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
import androidx.compose.runtime.Composable
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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.fw.myfirewall.R
import com.fw.myfirewall.viewmodel.RulesViewModel
import com.fw.myfirewall.worker.BlockListUpdateWorker
import java.util.concurrent.TimeUnit

@Composable
fun RulesScreen(
    modifier: Modifier = Modifier, 
    viewModel: RulesViewModel = viewModel(),
    onNavigateToSaveRules: () -> Unit = {},
    onNavigateToCustomRules: () -> Unit = {}
) {
    val context = LocalContext.current
    
    val showDialog = remember { mutableStateOf(false) }
    
    val isAdvertisingEnabled by viewModel.isAdvertisingEnabled.collectAsState()
    val isAnnoyancesEnabled by viewModel.isAnnoyancesEnabled.collectAsState()
    val isSocialEnabled by viewModel.isSocialEnabled.collectAsState()
    val isCustomEnabled by viewModel.isCustomEnabled.collectAsState()
    val selectedSchedule by viewModel.selectedSchedule.collectAsState()

    if (showDialog.value) {
        ScheduleDialog(
            currentSelection = selectedSchedule,
            onDismissRequest = { showDialog.value = false },
            onConfirmation = { selection ->
                viewModel.updateSchedule(selection)
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
        // ############################################################################
        // TITLE SECTION
        // ############################################################################
        Text(
            text = stringResource(R.string.rules_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp) // Aligne le texte avec le bord des cartes
                .padding(top = 8.dp, bottom = 16.dp)
        )

        // ############################################################################
        // MAIN SETTINGS CARD
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
                // SUBTITLE SECTION (BLOCK LISTS)
                // ############################################################################
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.advertising_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // ############################################################################
                // ADVERTISING RULE ROW
                // ############################################################################
                RulesItemRow(
                    title = stringResource(R.string.advertising_label),
                    icon = Icons.Default.Block,
                    checked = isAdvertisingEnabled,
                    onCheckedChange = { checked ->
                        viewModel.toggleAdvertising(checked)
                        if (checked) {
                            scheduleWorker(context, selectedSchedule)
                        }
                    }
                )
                
                RulesDivider()

                // ############################################################################
                // ANNOYANCES RULE ROW
                // ############################################################################
                RulesItemRow(
                    title = stringResource(R.string.annoyances_label),
                    icon = Icons.Default.VisibilityOff,
                    checked = isAnnoyancesEnabled,
                    onCheckedChange = { checked ->
                        viewModel.toggleAnnoyances(checked)
                        if (checked) {
                            scheduleWorker(context, selectedSchedule)
                        }
                    }
                )

                RulesDivider()

                // ############################################################################
                // SOCIAL MEDIA RULE ROW
                // ############################################################################
                RulesItemRow(
                    title = stringResource(R.string.social_label),
                    icon = Icons.Default.Group,
                    checked = isSocialEnabled,
                    onCheckedChange = { checked ->
                        viewModel.toggleSocial(checked)
                        if (checked) {
                            scheduleWorker(context, selectedSchedule)
                        }
                    }
                )

                RulesDivider()

                // ############################################################################
                // CUSTOM RULES ROW
                // ############################################################################
                RulesItemRow(
                    title = stringResource(R.string.custom_label),
                    icon = Icons.Default.Edit,
                    checked = isCustomEnabled,
                    onCheckedChange = { checked ->
                        viewModel.toggleCustom(checked)
                    }
                )
                
                RulesDivider()

                // ############################################################################
                // UPDATE SCHEDULE ROW (OPENS DIALOG)
                // ############################################################################
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
                                text = getScheduleLabel(selectedSchedule),
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
        
        // ############################################################################
        // RULES MANAGEMENT CARD (Migrated UI)
        // ############################################################################
        RulesManagementCard(
            onNavigateToSaveRules = onNavigateToSaveRules,
            onNavigateToCustomRules = onNavigateToCustomRules
        )


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
