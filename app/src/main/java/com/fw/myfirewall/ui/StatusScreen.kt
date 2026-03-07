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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.fw.myfirewall.R
import com.fw.myfirewall.viewmodel.StatusViewModel
import android.content.Intent
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(
    modifier: Modifier = Modifier,
    totalApps: Int = 0,
    blockedApps: Int = 0,
    viewModel: StatusViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    
    val errorOpenSettingsMsg = stringResource(R.string.error_open_settings)
    val statusOn = stringResource(R.string.status_on)
    val statusOff = stringResource(R.string.status_off)
    val engineAppLabel = stringResource(R.string.status_engine_app)
    val engineUrlVpnLabel = stringResource(R.string.status_engine_url_vpn)
    val engineUrlRootLabel = stringResource(R.string.status_engine_url_root)
    val engineBootLabel = stringResource(R.string.status_engine_boot)

    // ############################################################################
    // 1. CYCLE DE VIE ET SYNC [SORTIES -> ViewModel]
    // ############################################################################
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Actions de rafraîchissement au premier plan
                viewModel.refreshNetworkStats()
                viewModel.getFilteredAppsCount()
                viewModel.checkPrivateDnsSetting()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // ############################################################################
    // 2. [ENTRÉES VIEWMODEL] : États de Filtrage et Statistiques
    // ############################################################################
    val isPrivateDnsActive by viewModel.isPrivateDnsActive.collectAsState()
    val isUrlFilterEnabled by viewModel.isUrlFilterEnabled.collectAsState()

    // Statistiques des règles de filtrage DNS/URL
    val userAppsCountState by viewModel.userAppsCount.collectAsState()
    val advertisingCount by viewModel.advertisingCount.collectAsState()
    val annoyancesCount by viewModel.annoyancesCount.collectAsState()
    val socialCount by viewModel.socialCount.collectAsState()
    val customCount by viewModel.customCount.collectAsState()

    // Métriques réseau et vitesse
    val wifiUsage by viewModel.wifiUsage.collectAsState()
    val mobileUsage by viewModel.mobileUsage.collectAsState()
    val hasPermission by viewModel.hasUsagePermission.collectAsState()
    val downloadSpeed by viewModel.downloadSpeed.collectAsState()
    val uploadSpeed by viewModel.uploadSpeed.collectAsState()
    val speedMode by viewModel.speedMode.collectAsState()

    // États d'activation des catégories DNS
    val isAdvertisingEnabled by viewModel.isAdvertisingEnabled.collectAsState()
    val isAnnoyancesEnabled by viewModel.isAnnoyancesEnabled.collectAsState()
    val isSocialEnabled by viewModel.isSocialEnabled.collectAsState()
    val isCustomEnabled by viewModel.isCustomEnabled.collectAsState()

    // ############################################################################
    // 3. [ENTRÉES VIEWMODEL] : Données Appareil et État Moteurs
    // ############################################################################
    val wifiIp by viewModel.wifiIp.collectAsState()
    val mobileIp by viewModel.mobileIp.collectAsState()
    val systemDns by viewModel.systemDns.collectAsState()
    val vpnDns by viewModel.vpnDns.collectAsState()
    val userDns by viewModel.userDns.collectAsState()

    // Status des services persistants
    val isAppFilterOn by viewModel.isAppFilterEnabled.collectAsState()
    val isUrlFilterOn by viewModel.isUrlFilterEnabled.collectAsState()
    val isBootOn by viewModel.isStartOnBootEnabled.collectAsState()
    val isRootOn by viewModel.isRootEnabled.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 32.dp, bottom = 16.dp)
    ) {
        
        // ----------------------------------------------------------------------------
        // ALERTE SYSTÈME : Conflit DNS Privé Android
        // ----------------------------------------------------------------------------
        if (isUrlFilterEnabled && isPrivateDnsActive) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                onClick = {
                    try {
                        val intent = Intent("android.settings.PRIVATE_DNS_SETTINGS")
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            val fallbackIntent = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                            context.startActivity(fallbackIntent)
                        } catch (e2: Exception) {
                            Toast.makeText(context, errorOpenSettingsMsg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.dns_alert_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = stringResource(R.string.dns_alert_msg),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.dns_alert_cta),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // ############################################################################
        // CARROUSEL 1 : STATISTIQUES DES APPLICATIONS
        // ############################################################################
        val appItems = listOf(
            // Carte : Total Applications Utilisateur
            Triple(stringResource(R.string.user_apps_title), userAppsCountState.toString(), Icons.Default.Apps to MaterialTheme.colorScheme.primaryContainer),
            // Carte : Applications Bloquées (État temps réel du Firewall App)
            Triple(
                stringResource(R.string.blocked_apps_title), 
                blockedApps.toString(), 
                Icons.Default.Block to MaterialTheme.colorScheme.errorContainer
            ),
            // Carte : Applications Autorisées
            Triple(stringResource(R.string.allowed_apps_title), (totalApps - blockedApps).coerceAtLeast(0).toString(), Icons.Default.CheckCircle to MaterialTheme.colorScheme.secondaryContainer)
        )

        HorizontalMultiBrowseCarousel(
            state = rememberCarouselState { appItems.size },
            modifier = Modifier.fillMaxWidth().height(140.dp),
            preferredItemWidth = 186.dp,
            itemSpacing = 8.dp,
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) { index ->
            val item = appItems[index]
            StatusCard(
                title = item.first,
                count = item.second,
                subtitle = if (index == 1) { // Apply only to Blocked Apps card
                    if (isAppFilterOn) stringResource(R.string.rule_applied) else stringResource(R.string.rule_not_applied)
                } else null,
                icon = item.third.first,
                color = item.third.second,
                modifier = Modifier.height(140.dp).maskClip(MaterialTheme.shapes.extraLarge)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ############################################################################
        // CARROUSEL 2 : FILTRAGE DNS / URLS BLOQUÉES
        // Action : Long Click -> [SORTIES -> viewModel.toggle...]
        // ############################################################################
        val blockedItems = listOf(
            // Carte : Publicités (Advertising)
            R.string.advertising_label to Triple(advertisingCount.toString(), Icons.Default.AdsClick, if (isAdvertisingEnabled) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant),
            // Carte : Nuisances (Annoyances)
            R.string.annoyances_label to Triple(annoyancesCount.toString(), Icons.Default.VisibilityOff, if (isAnnoyancesEnabled) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant),
            // Carte : Réseaux Sociaux (Social)
            R.string.social_label to Triple(socialCount.toString(), Icons.Default.Group, if (isSocialEnabled) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant),
            // Carte : Règles Personnalisées (Custom)
            R.string.custom_label to Triple(customCount.toString(), Icons.Default.Edit, if (isCustomEnabled) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant)
        )

        HorizontalMultiBrowseCarousel(
            state = rememberCarouselState { blockedItems.size },
            modifier = Modifier.fillMaxWidth().height(140.dp),
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
                    // Déclencheurs de changement d'état DNS dans le ViewModel
                    when (resId) {
                        R.string.advertising_label -> viewModel.toggleAdvertising()
                        R.string.annoyances_label -> viewModel.toggleAnnoyances()
                        R.string.social_label -> viewModel.toggleSocial()
                        R.string.custom_label -> viewModel.toggleCustom()
                    }
                },
                modifier = Modifier.height(140.dp).maskClip(MaterialTheme.shapes.extraLarge)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ############################################################################
        // CARROUSEL 3 : MONITORING RÉSEAU ET DÉBITS
        // Action : Click -> [SORTIE -> viewModel.toggleSpeedMode]
        // ############################################################################
        val speedText = "↓ ${viewModel.formatSpeed(downloadSpeed)}\n↑ ${viewModel.formatSpeed(uploadSpeed)}"
        val networkItems = listOf(
            // Carte : Vitesse (Mode Temps Réel / Total)
            Triple(stringResource(R.string.network_speed_label, speedMode), speedText, Icons.Default.Speed),
            // Carte : Consommation Wi-Fi
            Triple(stringResource(R.string.network_wifi), if (hasPermission) viewModel.formatDataSize(wifiUsage) else "N/A", Icons.Default.NetworkWifi),
            // Carte : Consommation Mobile / Boot
            Triple(if (hasPermission) stringResource(R.string.network_mobile) else "Total (Boot)", viewModel.formatDataSize(mobileUsage), Icons.Default.SignalCellularAlt)
        )

        HorizontalMultiBrowseCarousel(
            state = rememberCarouselState { networkItems.size },
            modifier = Modifier.fillMaxWidth().height(140.dp),
            preferredItemWidth = 186.dp,
            itemSpacing = 8.dp,
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) { index ->
            val item = networkItems[index]
            StatusCard(
                title = item.first,
                count = item.second,
                icon = item.third,
                color = MaterialTheme.colorScheme.surfaceVariant,
                onClick = {
                    // Change l'unité de mesure ou le mode de monitoring
                    if (index == 0) viewModel.toggleSpeedMode()
                },
                countFontSize = if (index == 0) 20.sp else 28.sp,
                modifier = Modifier.height(140.dp).maskClip(MaterialTheme.shapes.extraLarge)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ############################################################################
        // CARROUSEL 4 : INFOS SYSTÈME ET DIAGNOSTIC (IP, DNS, MOTEURS)
        // ############################################################################
        val infoItems = listOf(
            // Carte : Adresses IP (Wi-Fi et Mobile)
            Triple(stringResource(R.string.status_device_ips), "Wi-Fi: $wifiIp\nMobile: $mobileIp", Icons.Default.Dns),
            // Carte : Résolveurs DNS Actifs (System, VPN, User)
            Triple(
                stringResource(R.string.status_dns_servers), 
                "${stringResource(R.string.dns_label_system)}: $systemDns\n" +
                "${stringResource(R.string.dns_label_vpn)}: $vpnDns\n" +
                "${stringResource(R.string.dns_label_user)}: $userDns", 
                Icons.Default.Lan
            ),
            // Carte : État des Moteurs (VPN, Root, Boot)
            Triple(
                stringResource(R.string.status_engine_status), 
                "${engineAppLabel.format(if (isAppFilterOn) statusOn else statusOff)}\n" +
                "${engineUrlVpnLabel.format(if (isUrlFilterOn) statusOn else statusOff)}\n" +
                "${engineUrlRootLabel.format(if (isRootOn) statusOn else statusOff)}\n" +
                        engineBootLabel.format(if (isBootOn) statusOn else statusOff),
                Icons.Rounded.SettingsSuggest
            )
        )

        HorizontalMultiBrowseCarousel(
            state = rememberCarouselState { infoItems.size },
            modifier = Modifier.fillMaxWidth().height(160.dp),
            preferredItemWidth = 240.dp,
            itemSpacing = 8.dp,
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) { index ->
            val item = infoItems[index]
            StatusCard(
                title = item.first,
                count = item.second,
                icon = item.third,
                color = MaterialTheme.colorScheme.primaryContainer,
                countFontSize = 12.sp,
                modifier = Modifier.height(140.dp).maskClip(MaterialTheme.shapes.extraLarge)
            )
        }
    }
}

// ############################################################################
// REUSABLE STATUS CARD COMPONENT
// ############################################################################
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun StatusCard(
    title: String,
    count: String,
    subtitle: String? = null,
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
                Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = count, 
                    fontSize = countFontSize, 
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = countFontSize,
                        lineHeight = if (countFontSize < 20.sp) 16.sp else countFontSize
                    ),
                    maxLines = 4, 
                    textAlign = TextAlign.Center
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
                Text(
                    text = title, 
                    style = MaterialTheme.typography.labelSmall, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
