package com.fw.myfirewall.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    STATUS("Status", Icons.Default.Dashboard),
    APPLICATIONS("Applications", Icons.Default.Apps),
    RULES("Rules", Icons.Default.List),
    SETTINGS("Settings", Icons.Default.Settings),
}
