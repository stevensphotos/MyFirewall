package com.fw.myfirewall.ui

import android.Manifest
import androidx.compose.ui.tooling.preview.Preview
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fw.myfirewall.R
import com.fw.myfirewall.ui.theme.MyFirewallTheme
import com.fw.myfirewall.viewmodel.SettingsViewModel
import com.fw.myfirewall.viewmodel.openUsageStatsSettings
import androidx.compose.material3.ButtonDefaults

private const val THEME_LIGHT = "light"
private const val THEME_DARK = "dark"

@Composable
fun OnboardingScreen(
    onOnboardingFinished: () -> Unit,
    onInfoClick: () -> Unit = {}
) {
    val settingsViewModel: SettingsViewModel = viewModel()
    val isPureBlackEnabled by settingsViewModel.isPureBlackEnabled.collectAsState()
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val isDynamicColorEnabled by settingsViewModel.isDynamicColorEnabled.collectAsState()

    val darkTheme = when (themeMode) {
        THEME_LIGHT -> false
        THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }

    MyFirewallTheme(
        darkTheme = darkTheme,
        pureBlack = isPureBlackEnabled,
        dynamicColor = isDynamicColorEnabled
    ) {
        OnboardingContent(
            onOnboardingFinished = onOnboardingFinished,
            onInfoClick = onInfoClick,
            viewModel = settingsViewModel
        )
    }
}


// ############################################################################
// DIALOG: QUICKSTART GUIDE
// ############################################################################
@Composable
fun QuickStartDialog(
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                Icons.Default.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.info_quick_start_title),
                style = MaterialTheme.typography.headlineMedium
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                QuickStartStep(
                    number = "1",
                    title = stringResource(R.string.step_permissions_title),
                    description = stringResource(R.string.step_permissions_desc)
                )


                QuickStartStep(
                    number = "2",
                    title = stringResource(R.string.step_vpn_title),
                    description = stringResource(R.string.step_vpn_desc)
                )
                QuickStartStep(
                    number = "3",
                    title = stringResource(R.string.step_rules_title),
                    description = stringResource(R.string.step_rules_desc)
                )

                // Ajoutez d'autres étapes ici...
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.btn_got_it))
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
private fun QuickStartStep(number: String, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionGrantedRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun OnboardingContent(
    onOnboardingFinished: () -> Unit,
    onInfoClick: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    BackHandler(onBack = onOnboardingFinished)

    val isUsageStatsGranted by viewModel.isUsageStatsPermissionGranted.collectAsState()
    val hasNotificationPermission by viewModel.hasNotificationPermission.collectAsState()
    val isRootEnabled by viewModel.isRootEnabled.collectAsState()
    val isRootPossible by viewModel.isRootPossible.collectAsState()
    val rootCheckResult by viewModel.rootCheckResult.collectAsState()

    val rootGrantedMsg = stringResource(R.string.root_access_granted)
    val rootDeniedMsg = stringResource(R.string.root_access_not_available)

    LaunchedEffect(rootCheckResult) {
        rootCheckResult?.let { hasRoot ->
            val message = if (hasRoot) rootGrantedMsg else rootDeniedMsg
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.resetRootCheckResult()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkInitialPermissions(context)
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> viewModel.updateNotificationPermission(granted) }
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // ############################################################################
            // HEADER SECTION: Icon + Title
            // ############################################################################
            OutlinedCard(
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = ButtonDefaults.outlinedButtonBorder,
                shape = RoundedCornerShape(36.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp),
                        shadowElevation = 2.dp
                    ) {
                        Image(
                            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = stringResource(R.string.welcome_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Left
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ############################################################################
            // DISCLAIMER SECTION
            // ############################################################################
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(25.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_disclaimer),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Justify,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            val showGuideDialog = remember { mutableStateOf(false) }

            if (showGuideDialog.value) {
                QuickStartDialog(onDismissRequest = { showGuideDialog.value = false })
            }



            // ############################################################################
            // QUICK START GUIDE: Section informative dans une carte
            // ############################################################################
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(
                        alpha = 0.4f
                    )
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showGuideDialog.value = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.onboarding_guide_label),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.onboarding_guide_desc),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            } // Fin de la Card

            Spacer(modifier = Modifier.height(24.dp))


                                Spacer(modifier = Modifier.height(24.dp))


                                // ############################################################################
                                // PERMISSIONS SECTION
                                // ############################################################################
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        if (!hasNotificationPermission) {
                                            Column {
                                                Card(
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(
                                                            alpha = 0.5f
                                                        )
                                                    ),
                                                    shape = RoundedCornerShape(25.dp),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(bottom = 8.dp)
                                                ) {
                                                    Column(modifier = Modifier.padding(16.dp)) {
                                                        // Titre inséré à l'intérieur de la carte pour la cohérence visuelle
                                                        Text(
                                                            text = stringResource(R.string.perm_notification_title),
                                                            style = MaterialTheme.typography.titleMedium,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.fillMaxWidth(),
                                                            textAlign = TextAlign.Center
                                                        )

                                                        Spacer(modifier = Modifier.height(8.dp))

                                                        Text(
                                                            text = stringResource(R.string.onboarding_perm_vpn_desc),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            textAlign = TextAlign.Justify,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                    }
                                                }
                                                OutlinedButton(
                                                    onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(stringResource(R.string.permission_notification))
                                                }
                                            }
                                        } else {
                                            PermissionGrantedRow(text = stringResource(R.string.permission_notification))
                                        }
                                    }

                                    // --- USAGE STATS PERMISSION ---
                                    if (!isUsageStatsGranted) {
                                        Column {
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(
                                                        alpha = 0.5f
                                                    )
                                                ),
                                                shape = RoundedCornerShape(25.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 8.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(16.dp)) {
                                                    Text(
                                                        text = stringResource(R.string.perm_usage_stats_title),
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.fillMaxWidth(),
                                                        textAlign = TextAlign.Center
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = stringResource(R.string.permission_usage_stats_description),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        textAlign = TextAlign.Justify,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                }
                                            }
                                            OutlinedButton(
                                                onClick = { openUsageStatsSettings(context) },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(stringResource(R.string.permission_usage_stats))
                                            }
                                        }
                                    } else {
                                        PermissionGrantedRow(text = stringResource(R.string.permission_usage_stats))
                                    }

                                    // --- ROOT PERMISSION ---
                                    if (isRootPossible && !isRootEnabled) {
                                        Column {
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(
                                                        alpha = 0.5f
                                                    )
                                                ),
                                                shape = RoundedCornerShape(25.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 8.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(16.dp)) {
                                                    Text(
                                                        text = stringResource(R.string.perm_root_title),
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.fillMaxWidth(),
                                                        textAlign = TextAlign.Center
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = stringResource(R.string.root_disclaimer_onboarding),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        textAlign = TextAlign.Justify,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                }
                                            }
                                            OutlinedButton(
                                                onClick = { viewModel.toggleRoot(enabled = true) },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(stringResource(R.string.permission_root))
                                            }
                                        }
                                    } else if (isRootEnabled) {
                                        PermissionGrantedRow(text = stringResource(R.string.root_access_granted))
                                    }
                                }

                                // 4. FOOTER SECTION
                                // Note: Weight(1f) is used to push buttons to the bottom
                                Spacer(modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.height(32.dp))

                                OutlinedButton(
                                    onClick = onInfoClick,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    enabled = true,
                                    shape = RoundedCornerShape(28.dp),
                                    border = ButtonDefaults.outlinedButtonBorder(enabled = true),

                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.information_title))
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = onOnboardingFinished,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    shape = RoundedCornerShape(28.dp)
                                ) {
                                    Text(stringResource(R.string.onboarding_done))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                                }
                            }
                        }
                    }
