package com.fw.myfirewall.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(
    modifier: Modifier = Modifier,
    totalApps: Int = 0,
    blockedApps: Int = 0,
    userAppsCount: Int = 0,
    viewModel: StatusViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Refresh stats when screen resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshNetworkStats()
                viewModel.getFilteredAppsCount()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val allowedApps = (totalApps - blockedApps).coerceAtLeast(0)
    
    // Collecting state from ViewModel
    val advertisingCount by viewModel.advertisingCount.collectAsState()
    val annoyancesCount by viewModel.annoyancesCount.collectAsState()
    val socialCount by viewModel.socialCount.collectAsState()
    val customCount by viewModel.customCount.collectAsState()

    val wifiUsage by viewModel.wifiUsage.collectAsState()
    val mobileUsage by viewModel.mobileUsage.collectAsState()
    val hasPermission by viewModel.hasUsagePermission.collectAsState()
    
    val downloadSpeed by viewModel.downloadSpeed.collectAsState()
    val uploadSpeed by viewModel.uploadSpeed.collectAsState()
    val speedMode by viewModel.speedMode.collectAsState()

    val isAdvertisingEnabled by viewModel.isAdvertisingEnabled.collectAsState()
    val isAnnoyancesEnabled by viewModel.isAnnoyancesEnabled.collectAsState()
    val isSocialEnabled by viewModel.isSocialEnabled.collectAsState()
    val isCustomEnabled by viewModel.isCustomEnabled.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp)
    ) {
        // --- SECTION APPLICATIONS ---
        Text(
            stringResource(R.string.applications_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        val appItems = listOf(
            Triple(stringResource(R.string.blocked_apps_title), blockedApps.toString(), Icons.Default.Block to MaterialTheme.colorScheme.errorContainer),
            Triple(stringResource(R.string.allowed_apps_title), allowedApps.toString(), Icons.Default.CheckCircle to MaterialTheme.colorScheme.secondaryContainer),
            Triple("User Apps", userAppsCount.toString(), Icons.Default.Apps to MaterialTheme.colorScheme.primaryContainer)
        )

        HorizontalMultiBrowseCarousel(
            state = rememberCarouselState { appItems.size },
            modifier = Modifier.fillMaxWidth().height(160.dp),
            preferredItemWidth = 186.dp,
            itemSpacing = 8.dp,
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) { index ->
            val item = appItems[index]
            StatusCard(
                title = item.first,
                count = item.second,
                icon = item.third.first,
                color = item.third.second,
                modifier = Modifier.height(160.dp).maskClip(MaterialTheme.shapes.extraLarge)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- SECTION BLOCKED URLS ---
        Text(
            stringResource(R.string.blocked_urls_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Using Res IDs for reliable identification in long-click
        val blockedItems = listOf(
            R.string.advertising_label to Triple(advertisingCount.toString(), Icons.Default.AdsClick, if (isAdvertisingEnabled) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant),
            R.string.annoyances_label to Triple(annoyancesCount.toString(), Icons.Default.VisibilityOff, if (isAnnoyancesEnabled) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant),
            R.string.social_label to Triple(socialCount.toString(), Icons.Default.Group, if (isSocialEnabled) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant),
            R.string.custom_label to Triple(customCount.toString(), Icons.Default.Edit, if (isCustomEnabled) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant)
        )

        HorizontalMultiBrowseCarousel(
            state = rememberCarouselState { blockedItems.size },
            modifier = Modifier.fillMaxWidth().height(160.dp),
            preferredItemWidth = 186.dp,
            itemSpacing = 8.dp,
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) { index ->
            val (resId, data) = blockedItems[index]
            StatusCard(
                title = stringResource(resId),
                count = data.first,
                icon = data.second,
                color = data.third,
                onLongClick = {
                    when (resId) {
                        R.string.advertising_label -> viewModel.toggleAdvertising()
                        R.string.annoyances_label -> viewModel.toggleAnnoyances()
                        R.string.social_label -> viewModel.toggleSocial()
                        R.string.custom_label -> viewModel.toggleCustom()
                    }
                },
                modifier = Modifier.height(160.dp).maskClip(MaterialTheme.shapes.extraLarge)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- SECTION NETWORK ---
        Text(
            stringResource(R.string.network_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        val wifiText = if (hasPermission) viewModel.formatDataSize(wifiUsage) else "N/A"
        val mobileText = viewModel.formatDataSize(mobileUsage)
        val mobileLabel = if (hasPermission) stringResource(R.string.network_mobile) else "Total (Boot)"
        val networkColor = MaterialTheme.colorScheme.surfaceVariant

        val speedText = "↓ ${viewModel.formatSpeed(downloadSpeed)}\n↑ ${viewModel.formatSpeed(uploadSpeed)}"
        val speedLabel = stringResource(R.string.network_speed_label, speedMode)

        val networkItems = listOf(
            Triple(speedLabel, speedText, Icons.Default.Speed),
            Triple(stringResource(R.string.network_wifi), wifiText, Icons.Default.NetworkWifi),
            Triple(mobileLabel, mobileText, Icons.Default.SignalCellularAlt)
        )

        HorizontalMultiBrowseCarousel(
            state = rememberCarouselState { networkItems.size },
            modifier = Modifier.fillMaxWidth().height(160.dp),
            preferredItemWidth = 186.dp,
            itemSpacing = 8.dp,
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) { index ->
            val item = networkItems[index]
            val textSize = if (index == 0) 20.sp else 28.sp

            StatusCard(
                title = item.first,
                count = item.second,
                icon = item.third,
                color = networkColor,
                onLongClick = {
                    when (index) {
                        1 -> context.startActivity(android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
                        2 -> context.startActivity(android.content.Intent(android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS))
                    }
                },
                onClick = {
                    if (index == 0) viewModel.toggleSpeedMode()
                },
                countFontSize = textSize,
                modifier = Modifier.height(160.dp).maskClip(MaterialTheme.shapes.extraLarge)
            )
        }
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
    onLongClick: (() -> Unit)? = null,
    countFontSize: androidx.compose.ui.unit.TextUnit = 32.sp,
    modifier: Modifier = Modifier
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Surface(
        modifier = modifier.then(
            if (onClick != null || onLongClick != null) {
                Modifier.combinedClickable(
                    onClick = { onClick?.invoke() },
                    onLongClick = {
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
        Box(modifier = Modifier.fillMaxSize()) {
            if (onLongClick != null) {
                Icon(
                    imageVector = Icons.Rounded.SettingsSuggest,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
            Column(
                modifier = Modifier.padding(12.dp).fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(count, fontSize = countFontSize, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center, lineHeight = countFontSize)
                Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
