package com.fw.myfirewall

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fw.myfirewall.data.AppDatabase
import com.fw.myfirewall.data.AppRule
import com.fw.myfirewall.data.UserPreferences
import com.fw.myfirewall.model.AppInfo
import com.fw.myfirewall.ui.*
import com.fw.myfirewall.ui.theme.MyFirewallTheme
import com.fw.myfirewall.viewmodel.SettingsViewModel
import com.fw.myfirewall.viewmodel.StatusViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.fw.myfirewall.ui.OnboardingScreen
import com.fw.myfirewall.ui.InfoPage


// ############################################################################
// ACTIVITY ENTRY POINT
// ############################################################################
class MainActivity : androidx.fragment.app.FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Handle the splash screen transition
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialisation des préférences
        val userPreferences = UserPreferences(applicationContext)

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val isAppLockEnabled by settingsViewModel.isAppLockEnabled.collectAsState()
            val isPureBlackEnabled by settingsViewModel.isPureBlackEnabled.collectAsState()
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val isDynamicColorEnabled by settingsViewModel.isDynamicColorEnabled.collectAsState()

            // États de sécurité
            var isAppUnlocked by rememberSaveable { mutableStateOf(false) }
            var lastActiveTime by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
            val lockTimeout = 30000L // 30 secondes

            val isFirstLaunch by userPreferences.isFirstLaunch.collectAsState(initial = null)
            val scope = rememberCoroutineScope()

            // SplashScreen actif tant que l'état de premier lancement est inconnu
            splashScreen.setKeepOnScreenCondition { isFirstLaunch == null }

            // Gestion du verrouillage lors de la mise en arrière-plan
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        val elapsed = System.currentTimeMillis() - lastActiveTime
                        if (isAppLockEnabled && elapsed > lockTimeout && isFirstLaunch == false) {
                            isAppUnlocked = false
                        }
                    } else if (event == Lifecycle.Event.ON_PAUSE) {
                        lastActiveTime = System.currentTimeMillis()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

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
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // LOGIQUE DE SÉCURITÉ : Interception si le verrouillage est actif
                    // On ne verrouille pas si c'est le premier lancement pour ne pas bloquer l'onboarding
                    val needsAuthentication = isAppLockEnabled && isFirstLaunch == false && !isAppUnlocked

                    if (needsAuthentication) {
                        LaunchedEffect(Unit) {
                            showBiometricPrompt(
                                activity = this@MainActivity,
                                onSuccess = { isAppUnlocked = true },
                                onError = { errorCode, _ ->
                                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                        finish()
                                    }
                                }
                            )
                        }
                        // Écran d'attente pendant l'authentification
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        // Navigation normale une fois authentifié ou si désactivé
                        when (isFirstLaunch) {
                            null -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }

                            true -> {
                                var showOnboardingInfo by rememberSaveable { mutableStateOf(false) }
                                if (showOnboardingInfo) {
                                    InfoPage(
                                        onBack = { showOnboardingInfo = false },
                                        onClose = {
                                            showOnboardingInfo = false
                                            scope.launch { userPreferences.completeOnboarding() }
                                        }
                                    )
                                } else {
                                    OnboardingScreen(
                                        onOnboardingFinished = {
                                            scope.launch { userPreferences.completeOnboarding() }
                                            // On déverrouille automatiquement après le premier onboarding
                                            isAppUnlocked = true
                                        },
                                        onInfoClick = { showOnboardingInfo = true }
                                    )
                                }
                            }

                            false -> {
                                MyFirewallApp(settingsViewModel)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Affiche l'invite biométrique du système (Empreinte, Visage ou PIN/Schéma)
     */
    private fun showBiometricPrompt(
        activity: androidx.fragment.app.FragmentActivity,
        onSuccess: () -> Unit,
        onError: (Int, CharSequence) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errorCode, errString)
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_title))
            .setSubtitle(getString(R.string.biometric_subtitle))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    @PreviewScreenSizes
    @Composable
    fun MyFirewallApp(settingsViewModel: SettingsViewModel) {
        // ############################################################################
        // CORE APP STATE & VIEWMODEL INITIALIZATION
        // ############################################################################
        val pagerDestinations = remember {
            AppDestinations.entries.filter { it != AppDestinations.SAVE_RULES && it != AppDestinations.CUSTOM_RULES }
        }
        val pagerState = rememberPagerState(pageCount = { pagerDestinations.size })

        // State to handle screen overlays
        var showSaveRulesScreen by rememberSaveable { mutableStateOf(false) }
        var showCustomRulesScreen by rememberSaveable { mutableStateOf(false) }

        @Suppress("AssignedValueIsNeverRead")
        var showOnboardingOverlay by rememberSaveable { mutableStateOf(false) }

        @Suppress("AssignedValueIsNeverRead")
        var showInfoOverlay by rememberSaveable { mutableStateOf(false) }

        val coroutineScope = rememberCoroutineScope()
        var selectedFilters by rememberSaveable {
            mutableStateOf(setOf(AppFilter.USER, AppFilter.INTERNET))
        }

        val context = LocalContext.current
        val database = remember { AppDatabase.getDatabase(context) }
        val dao = remember { database.appRuleDao() }

        val statusViewModel: StatusViewModel = viewModel()

        // ############################################################################
        // APPLICATION LIST DATA HANDLING
        // ############################################################################
        var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }
        var dataLoaded by rememberSaveable { mutableStateOf(false) }

        // Function to load apps
        val loadApps = suspend {
            withContext(Dispatchers.IO) {
                isLoading = true
                val pm = context.packageManager
                val installedApps = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                    .mapNotNull { packageInfo ->
                        val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
                        val label = pm.getApplicationLabel(appInfo).toString()
                        val icon = pm.getApplicationIcon(appInfo).toBitmap().asImageBitmap()
                        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        val hasInternet = packageInfo.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
                        val savedRule = dao.getRule(packageInfo.packageName)

                        AppInfo(
                            name = label,
                            label = label,
                            packageName = packageInfo.packageName,
                            icon = icon,
                            isSystem = isSystem,
                            hasInternet = hasInternet,
                            wifiAllowed = savedRule?.wifiAllowed ?: true,
                            mobileAllowed = savedRule?.mobileAllowed ?: true,
                            roamingAllowed = savedRule?.roamingAllowed ?: true
                        )
                    }.sortedBy { it.name }

                withContext(Dispatchers.Main) {
                    apps = installedApps
                    isLoading = false
                    dataLoaded = true
                }
            }
        }

        LaunchedEffect(Unit) {
            if (!dataLoaded || apps.isEmpty()) {
                loadApps()
            }
        }

        // Function to update DB
        fun updateAppRule(app: AppInfo) {
            coroutineScope.launch(Dispatchers.IO) {
                dao.insertRule(
                    AppRule(
                        packageName = app.packageName,
                        wifiAllowed = app.wifiAllowed,
                        mobileAllowed = app.mobileAllowed,
                        roamingAllowed = app.roamingAllowed
                    )
                )
            }
        }

        // ############################################################################
        // MAIN UI STRUCTURE
        // ############################################################################
        if (showSaveRulesScreen) {
            // Overlay for SaveRulesScreen
            BackHandler { showSaveRulesScreen = false }
            SaveRulesScreen(onBack = { showSaveRulesScreen = false })
        } else if (showCustomRulesScreen) {
            // Overlay for CustomRulesScreen
            BackHandler { showCustomRulesScreen = false }
            CustomRulesScreen(
                // Correction : Ajout du paramètre manquant pour la navigation
                onNavigateToSaveRules = {
                    showCustomRulesScreen = false
                    showSaveRulesScreen = true
                },
                onBack = { showCustomRulesScreen = false }
            )

        } else if (showInfoOverlay) {
            // Overlay for InfoPage
            InfoPage(
                onBack = { showInfoOverlay = false },
                onClose = {
                    showInfoOverlay = false
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerDestinations.indexOf(AppDestinations.STATUS))
                    }
                }
            )
        } else if (showOnboardingOverlay) {
            // Overlay for Onboarding (Help mode)
            BackHandler { showOnboardingOverlay = false }
            OnboardingScreen(
                onOnboardingFinished = { showOnboardingOverlay = false },
                onInfoClick = { showInfoOverlay = true }
            )
        } else {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = currentDensity.density,
                    fontScale = currentDensity.fontScale.coerceAtMost(1.2f)
                )
            ) {
                NavigationSuiteScaffold(
                    navigationSuiteItems = {
                        pagerDestinations.forEachIndexed { index, destination ->
                            item(
                                icon = { Icon(destination.icon, contentDescription = destination.name) },
                                label = {
                                    Text(
                                        text = stringResource(id = destination.labelRes),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                selected = index == pagerState.currentPage,
                                onClick = {
                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                }
                            )
                        }
                    }
                ) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        val isSwipeEnabled = true

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = true
                        ) { page ->
                            when (pagerDestinations[page]) {
                                AppDestinations.STATUS -> {
                                    StatusScreen(
                                        modifier = Modifier.padding(innerPadding),
                                        totalApps = apps.size,
                                        blockedApps = apps.count { !it.wifiAllowed && !it.mobileAllowed },
                                        viewModel = statusViewModel
                                    )
                                }

                                AppDestinations.APPLICATIONS -> {
                                    AppListScreen(
                                        modifier = Modifier.padding(innerPadding),
                                        selectedFilters = selectedFilters,
                                        onFilterClick = { filter ->
                                            selectedFilters = if (selectedFilters.contains(filter)) selectedFilters - filter else selectedFilters + filter
                                        },
                                        onFilterLongClick = { /* Logic */ },
                                        apps = apps,
                                        isLoading = isLoading,
                                        onAppsChange = { apps = it },
                                        onAppUpdated = { updatedApp ->
                                            updateAppRule(updatedApp)
                                            apps = apps.map { if (it.packageName == updatedApp.packageName) updatedApp else it }
                                        },
                                        onAppsUpdated = { updatedList ->
                                            updatedList.forEach { updateAppRule(it) }
                                            apps = updatedList
                                        },
                                        onRefresh = { coroutineScope.launch { loadApps() } }
                                    )
                                }

                                AppDestinations.RULES -> {
                                    RulesScreen(
                                        modifier = Modifier.padding(innerPadding),
                                        onNavigateToSaveRules = { showSaveRulesScreen = true },
                                        onNavigateToCustomRules = { showCustomRulesScreen = true }
                                    )
                                }

                                AppDestinations.SETTINGS -> {
                                    SettingsScreen(
                                        modifier = Modifier.padding(innerPadding),
                                        viewModel = settingsViewModel,
                                        onNavigateToHelp = { showOnboardingOverlay = true }
                                    )
                                }

                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}
