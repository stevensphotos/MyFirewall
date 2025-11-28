package com.fw.myfirewall

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.fw.myfirewall.ui.theme.MyFirewallTheme
import kotlinx.coroutines.Dispatchers
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
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.APPLICATIONS) }
    var currentFilter by rememberSaveable { mutableStateOf(AppFilter.ALL) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            it.icon,
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (currentDestination) {
                AppDestinations.APPLICATIONS -> {
                    AppListScreen(
                        modifier = Modifier.padding(innerPadding),
                        currentFilter = currentFilter,
                        onFilterSelected = { currentFilter = it }
                    )
                }
                else -> {
                    Greeting(
                        name = currentDestination.label,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun AppListScreen(
    modifier: Modifier = Modifier,
    currentFilter: AppFilter,
    onFilterSelected: (AppFilter) -> Unit
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val installedApps = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                .mapNotNull { packageInfo ->
                    val appInfo = packageInfo.applicationInfo
                    appInfo?.let {
                        val label = pm.getApplicationLabel(it).toString()
                        val icon = pm.getApplicationIcon(it).toBitmap().asImageBitmap()
                        val isSystem = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        val hasInternet = packageInfo.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
                        AppInfo(label, packageInfo.packageName, icon, isSystem, hasInternet)
                    }
                }
                .sortedBy { it.name }
            apps = installedApps
            isLoading = false
        }
    }

    val filteredApps = remember(apps, currentFilter) {
        when (currentFilter) {
            AppFilter.ALL -> apps
            AppFilter.SYSTEM -> apps.filter { it.isSystem }
            AppFilter.USER -> apps.filter { !it.isSystem }
            AppFilter.INTERNET -> apps.filter { it.hasInternet }
            AppFilter.NO_INTERNET -> apps.filter { !it.hasInternet }
        }
    }

    if (isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AppFilter.entries.forEach { filter ->
                    IconButton(
                        onClick = { onFilterSelected(filter) },
                        modifier = Modifier
                            .background(
                                if (currentFilter == filter) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = filter.icon,
                            contentDescription = filter.name,
                            tint = if (currentFilter == filter) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filteredApps) { app ->
                    AppItem(app)
                }
            }
        }
    }
}

@Composable
fun AppItem(app: AppInfo) {
    ListItem(
        headlineContent = { Text(app.name) },
        supportingContent = { Text(app.packageName) },
        leadingContent = {
            Image(
                bitmap = app.icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        },
        trailingContent = {
            Row {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = "Wi-Fi",
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Icon(
                    imageVector = Icons.Default.SignalCellularAlt,
                    contentDescription = "Mobile Data",
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = "Roaming",
                    modifier = Modifier.padding(horizontal = 4.dp)
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
    val hasInternet: Boolean
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