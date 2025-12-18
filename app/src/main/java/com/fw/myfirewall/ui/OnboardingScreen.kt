package com.fw.myfirewall.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fw.myfirewall.R
import com.fw.myfirewall.ui.theme.MyFirewallTheme
import com.fw.myfirewall.viewmodel.SettingsViewModel
import com.fw.myfirewall.viewmodel.hasUsageStatsPermission
import com.fw.myfirewall.viewmodel.openUsageStatsSettings

@Composable
fun OnboardingScreen(
    onOnboardingFinished: () -> Unit
) {
    val settingsViewModel: SettingsViewModel = viewModel()
    val isPureBlackEnabled by settingsViewModel.isPureBlackEnabled.collectAsState()
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val isDynamicColorEnabled by settingsViewModel.isDynamicColorEnabled.collectAsState()

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
        OnboardingContent(onOnboardingFinished = onOnboardingFinished)
    }
}

@Composable
fun OnboardingContent(
    onOnboardingFinished: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    var isUsageStatsGranted by remember { mutableStateOf(hasUsageStatsPermission(context)) }

    // Update permissions on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isUsageStatsGranted = hasUsageStatsPermission(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    hasNotificationPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Lanceur pour la permission Notification (Android 13+)
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { hasNotificationPermission = it }
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        // UNE SEULE Column principale avec statusBarsPadding
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top // On commence en haut
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // 1. APPLICATION ICON
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(120.dp),
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier
                            .size(120.dp) // Taille cohérente (inférieure à 120)
                            .clip(CircleShape) // This clips the adaptive icon background into a circle
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Disclaimer
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_disclaimer),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 3. Boutons de Permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                OutlinedButton(
                    onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.permission_notification))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (!isUsageStatsGranted) {
                OutlinedButton(
                    onClick = { openUsageStatsSettings(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.permission_usage_stats))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Pousse le bouton vers le bas
            Spacer(modifier = Modifier.weight(1f))

            // 4. Bouton Final
            Button(
                onClick = onOnboardingFinished,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(stringResource(R.string.start_protection))
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.CheckCircle, contentDescription = null)
            }
        }
    }
}

// ############################################################################
// PREVIEWS
// ############################################################################
@Preview(showBackground = true, name = "Onboarding - Light Mode")
@Composable
fun OnboardingPreviewLight() {
    MyFirewallTheme(darkTheme = false) {
        // On utilise OnboardingContent pour éviter de gérer le ViewModel dans la Preview
        OnboardingContent(onOnboardingFinished = {})
    }
}

@Preview(showBackground = true, name = "Onboarding - Dark Mode")
@Composable
fun OnboardingPreviewDark() {
    MyFirewallTheme(darkTheme = true) {
        OnboardingContent(onOnboardingFinished = {})
    }
}

@Preview(showBackground = true, name = "Onboarding - Pure Black")
@Composable
fun OnboardingPreviewPureBlack() {
    MyFirewallTheme(darkTheme = true, pureBlack = true) {
        OnboardingContent(onOnboardingFinished = {})
    }
}