package com.fw.myfirewall

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fw.myfirewall.data.AppDatabase
import com.fw.myfirewall.data.AppRule
import com.fw.myfirewall.model.AppInfo
import com.fw.myfirewall.ui.AppDestinations
import com.fw.myfirewall.ui.AppFilter
import com.fw.myfirewall.ui.AppListScreen
import com.fw.myfirewall.ui.RulesScreen
import com.fw.myfirewall.ui.SettingsScreen
import com.fw.myfirewall.ui.StatusScreen
import com.fw.myfirewall.ui.theme.MyFirewallTheme
import com.fw.myfirewall.viewmodel.SettingsViewModel
import com.fw.myfirewall.viewmodel.StatusViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val isPureBlackEnabled by settingsViewModel.isPureBlackEnabled.collectAsState()
            
            MyFirewallTheme(pureBlack = isPureBlackEnabled) {
                MyFirewallApp()
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun MyFirewallApp() {
    val pagerState = rememberPagerState(pageCount = { AppDestinations.entries.size })
    val coroutineScope = rememberCoroutineScope()
    var selectedFilters by rememberSaveable { mutableStateOf(setOf(AppFilter.ALL)) }

    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val dao = remember { database.appRuleDao() }
    
    val statusViewModel: StatusViewModel = viewModel()

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
                    val appInfo = packageInfo.applicationInfo
                    appInfo?.let {
                        val label = pm.getApplicationLabel(it).toString()
                        val icon = pm.getApplicationIcon(it).toBitmap().asImageBitmap()
                        val isSystem = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        val hasInternet = packageInfo.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
                        
                        val savedRule = dao.getRule(packageInfo.packageName)
                        val wifiAllowed = savedRule?.wifiAllowed ?: true
                        val mobileAllowed = savedRule?.mobileAllowed ?: true
                        val roamingAllowed = savedRule?.roamingAllowed ?: true

                        AppInfo(label, packageInfo.packageName, icon, isSystem, hasInternet, wifiAllowed, mobileAllowed, roamingAllowed)
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


    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEachIndexed { index, destination ->
                item(
                    icon = {
                        Icon(
                            destination.icon,
                            contentDescription = destination.label
                        )
                    },
                    label = { Text(destination.label) },
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
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (AppDestinations.entries[page]) {
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
                                    
                                    selectedFilters = if (current.isEmpty()) setOf(AppFilter.ALL) else current
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
                                    
                                    selectedFilters = if (current.isEmpty()) setOf(AppFilter.ALL) else current
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
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    AppDestinations.SETTINGS -> {
                        SettingsScreen(
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
