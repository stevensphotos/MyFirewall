package com.fw.myfirewall.ui

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.fw.myfirewall.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PublicOff
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SignalCellularOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fw.myfirewall.model.AppInfo

enum class AppFilter(val icon: ImageVector) {
    ALL(Icons.Default.Apps),
    SYSTEM(Icons.Default.Android),
    USER(Icons.Default.Person),
    INTERNET(Icons.Default.Cloud),
    NO_INTERNET(Icons.Default.CloudOff)
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
    val context = LocalContext.current
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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(if (isLandscape) 0.9f else 1f)
                ) {
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
                                        onClick = {
                                            Toast.makeText(context, context.getString(R.string.filter_msg, filter.name), Toast.LENGTH_SHORT).show()

                                            onFilterClick(filter)
                                        },
                                        onLongClick = {
                                            Toast.makeText(context, context.getString(R.string.filter_msg, filter.name), Toast.LENGTH_SHORT).show()

                                            onFilterClick(filter)
                                        }
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
                                        // 1. Trouver l'index de l'application dans la liste principale
                                        val index = apps.indexOfFirst { it.packageName == app.packageName }

                                        if (index != -1) {
                                            // 2. Créer une copie de l'app avec le nouveau statut Wi-Fi inversé
                                            val newApp = apps[index].copy(wifiAllowed = !apps[index].wifiAllowed)

                                            // 3. Mettre à jour la liste complète (pour l'affichage local si nécessaire)
                                            val newApps = apps.toMutableList().apply { set(index, newApp) }

                                            // 4. Notifier les changements vers le haut (ViewModel/MainActivity)
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
