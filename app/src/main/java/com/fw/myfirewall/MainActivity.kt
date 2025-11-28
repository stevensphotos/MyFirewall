package com.fw.myfirewall

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PublicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SignalCellularOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.fw.myfirewall.data.AppDatabase
import com.fw.myfirewall.data.AppRule
import com.fw.myfirewall.ui.theme.MyFirewallTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyFirewallTheme {
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
                    AppDestinations.HOME -> {
                        HomeScreen(
                            modifier = Modifier.padding(innerPadding),
                            totalApps = apps.size,
                            blockedApps = apps.count { !it.wifiAllowed && !it.mobileAllowed }
                        )
                    }
                    AppDestinations.APPLICATIONS -> {
                        AppListScreen(
                            modifier = Modifier.padding(innerPadding),
                            selectedFilters = selectedFilters,
                            onFilterClick = { filter ->
                                selectedFilters = if (filter == AppFilter.ALL) {
                                    setOf(AppFilter.ALL)
                                } else {
                                    setOf(filter)
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
                    else -> {
                        Greeting(
                            name = AppDestinations.entries[page].label,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    totalApps: Int = 0,
    blockedApps: Int = 0
) {
    val allowedApps = totalApps - blockedApps

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Zone 1: Total Apps
        StatusCard(
            title = "Total Apps",
            count = totalApps.toString(),
            icon = Icons.Default.Apps,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.weight(1f)
        )

        // Zone 2: Allowed Apps
        StatusCard(
            title = "Allowed",
            count = allowedApps.toString(),
            icon = Icons.Default.CheckCircle,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.weight(1f)
        )

        // Zone 3: Blocked Apps
        StatusCard(
            title = "Blocked",
            count = blockedApps.toString(),
            icon = Icons.Default.Block,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun StatusCard(
    title: String,
    count: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(25.dp),
        color = color,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = count,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    modifier: Modifier = Modifier,
    selectedFilters: Set<AppFilter>,
    onFilterClick: (AppFilter) -> Unit,
    onFilterLongClick: (AppFilter) -> Unit,
    apps: List<AppInfo>,
    isLoading: Boolean,
    onAppsChange: (List<AppInfo>) -> Unit,
    onAppUpdated: (AppInfo) -> Unit,
    onAppsUpdated: (List<AppInfo>) -> Unit,
    onRefresh: () -> Unit
) {
    val filteredApps = remember(apps, selectedFilters) {
        if (selectedFilters.contains(AppFilter.ALL)) {
            apps
        } else {
            apps.filter { app ->
                selectedFilters.all { filter ->
                    when (filter) {
                        AppFilter.ALL -> true
                        AppFilter.SYSTEM -> app.isSystem
                        AppFilter.USER -> !app.isSystem
                        AppFilter.INTERNET -> app.hasInternet
                        AppFilter.NO_INTERNET -> !app.hasInternet
                    }
                }
            }
        }
    }

    val pullRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = onRefresh,
        state = pullRefreshState,
        modifier = modifier.fillMaxSize()
    ) {
        if (isLoading && apps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AppFilter.entries.forEach { filter ->
                        val isSelected = selectedFilters.contains(filter)
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                1.dp, 
                                if (isSelected) Color.Transparent 
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .combinedClickable(
                                    onClick = { onFilterClick(filter) },
                                    onLongClick = { onFilterLongClick(filter) }
                                )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = filter.icon,
                                    contentDescription = filter.name,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(25.dp))
                        .border(
                            width = 1.dp, 
                            color = MaterialTheme.colorScheme.outlineVariant, 
                            shape = RoundedCornerShape(25.dp)
                        )
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredApps) { app ->
                            AppItem(
                                app = app,
                                onWifiToggle = { 
                                    val index = apps.indexOfFirst { it.packageName == app.packageName }
                                    if (index != -1) {
                                        val newApp = apps[index].copy(wifiAllowed = !apps[index].wifiAllowed)
                                        val newApps = apps.toMutableList().apply { set(index, newApp) }
                                        onAppsChange(newApps)
                                        onAppUpdated(newApp)
                                    }
                                },
                                onMobileToggle = {
                                    val index = apps.indexOfFirst { it.packageName == app.packageName }
                                    if (index != -1) {
                                        val currentApp = apps[index]
                                        val newState = !currentApp.mobileAllowed
                                        val newApp = currentApp.copy(
                                            mobileAllowed = newState,
                                            roamingAllowed = newState 
                                        )
                                        val newApps = apps.toMutableList().apply { set(index, newApp) }
                                        onAppsChange(newApps)
                                        onAppUpdated(newApp)
                                    }
                                },
                                onRoamingToggle = {
                                    val index = apps.indexOfFirst { it.packageName == app.packageName }
                                    if (index != -1) {
                                        val newApp = apps[index].copy(roamingAllowed = !apps[index].roamingAllowed)
                                        val newApps = apps.toMutableList().apply { set(index, newApp) }
                                        onAppsChange(newApps)
                                        onAppUpdated(newApp)
                                    }
                                },
                                onToggleAll = {
                                    val index = apps.indexOfFirst { it.packageName == app.packageName }
                                    if (index != -1) {
                                        val currentApp = apps[index]
                                        val anyAllowed = currentApp.wifiAllowed || currentApp.mobileAllowed || currentApp.roamingAllowed
                                        val newState = !anyAllowed
                                        val newApp = currentApp.copy(
                                            wifiAllowed = newState,
                                            mobileAllowed = newState,
                                            roamingAllowed = newState
                                        )
                                        val newApps = apps.toMutableList().apply { set(index, newApp) }
                                        onAppsChange(newApps)
                                        onAppUpdated(newApp)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppItem(
    app: AppInfo,
    onWifiToggle: () -> Unit,
    onMobileToggle: () -> Unit,
    onRoamingToggle: () -> Unit,
    onToggleAll: () -> Unit
) {
    val context = LocalContext.current
    ListItem(
        modifier = Modifier.combinedClickable(
            onClick = {},
            onLongClick = onToggleAll
        ),
        headlineContent = { Text(app.name) },
        supportingContent = { 
            Text(
                text = app.packageName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Image(
                bitmap = app.icon,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", app.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    )
            )
        },
        trailingContent = {
            Row {
                Icon(
                    imageVector = if (app.wifiAllowed) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = "Wi-Fi",
                    tint = if (app.wifiAllowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clickable { onWifiToggle() }
                )
                Icon(
                    imageVector = if (app.mobileAllowed) Icons.Default.SignalCellularAlt else Icons.Default.SignalCellularOff,
                    contentDescription = "Mobile Data",
                    tint = if (app.mobileAllowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clickable { onMobileToggle() }
                )
                Icon(
                    imageVector = if (app.roamingAllowed) Icons.Default.Public else Icons.Default.PublicOff,
                    contentDescription = "Roaming",
                    tint = if (app.roamingAllowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clickable { onRoamingToggle() }
                )
            }
        }
    )
}

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: ImageBitmap,
    val isSystem: Boolean,
    val hasInternet: Boolean,
    val wifiAllowed: Boolean,
    val mobileAllowed: Boolean,
    val roamingAllowed: Boolean
)

enum class AppFilter(val icon: ImageVector) {
    ALL(Icons.Default.Apps),
    SYSTEM(Icons.Default.Android),
    USER(Icons.Default.Person),
    INTERNET(Icons.Default.Cloud),
    NO_INTERNET(Icons.Default.CloudOff)
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Default.Home),
    APPLICATIONS("Applications", Icons.Default.Apps),
    RULES("Rules", Icons.Default.List),
    SETTINGS("Settings", Icons.Default.Settings),
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyFirewallTheme {
        Greeting("Android")
    }
}