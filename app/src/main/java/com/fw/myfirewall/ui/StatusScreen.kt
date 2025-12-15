package com.fw.myfirewall.ui

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fw.myfirewall.R
import com.fw.myfirewall.viewmodel.StatusViewModel

@Composable
fun StatusScreen(
    modifier: Modifier = Modifier,
    totalApps: Int = 0,
    blockedApps: Int = 0,
    viewModel: StatusViewModel
) {
    val allowedApps = totalApps - blockedApps
    
    // Collecting state from ViewModel
    val advertisingCount by viewModel.advertisingCount.collectAsState()
    val annoyancesCount by viewModel.annoyancesCount.collectAsState()
    val socialCount by viewModel.socialCount.collectAsState()
    val customCount by viewModel.customCount.collectAsState()
    
    // Collecting toggle states
    val isAdvertisingEnabled by viewModel.isAdvertisingEnabled.collectAsState()
    val isAnnoyancesEnabled by viewModel.isAnnoyancesEnabled.collectAsState()
    val isSocialEnabled by viewModel.isSocialEnabled.collectAsState()
    val isCustomEnabled by viewModel.isCustomEnabled.collectAsState()
    
    // Récupération de la configuration pour adapter la taille des cartes
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    // Calcul de la largeur des cartes
    val cardWidth = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        (screenWidth - 48.dp) / 3.5f 
    } else {
        (screenWidth - 32.dp) / 2.3f
    }

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
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                StatusCard(
                    stringResource(R.string.blocked_apps_title),
                    count = blockedApps.toString(),
                    icon = Icons.Default.Block,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .width(cardWidth)
                        .height(160.dp)
                )
            }

            item {
                StatusCard(
                    stringResource(R.string.allowed_apps_title),
                    count = allowedApps.toString(),
                    icon = Icons.Default.CheckCircle,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .width(cardWidth)
                        .height(160.dp)
                )
            }

            item {
                StatusCard(
                    stringResource(R.string.total_apps_title),
                    count = totalApps.toString(),
                    icon = Icons.Default.Apps,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .width(cardWidth)
                        .height(160.dp)
                )
            }
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
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Advertising
            item {
                StatusCard(
                    stringResource(R.string.advertising_label),
                    count = advertisingCount.toString(),
                    icon = Icons.Default.Block,
                    color = if (isAdvertisingEnabled) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .width(cardWidth)
                        .height(160.dp)
                )
            }

            // Annoyances
            item {
                StatusCard(
                    stringResource(R.string.annoyances_label),
                    count = annoyancesCount.toString(),
                    icon = Icons.Default.VisibilityOff,
                    color = if (isAnnoyancesEnabled) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .width(cardWidth)
                        .height(160.dp)
                )
            }

            // Social Media
            item {
                StatusCard(
                    stringResource(R.string.social_label),
                    count = socialCount.toString(),
                    icon = Icons.Default.Group,
                    color = if (isSocialEnabled) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .width(cardWidth)
                        .height(160.dp)
                )
            }

            // Custom Rules
            item {
                StatusCard(
                    stringResource(R.string.custom_label),
                    count = customCount.toString(),
                    icon = Icons.Default.Edit,
                    color = if (isCustomEnabled) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .width(cardWidth)
                        .height(160.dp)
                )
            }
        }
        // ############################################################################
        // FIN CAROUSEL "BLOCKED URLS"
        // ############################################################################
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
        modifier = modifier,
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
