package com.fw.myfirewall.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.fw.myfirewall.R
import com.fw.myfirewall.viewmodel.StatusViewModel
import android.content.pm.ApplicationInfo
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(
    modifier: Modifier = Modifier,
    totalApps: Int = 0,
    blockedApps: Int = 0,
    allowedApps: Int = 0,
    userAppsCount: Int = 0,
    // AJOUTEZ CES PARAMÈTRES S'ILS MANQUENT :
    hasPermission: Boolean = false,
    wifiUsage: Long = 0L,
    mobileUsage: Long = 0L,
    downloadSpeed: Long = 0L,
    uploadSpeed: Long = 0L,
    speedMode: String = "bits",
    viewModel: StatusViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Refresh stats when screen resumes
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshNetworkStats()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val allowedApps = totalApps - blockedApps
    
    // Collecting state from ViewModel
    val advertisingCount by viewModel.advertisingCount.collectAsState()
    val annoyancesCount by viewModel.annoyancesCount.collectAsState()
    val socialCount by viewModel.socialCount.collectAsState()
    val customCount by viewModel.customCount.collectAsState()


    val wifiUsage by viewModel.wifiUsage.collectAsState()
    val mobileUsage by viewModel.mobileUsage.collectAsState()
    val hasPermission by viewModel.hasUsagePermission.collectAsState()
    
    // Speed stats
    val downloadSpeed by viewModel.downloadSpeed.collectAsState()
    val uploadSpeed by viewModel.uploadSpeed.collectAsState()
    val speedMode by viewModel.speedMode.collectAsState()

    // Collecting toggle states
    val isAdvertisingEnabled by viewModel.isAdvertisingEnabled.collectAsState()
    val isAnnoyancesEnabled by viewModel.isAnnoyancesEnabled.collectAsState()
    val isSocialEnabled by viewModel.isSocialEnabled.collectAsState()
    val isCustomEnabled by viewModel.isCustomEnabled.collectAsState()

    val appItems = listOf(
        Triple(
            stringResource(R.string.blocked_apps_title),
            blockedApps.toString(),
            Icons.Default.Block to MaterialTheme.colorScheme.errorContainer // Rouge si erreur
        ),
        Triple(
            stringResource(R.string.user_apps_title),
            userAppsCount.toString(),
            Icons.Default.Apps to MaterialTheme.colorScheme.primaryContainer // Couleur principale

        ),
        Triple(
            stringResource(R.string.user_apps_title),
            userAppsCount.toString(), // <--- CHANGE THIS from totalApps.toString()
            Icons.Default.Apps to MaterialTheme.colorScheme.primaryContainer
        )
    )


    // Container principal scrollable verticalement pour accueillir plusieurs carousels
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp)
    ) {

        // ############################################################################
        // DEBUT CAROUSEL "APPLICATIONS"
        // ############################################################################
        
        Text(
            stringResource(R.string.applications_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        val appItems = listOf(
            Triple(stringResource(R.string.blocked_apps_title), blockedApps.toString(), Icons.Default.Block to MaterialTheme.colorScheme.errorContainer),
            Triple(stringResource(R.string.allowed_apps_title), allowedApps.toString(), Icons.Default.CheckCircle to MaterialTheme.colorScheme.secondaryContainer),
            Triple(stringResource(R.string.user_apps_title), totalApps.toString(), Icons.Default.Apps to MaterialTheme.colorScheme.primaryContainer)
        )

        HorizontalMultiBrowseCarousel(
            state = rememberCarouselState { appItems.size },
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            preferredItemWidth = 180.dp,
            itemSpacing = 16.dp,
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) { i ->
            val item = appItems[i]
            StatusCard(
                title = item.first,
                count = item.second,
                icon = item.third.first,
                color = item.third.second,
                modifier = Modifier
                    .fillMaxSize()
                    .maskClip(MaterialTheme.shapes.extraLarge)
            )
        }
        // ############################################################################
        // FIN CAROUSEL "APPLICATIONS"
        // ############################################################################

        Spacer(modifier = Modifier.height(24.dp))

        // ############################################################################
        // DEBUT CAROUSEL "BLOCKED URLS"
        // ############################################################################
        
        Text(
            stringResource(R.string.blocked_urls_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        val blockedItems = listOf(
            Triple(stringResource(R.string.advertising_label), advertisingCount.toString(), Icons.Default.AdsClick to (if (isAdvertisingEnabled) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer)),
            Triple(stringResource(R.string.annoyances_label), annoyancesCount.toString(), Icons.Default.VisibilityOff to (if (isAnnoyancesEnabled) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer)),
            Triple(stringResource(R.string.social_label), socialCount.toString(), Icons.Default.Group to (if (isSocialEnabled) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer)),
            Triple(stringResource(R.string.custom_label), customCount.toString(), Icons.Default.Edit to (if (isCustomEnabled) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer))
        )

        HorizontalMultiBrowseCarousel(
            state = rememberCarouselState { blockedItems.size },
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            preferredItemWidth = 180.dp,
            itemSpacing = 16.dp,
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) { i ->
            val item = blockedItems[i]
            StatusCard(
                title = item.first,
                count = item.second,
                icon = item.third.first,
                color = item.third.second,
                onClick = { /* Action simple si besoin */ },
                onLongClick = {
                    // Exemple : Ouvrir directement l'importation de listes pour cette catégorie
                    // Ou simplement naviguer vers l'écran CustomRules
                    // onNavigateToCustomRules()
                    println("Long click sur ${item.first}: charger de nouvelles listes")
                },
                modifier = Modifier
                    .fillMaxSize()
                    .maskClip(MaterialTheme.shapes.extraLarge)
            )
        }
        // ############################################################################
        // FIN CAROUSEL "BLOCKED URLS"
        // ############################################################################

        Spacer(modifier = Modifier.height(24.dp))

        // ############################################################################
        // DEBUT CAROUSEL "NETWORK"
        // ############################################################################
        
        Text(
            stringResource(R.string.network_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        val wifiText = if (hasPermission) viewModel.formatDataSize(wifiUsage) else "N/A"
        val mobileText = viewModel.formatDataSize(mobileUsage)
        val mobileLabel = if (hasPermission) stringResource(R.string.network_mobile) else "Total (Boot)"
        val networkColor = if (hasPermission) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.tertiaryContainer

        val speedText = "↓ ${viewModel.formatSpeed(downloadSpeed)}\n↑ ${viewModel.formatSpeed(uploadSpeed)}"
        val speedLabel = stringResource(R.string.network_speed_label, speedMode)

        val networkItems = listOf(
            Triple(speedLabel, speedText, Icons.Default.Speed),
            Triple(stringResource(R.string.network_wifi), wifiText, Icons.Default.NetworkWifi),
            Triple(mobileLabel, mobileText, Icons.Default.SignalCellularAlt)
        )

        HorizontalMultiBrowseCarousel(
            state = rememberCarouselState { networkItems.size },
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            preferredItemWidth = 180.dp,
            itemSpacing = 16.dp,
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) { i ->
            val item = networkItems[i]
            
            // Adjust dynamic text size
            val textSize = when {
                i == 0 -> 20.sp // Speed card has 2 lines, needs smaller font
                item.second.length > 8 -> 28.sp 
                else -> MaterialTheme.typography.displayMedium.fontSize
            }

            StatusCard(
                title = item.first,
                count = item.second,
                icon = item.third,
                color = networkColor,
                onLongClick = {
                    when (i) {
                        1 -> { // WiFi Card
                            val intent = android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                            context.startActivity(intent)
                        }
                        2 -> { // Mobile Card
                            val intent = android.content.Intent(android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS)
                            context.startActivity(intent)
                        }
                    }
                },
                onClick = {
                    if (i == 0) {
                        viewModel.toggleSpeedMode()
//                    } else if (!hasPermission) {
//                        openUsageStatsSettings(context)

                    }
                },
                countFontSize = textSize,
                modifier = Modifier
                    .fillMaxSize()
                    .maskClip(MaterialTheme.shapes.extraLarge)
            )
        }
        // ############################################################################
        // FIN CAROUSEL "NETWORK"
        // ############################################################################
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun StatusCard(
    title: String,
    count: String,
    icon: ImageVector,
    color: Color,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null, // AJOUT
    countFontSize: androidx.compose.ui.unit.TextUnit = MaterialTheme.typography.displayMedium.fontSize,
    modifier: Modifier = Modifier
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Surface(
        modifier = modifier.then(
            if (onClick != null || onLongClick != null) {
                Modifier.combinedClickable(
                    onClick = { onClick?.invoke() },
                    onLongClick = {
                        // Retour haptique pour confirmer l'appui long
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onLongClick?.invoke()
                    }
                )
            } else Modifier
        ),


        shape = RoundedCornerShape(25.dp),
        color = color,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        // Box permet de superposer l'icône de réglages par-dessus le contenu
        Box(modifier = Modifier.fillMaxSize()) {

            // ICÔNE DE SUGGESTION (S'affiche si onLongClick est défini)
            if (onLongClick != null) {
                Icon(
                    imageVector = Icons.Rounded.SettingsSuggest,
                    contentDescription = "Options disponibles",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(18.dp),
                    // La couleur s'adapte au thème (onSurface avec de la transparence)
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }


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
                    fontSize = countFontSize,
                    lineHeight = if (count.contains("\n")) countFontSize * 1.2 else countFontSize,
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
