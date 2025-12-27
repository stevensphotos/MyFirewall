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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fw.myfirewall.model.AppInfo
import androidx.compose.runtime.mutableStateMapOf

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

    val lastToastTime = remember { mutableStateMapOf<AppFilter, Long>() }
    val toastCooldown = 2000L // 2 secondes entre chaque Toast

    val filterAllLabel = stringResource(R.string.filter_all)
    val filterSystemLabel = stringResource(R.string.filter_system)
    val filterUserLabel = stringResource(R.string.filter_user)
    val filterInternetLabel = stringResource(R.string.filter_internet)
    val filterNoInternetLabel = stringResource(R.string.filter_no_internet)
    val filterMsg = stringResource(R.string.filter_msg)

    val context = LocalContext.current
    val localSelectedFilters = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(selectedFilters) }
    androidx.compose.runtime.LaunchedEffect(selectedFilters) { localSelectedFilters.value = selectedFilters }
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
                        .verticalScroll(rememberScrollState())
                        // Augmentez le padding top (ex: 32.dp au lieu de 16.dp)
                        .padding(top = 32.dp, bottom = 16.dp)






                ) {
                    // ############################################################################
                    // FILTER SECTION
                    // ############################################################################
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        AppFilter.entries.forEach { filter ->
                            val isSelected = localSelectedFilters.value.contains(filter)
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
                                            val current = localSelectedFilters.value
                                            val wasSelected = current.contains(filter)

                                            // Ignorer si le filtre est déjà sélectionné
                                            if (wasSelected) {
                                                return@combinedClickable
                                            }

                                            val updated = current.toMutableSet()
                                            val removedConflicts = mutableListOf<AppFilter>()

                                            // Ajouter le filtre cliqué
                                            updated.add(filter)

                                            // Résolution XOR des conflits lors de l'ajout
                                            when (filter) {
                                                AppFilter.INTERNET -> {
                                                    if (updated.contains(AppFilter.NO_INTERNET)) {
                                                        updated.remove(AppFilter.NO_INTERNET)
                                                        removedConflicts += AppFilter.NO_INTERNET
                                                    }
                                                }

                                                AppFilter.NO_INTERNET -> {
                                                    if (updated.contains(AppFilter.INTERNET)) {
                                                        updated.remove(AppFilter.INTERNET)
                                                        removedConflicts += AppFilter.INTERNET
                                                    }
                                                }

                                                AppFilter.SYSTEM -> {
                                                    if (updated.contains(AppFilter.USER)) {
                                                        updated.remove(AppFilter.USER)
                                                        removedConflicts += AppFilter.USER
                                                    }
                                                    if (updated.contains(AppFilter.ALL)) {
                                                        updated.remove(AppFilter.ALL)
                                                        removedConflicts += AppFilter.ALL
                                                    }
                                                }

                                                AppFilter.USER -> {
                                                    if (updated.contains(AppFilter.SYSTEM)) {
                                                        updated.remove(AppFilter.SYSTEM)
                                                        removedConflicts += AppFilter.SYSTEM
                                                    }
                                                    if (updated.contains(AppFilter.ALL)) {
                                                        updated.remove(AppFilter.ALL)
                                                        removedConflicts += AppFilter.ALL
                                                    }
                                                }

                                                AppFilter.ALL -> {
                                                    // ALL retire USER et SYSTEM mais garde INTERNET/NO_INTERNET
                                                    if (updated.contains(AppFilter.USER)) {
                                                        updated.remove(AppFilter.USER)
                                                        removedConflicts += AppFilter.USER
                                                    }
                                                    if (updated.contains(AppFilter.SYSTEM)) {
                                                        updated.remove(AppFilter.SYSTEM)
                                                        removedConflicts += AppFilter.SYSTEM
                                                    }
                                                    // Note: INTERNET et NO_INTERNET restent si présents
                                                }
                                            }

                                            // Mettre à jour l'état local pour un retour visuel immédiat
                                            localSelectedFilters.value = updated

                                            // Propager les changements vers le parent
                                            onFilterClick(filter)
                                            removedConflicts.forEach { onFilterClick(it) }
                                        },
                                        onLongClick = {
                                            val currentTime = System.currentTimeMillis()
                                            val lastTime = lastToastTime.getOrDefault(filter, 0L)

                                            // 1. Résolution de la ressource locale
                                            val filterLabel = when (filter) {
                                                AppFilter.ALL -> filterAllLabel
                                                AppFilter.SYSTEM -> filterSystemLabel
                                                AppFilter.USER -> filterUserLabel
                                                AppFilter.INTERNET -> filterInternetLabel
                                                AppFilter.NO_INTERNET -> filterNoInternetLabel
                                            }

                                            // 2. Un seul bloc de Toast utilisant filterMsg
                                            if (currentTime - lastTime >= toastCooldown) {
                                                Toast.makeText(
                                                    context,
                                                    filterMsg.format(filterLabel),
                                                    Toast.LENGTH_SHORT
                                                ).show()

                                                lastToastTime[filter] = currentTime
                                            }

                                            // 3. Logique métier pour USER ou autres filtres (Conservée par rigueur)
                                            if (filter == AppFilter.USER) {
                                                val userApps = apps.filter { !it.isSystem }
                                                if (userApps.isNotEmpty()) {
                                                    val anyAllowed = userApps.any { it.wifiAllowed || it.mobileAllowed || it.roamingAllowed }
                                                    val newState = !anyAllowed
                                                    val updatedApps = apps.map { a ->
                                                        if (!a.isSystem) {
                                                            a.copy(
                                                                wifiAllowed = newState,
                                                                mobileAllowed = newState,
                                                                roamingAllowed = newState
                                                            )
                                                        } else a
                                                    }
                                                    onAppsChange(updatedApps)
                                                    onAppsUpdated(updatedApps)
                                                }
                                            } else {
                                                onFilterLongClick(filter)
                                            }
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

                    // ############################################################################
                    // APP LIST SECTION
                    // ############################################################################

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(
                            items = filteredApps,
                            key = { app -> app.packageName }
                        ) { app ->
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
                                        // 1. Trouver l'index de l'application dans la liste principale pour la cohérence
                                        val index = apps.indexOfFirst { it.packageName == app.packageName }
                                        if (index != -1) {
                                            val currentApp = apps[index]

                                            // 2. Créer une copie avec l'état Roaming inversé
                                            val newApp = currentApp.copy(
                                                roamingAllowed = !currentApp.roamingAllowed
                                            )

                                            // 3. Mettre à jour la liste complète (déclenche la recomposition locale)
                                            val newApps = apps.toMutableList().apply { set(index, newApp) }

                                            // 4. Propager vers le ViewModel pour mise à jour de la base de données et du VPN
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


// ############################################################################
// APP ITEM COMPOSABLE
// ############################################################################
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
                            val intent =
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
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
                    contentDescription = stringResource(R.string.wifi_content_description),
                    tint = if (app.wifiAllowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clickable { onWifiToggle() }
                )
                Icon(
                    imageVector = if (app.mobileAllowed) Icons.Default.SignalCellularAlt else Icons.Default.SignalCellularOff,
                    contentDescription = stringResource(R.string.mobile_data_content_description),
                    tint = if (app.mobileAllowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clickable { onMobileToggle() }
                )
                Icon(
                    imageVector = if (app.roamingAllowed) Icons.Default.Public else Icons.Default.PublicOff,
                    contentDescription = stringResource(R.string.roaming_content_description),
                    tint = if (app.roamingAllowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clickable { onRoamingToggle() }
                )
            }
        }
    )
}
