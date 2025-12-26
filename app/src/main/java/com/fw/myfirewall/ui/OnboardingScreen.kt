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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
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

    LaunchedEffect(rootCheckResult) {
        rootCheckResult?.let { hasRoot ->
            val message = if (hasRoot) R.string.root_access_granted else R.string.root_access_not_available
            Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show()
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

            // HEADER: Icône + Titre dans une carte style Outlined
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
                        textAlign = TextAlign.Start
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(25.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Titre de la carte pour faire ressortir le sujet
                    Text(
                        text = stringResource(R.string.onboarding_disclaimer_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.onboarding_disclaimer),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Justify,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(25.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.info_quick_start_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val steps = listOf(
                        R.string.info_step_1,
                        R.string.info_step_2,
                        R.string.info_step_3,
                        R.string.info_step_4
                    )

                    steps.forEach { stepRes ->
                        Text(
                            text = stringResource(stepRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                            textAlign = TextAlign.Start
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // 3. Permissions Section
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
                                    // Titre inséré à l'intérieur de la carte pour la cohérence
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
                }                }

            // Titre de section pour les permissions (Rigueur visuelle)
            Text(
                text = stringResource(R.string.onboarding_permissions_setup),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )

            if (!isUsageStatsGranted) {


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
                                // Titre inséré à l'intérieur de la carte pour la cohérence
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

                // ROOT PERMISSION
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
                                // Titre inséré à l'intérieur de la carte
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
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    textAlign = TextAlign.Justify
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

            Spacer(modifier = Modifier.height(32.dp))

            // 5. BOUTON INFORMATIONS
            OutlinedButton(
                onClick = onInfoClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = ButtonDefaults.outlinedShape
            ) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.information_title))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 6. BOUTON FINAL
            Button(
                onClick = onOnboardingFinished,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(stringResource(R.string.onboarding_done))
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.CheckCircle, contentDescription = null)
            }
        }
    }
}

@Preview(showBackground = true, name = "Onboarding - Light Mode")
@Composable
fun OnboardingPreviewLight() {
    MyFirewallTheme(darkTheme = false) {
        OnboardingContent(
            onOnboardingFinished = {},
            onInfoClick = {}
        )
    }
}
