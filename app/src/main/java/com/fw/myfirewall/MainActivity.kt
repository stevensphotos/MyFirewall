package com.fw.myfirewall

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.core.graphics.drawable.toBitmap
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fw.myfirewall.data.AppDatabase
import com.fw.myfirewall.data.AppRule
import com.fw.myfirewall.model.AppInfo
import com.fw.myfirewall.ui.AppDestinations
import com.fw.myfirewall.ui.AppFilter
import com.fw.myfirewall.ui.AppListScreen
import com.fw.myfirewall.ui.RulesScreen
import com.fw.myfirewall.ui.SaveRulesScreen
import com.fw.myfirewall.ui.CustomRulesScreen
import com.fw.myfirewall.ui.SettingsScreen
import com.fw.myfirewall.ui.StatusScreen
import com.fw.myfirewall.ui.theme.MyFirewallTheme
import com.fw.myfirewall.viewmodel.SettingsViewModel
import com.fw.myfirewall.viewmodel.StatusViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.fw.myfirewall.data.UserPreferences
import com.fw.myfirewall.ui.OnboardingScreen
import com.fw.myfirewall.ui.InfoPage


// ############################################################################
// ACTIVITY ENTRY POINT
// ############################################################################
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Handle the splash screen transition
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialisation des préférences
        val userPreferences = UserPreferences(applicationContext)

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val isPureBlackEnabled by settingsViewModel.isPureBlackEnabled.collectAsState()
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val isDynamicColorEnabled by settingsViewModel.isDynamicColorEnabled.collectAsState()

            // 1. État du premier lancement
            val isFirstLaunch by userPreferences.isFirstLaunch.collectAsState(initial = null)
            val scope = rememberCoroutineScope()

            // SplashScreen actif tant que l'état est inconnu
            splashScreen.setKeepOnScreenCondition { isFirstLaunch == null }

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
                    when (isFirstLaunch) {
                        null -> {
                            // État transitoire géré par SplashScreen
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        true -> {
                            // ########################################################
                            // SCÉNARIO PREMIER LANCEMENT
                            // ########################################################
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
                                    },
                                    onInfoClick = { showOnboardingInfo = true }
                                )
                            }
                        }
                        false -> {
                            // ########################################################
                            // SCÉNARIO UTILISATEUR EXISTANT
                            // ########################################################
                            MyFirewallApp()
                        }
                    }
                }
            }
        }
    }


    @PreviewScreenSizes
    @Composable
    fun MyFirewallApp() {
        // ############################################################################
        // CORE APP STATE & VIEWMODEL INITIALIZATION
        // ############################################################################
        val pagerDestinations =
            remember { AppDestinations.entries.filter { it != AppDestinations.SAVE_RULES && it != AppDestinations.CUSTOM_RULES } }
        val pagerState = rememberPagerState(pageCount = { pagerDestinations.size })

        // State to handle screen overlays
        var showSaveRulesScreen by rememberSaveable { mutableStateOf(false) }
        var showCustomRulesScreen by rememberSaveable { mutableStateOf(false) }
        var showOnboardingOverlay by rememberSaveable { mutableStateOf(false) }
        var showInfoOverlay by rememberSaveable { mutableStateOf(false) }

        val coroutineScope = rememberCoroutineScope()
        var selectedFilters by rememberSaveable {
            mutableStateOf(
                setOf(
                    AppFilter.USER,
                    AppFilter.INTERNET
                )
            )
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
                val installedApps =
                    pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                        .mapNotNull { packageInfo ->
                            val appInfo = packageInfo.applicationInfo
                            appInfo?.let {
                                val label = pm.getApplicationLabel(it).toString()
                                val icon =
                                    pm.getApplicationIcon(it).toBitmap().asImageBitmap()
                                val isSystem =
                                    (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                                val hasInternet =
                                    packageInfo.requestedPermissions?.contains(Manifest.permission.INTERNET) == true

                                val savedRule = dao.getRule(packageInfo.packageName)
                                val wifiAllowed = savedRule?.wifiAllowed ?: true
                                val mobileAllowed = savedRule?.mobileAllowed ?: true
                                val roamingAllowed = savedRule?.roamingAllowed ?: true

                                AppInfo(
                                    label,
                                    packageInfo.packageName,
                                    icon,
                                    isSystem,
                                    hasInternet,
                                    wifiAllowed,
                                    mobileAllowed,
                                    roamingAllowed
                                )
                            }
                        }
                        .sortedBy { it.name }
                apps = installedApps
                isLoading = false
                dataLoaded = true
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
            SaveRulesScreen(
                onBack = { showSaveRulesScreen = false }
            )
        } else if (showCustomRulesScreen) {
            // Overlay for CustomRulesScreen
            BackHandler { showCustomRulesScreen = false }
            CustomRulesScreen(
                // Correction : Ajout du paramètre manquant pour la navigation
                onNavigateToSaveRules = {
                    showCustomRulesScreen = false
                    showSaveRulesScreen = true
                },
                onBack = {
                    showCustomRulesScreen = false
                }
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
                onOnboardingFinished = {
                    showOnboardingOverlay = false
                },
                onInfoClick = {
                    showInfoOverlay = true
                }
            )
        } else {
            // Main App Content with Navigation Suite
            NavigationSuiteScaffold(
                navigationSuiteItems = {
                    // ############################################################################
                    // BOTTOM NAVIGATION BAR ITEMS
                    // ############################################################################
                    pagerDestinations.forEachIndexed { index, destination ->
                        item(
                            icon = {
                                Icon(
                                    destination.icon,
                                    contentDescription = destination.name
                                )
                            },
                            label = { Text(text = stringResource(id = destination.labelRes)) },
                            selected = index == pagerState.currentPage,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                        )
                    }
                }
            ) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // ############################################################################
                    // HORIZONTAL PAGER FOR SCREEN CONTENT
                    // ############################################################################
                    val currentPageDestination =
                        pagerDestinations[pagerState.currentPage]
                    val isSwipeEnabled = when (currentPageDestination) {
                        // Disable swipe on Settings page (to prevent overscroll or conflict)
//                    AppDestinations.SETTINGS -> false
                        else -> true
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = isSwipeEnabled
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
                                        if (filter == AppFilter.ALL) {
                                            selectedFilters = setOf(AppFilter.ALL)
                                        } else {
                                            val current = selectedFilters.toMutableSet()
                                            if (current.contains(AppFilter.ALL)) {
                                                current.remove(AppFilter.ALL)
                                            }

                                            if (current.contains(filter)) {
                                                current.remove(filter)
                                            } else {
                                                current.add(filter)
                                            }

                                            selectedFilters =
                                                if (current.isEmpty()) setOf(AppFilter.ALL) else current
                                        }
                                    },
                                    onFilterLongClick = { filter ->
                                        if (filter != AppFilter.ALL) {
                                            val current = selectedFilters.toMutableSet()
                                            if (current.contains(AppFilter.ALL)) {
                                                current.remove(AppFilter.ALL)
                                            }

                                            if (current.contains(filter)) {
                                                current.remove(filter)
                                            } else {
                                                current.add(filter)
                                            }

                                            selectedFilters =
                                                if (current.isEmpty()) setOf(AppFilter.ALL) else current
                                        } else {
                                            // Long click on ALL resets to just ALL
                                            selectedFilters = setOf(AppFilter.ALL)
                                        }
                                    },
                                    apps = apps,
                                    isLoading = isLoading,
                                    onAppsChange = { newApps ->
                                        apps = newApps
                                    },
                                    onAppUpdated = { app ->
                                        updateAppRule(app)
                                    },
                                    onAppsUpdated = { updatedApps ->
                                        updatedApps.forEach { updateAppRule(it) }
                                    },
                                    onRefresh = {
                                        coroutineScope.launch {
                                            loadApps()
                                        }
                                    }
                                )
                            }

                            AppDestinations.RULES -> {
                                RulesScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    onNavigateToSaveRules = {
                                        showSaveRulesScreen = true
                                    },
                                    onNavigateToCustomRules = {
                                        showCustomRulesScreen = true
                                    }
                                )
                            }

                            AppDestinations.SETTINGS -> {
                                SettingsScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    onNavigateToHelp = {
                                        showOnboardingOverlay = true
                                    }
                                )
                            }
                            // Handled outside pager
                            AppDestinations.SAVE_RULES -> {}
                            AppDestinations.CUSTOM_RULES -> {}
                        }
                    }
                }
            }
        }
    }
}
